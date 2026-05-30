package com.puj.collaboration.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.puj.security.redis.RedisClientProvider;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio de mensajería WebSocket con soporte de escalado horizontal mediante
 * Redis Pub/Sub.
 *
 * <p>Gestiona las sesiones WebSocket locales del pod actual y retransmite los
 * mensajes recibidos de Redis a los clientes conectados en este pod.</p>
 *
 * <p>Flujo de un mensaje por pod:</p>
 * <pre>
 *   Browser → [este pod] → Redis PUBLISH ws:group:{groupId}
 *                        ← Redis SUBSCRIBE ws:group:* → broadcast local
 * </pre>
 *
 * <p>Esto garantiza que todos los pods entregan el mensaje independientemente
 * de a qué pod esté conectado el cliente receptor. En caso de desconexión de
 * Redis, el suscriptor intenta reconectarse automáticamente cada 3 segundos.
 * Si Redis no está disponible al publicar, el mensaje se entrega solo a las
 * sesiones locales del pod actual (degradación controlada).</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class WsPubSubService {

    private static final Logger LOG            =
            Logger.getLogger(WsPubSubService.class.getName());
    static final String         CHANNEL_PREFIX = "ws:group:";

    /** Sesiones WebSocket locales de este pod: groupId → set de sesiones activas. */
    private final Map<String, Set<jakarta.websocket.Session>> localSessions =
            new ConcurrentHashMap<>();

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Inject
    private RedisClientProvider redisProvider;

    private ExecutorService subscriberThread;
    private JedisPubSub      pubSub;
    private volatile boolean running = false;

    /**
     * Inicia el hilo daemon de suscripción a Redis Pub/Sub.
     *
     * <p>Si la conexión se pierde, el bucle interno reintenta la suscripción
     * cada 3 segundos mientras {@code running} sea {@code true}.</p>
     */
    @PostConstruct
    void startSubscriber() {
        running = true;
        subscriberThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ws-redis-subscriber");
            t.setDaemon(true);
            return t;
        });

        subscriberThread.submit(this::runSubscriberLoop);
        LOG.info("WsPubSubService iniciado — suscrito a " + CHANNEL_PREFIX + "*");
    }

    /**
     * Detiene el hilo de suscripción y cierra la suscripción Redis de forma limpia.
     */
    @PreDestroy
    void stopSubscriber() {
        running = false;
        if (pubSub != null) {
            try { pubSub.punsubscribe(); }
            catch (Exception e) {
                LOG.warning("Error cancelando suscripción Redis: " + e.getMessage());
            }
        }
        if (subscriberThread != null) subscriberThread.shutdownNow();
    }

    /**
     * Registra una nueva sesión WebSocket en el mapa de sesiones locales del pod.
     *
     * @param groupId UUID del grupo como {@code String}
     * @param session sesión WebSocket a registrar
     */
    public void registerSession(
            String groupId, jakarta.websocket.Session session) {
        localSessions.computeIfAbsent(groupId, k -> ConcurrentHashMap.newKeySet())
                     .add(session);
    }

    /**
     * Elimina una sesión WebSocket del mapa de sesiones locales.
     *
     * <p>Si el grupo queda sin sesiones, elimina la entrada del mapa.</p>
     *
     * @param groupId UUID del grupo como {@code String}
     * @param session sesión WebSocket a eliminar
     */
    public void removeSession(
            String groupId, jakarta.websocket.Session session) {
        Set<jakarta.websocket.Session> sessions = localSessions.get(groupId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) localSessions.remove(groupId);
        }
    }

    /**
     * Publica un mensaje en el canal Redis del grupo para distribuirlo a todos
     * los pods. Si Redis no está disponible, el mensaje se entrega solo a las
     * sesiones locales de este pod.
     *
     * @param groupId UUID del grupo como {@code String}
     * @param message mensaje a publicar
     */
    public void publish(String groupId, WsMessage message) {
        try (Jedis jedis = redisProvider.getPool().getResource()) {
            String json = mapper.writeValueAsString(message);
            jedis.publish(CHANNEL_PREFIX + groupId, json);
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "Error publicando en Redis Pub/Sub — enviando solo local", e);
            deliverLocally(groupId, message);
        }
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * Bucle principal del hilo suscriptor. Se reconecta automáticamente a Redis
     * si la conexión se pierde, con un retraso de 3 segundos entre intentos.
     */
    private void runSubscriberLoop() {
        while (running) {
            try (Jedis jedis = redisProvider.getPool().getResource()) {
                pubSub = new JedisPubSub() {
                    @Override
                    public void onPMessage(
                            String pattern, String channel, String message) {
                        String groupId = channel.substring(CHANNEL_PREFIX.length());
                        deliverToLocalSessions(groupId, message);
                    }
                };
                jedis.psubscribe(pubSub, CHANNEL_PREFIX + "*");
            } catch (Exception e) {
                if (running) {
                    LOG.log(Level.WARNING,
                            "Redis pub/sub desconectado — reintentando en 3s", e);
                    try { Thread.sleep(3000); }
                    catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    /**
     * Serializa y entrega un mensaje directamente a las sesiones locales del pod
     * (fallback cuando Redis no está disponible).
     *
     * @param groupId UUID del grupo como {@code String}
     * @param message mensaje a entregar
     */
    private void deliverLocally(String groupId, WsMessage message) {
        try {
            deliverToLocalSessions(groupId, mapper.writeValueAsString(message));
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Error en fallback local", ex);
        }
    }

    /**
     * Envía el texto JSON de un mensaje a todas las sesiones WebSocket activas
     * del grupo en este pod.
     *
     * @param groupId UUID del grupo como {@code String}
     * @param json    texto JSON del mensaje a entregar
     */
    private void deliverToLocalSessions(String groupId, String json) {
        Set<jakarta.websocket.Session> sessions =
                localSessions.getOrDefault(groupId, Set.of());
        for (jakarta.websocket.Session s : sessions) {
            if (s.isOpen()) {
                try {
                    s.getBasicRemote().sendText(json);
                } catch (IOException e) {
                    LOG.log(Level.WARNING,
                            "Error enviando WS a sesión " + s.getId(), e);
                }
            }
        }
    }
}
