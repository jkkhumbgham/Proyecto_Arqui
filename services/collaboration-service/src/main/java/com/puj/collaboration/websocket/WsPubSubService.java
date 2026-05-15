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
 * Gestiona las sesiones WebSocket locales del pod y retransmite mensajes
 * recibidos de Redis Pub/Sub a los clientes conectados en este pod.
 *
 * Flujo por pod:
 *   Browser → [este pod] → Redis PUBLISH ws:group:{groupId}
 *                        ← Redis SUBSCRIBE ws:group:* → broadcast local
 *
 * Esto garantiza que todos los pods entregan el mensaje independientemente
 * de a qué pod esté conectado el cliente receptor.
 */
@ApplicationScoped
public class WsPubSubService {

    private static final Logger LOG        = Logger.getLogger(WsPubSubService.class.getName());
    static final String         CHANNEL_PREFIX = "ws:group:";

    /** Sessions locales de este pod: groupId → set de Session activas */
    private final Map<String, Set<jakarta.websocket.Session>> localSessions =
            new ConcurrentHashMap<>();

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Inject
    private RedisClientProvider redisProvider;

    private ExecutorService subscriberThread;
    private JedisPubSub      pubSub;
    private volatile boolean running = false;

    @PostConstruct
    void startSubscriber() {
        running = true;
        subscriberThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ws-redis-subscriber");
            t.setDaemon(true);
            return t;
        });

        subscriberThread.submit(() -> {
            while (running) {
                try (Jedis jedis = redisProvider.getPool().getResource()) {
                    pubSub = new JedisPubSub() {
                        @Override
                        public void onPMessage(String pattern, String channel, String message) {
                            String groupId = channel.substring(CHANNEL_PREFIX.length());
                            deliverToLocalSessions(groupId, message);
                        }
                    };
                    jedis.psubscribe(pubSub, CHANNEL_PREFIX + "*");
                } catch (Exception e) {
                    if (running) {
                        LOG.log(Level.WARNING, "Redis pub/sub desconectado — reintentando en 3s", e);
                        try { Thread.sleep(3000); } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        });

        LOG.info("WsPubSubService iniciado — suscrito a " + CHANNEL_PREFIX + "*");
    }

    @PreDestroy
    void stopSubscriber() {
        running = false;
        if (pubSub != null) {
            try { pubSub.punsubscribe(); } catch (Exception ignored) {}
        }
        if (subscriberThread != null) subscriberThread.shutdownNow();
    }

    // ── Gestión de sesiones locales ──────────────────────────────────────────

    public void registerSession(String groupId, jakarta.websocket.Session session) {
        localSessions.computeIfAbsent(groupId, k -> ConcurrentHashMap.newKeySet())
                     .add(session);
    }

    public void removeSession(String groupId, jakarta.websocket.Session session) {
        Set<jakarta.websocket.Session> sessions = localSessions.get(groupId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) localSessions.remove(groupId);
        }
    }

    // ── Publicar mensaje → Redis (distribuye a todos los pods) ───────────────

    public void publish(String groupId, WsMessage message) {
        try (Jedis jedis = redisProvider.getPool().getResource()) {
            String json = mapper.writeValueAsString(message);
            jedis.publish(CHANNEL_PREFIX + groupId, json);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error publicando en Redis Pub/Sub — enviando solo local", e);
            try {
                deliverToLocalSessions(groupId, mapper.writeValueAsString(message));
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Error en fallback local", ex);
            }
        }
    }

    // ── Entregar a sesiones locales de este pod ──────────────────────────────

    private void deliverToLocalSessions(String groupId, String json) {
        Set<jakarta.websocket.Session> sessions = localSessions.getOrDefault(groupId, Set.of());
        for (jakarta.websocket.Session s : sessions) {
            if (s.isOpen()) {
                try {
                    s.getBasicRemote().sendText(json);
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Error enviando WS a sesión " + s.getId(), e);
                }
            }
        }
    }
}
