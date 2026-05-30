package com.puj.colaboracion.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.puj.seguridad.redis.ProveedorRedis;
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
 *   Navegador → [este pod] → Redis PUBLISH ws:grupo:{idGrupo}
 *                          ← Redis SUBSCRIBE ws:grupo:* → broadcast local
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
public class ServicioPubSubWs {

    private static final Logger LOG            =
            Logger.getLogger(ServicioPubSubWs.class.getName());
    static final String         PREFIJO_CANAL  = "ws:group:";

    /** Sesiones WebSocket locales de este pod: idGrupo → set de sesiones activas. */
    private final Map<String, Set<jakarta.websocket.Session>> sesionesLocales =
            new ConcurrentHashMap<>();

    private final ObjectMapper mapeador = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Inject
    private ProveedorRedis proveedorRedis;

    private ExecutorService hiloSuscriptor;
    private JedisPubSub     pubSub;
    private volatile boolean ejecutando = false;

    /**
     * Inicia el hilo daemon de suscripción a Redis Pub/Sub.
     */
    @PostConstruct
    void iniciarSuscriptor() {
        ejecutando = true;
        hiloSuscriptor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ws-redis-subscriber");
            t.setDaemon(true);
            return t;
        });

        hiloSuscriptor.submit(this::bucleRedisSuscriptor);
        LOG.info("ServicioPubSubWs iniciado — suscrito a " + PREFIJO_CANAL + "*");
    }

    /**
     * Detiene el hilo de suscripción y cierra la suscripción Redis de forma limpia.
     */
    @PreDestroy
    void detenerSuscriptor() {
        ejecutando = false;
        if (pubSub != null) {
            try { pubSub.punsubscribe(); }
            catch (Exception e) {
                LOG.warning("Error cancelando suscripción Redis: " + e.getMessage());
            }
        }
        if (hiloSuscriptor != null) hiloSuscriptor.shutdownNow();
    }

    /**
     * Registra una nueva sesión WebSocket en el mapa de sesiones locales del pod.
     *
     * @param idGrupo UUID del grupo como {@code String}
     * @param sesion  sesión WebSocket a registrar
     */
    public void suscribir(String idGrupo, jakarta.websocket.Session sesion) {
        sesionesLocales.computeIfAbsent(idGrupo, k -> ConcurrentHashMap.newKeySet())
                       .add(sesion);
    }

    /**
     * Elimina una sesión WebSocket del mapa de sesiones locales.
     *
     * @param idGrupo UUID del grupo como {@code String}
     * @param sesion  sesión WebSocket a eliminar
     */
    public void desuscribir(String idGrupo, jakarta.websocket.Session sesion) {
        Set<jakarta.websocket.Session> sesiones = sesionesLocales.get(idGrupo);
        if (sesiones != null) {
            sesiones.remove(sesion);
            if (sesiones.isEmpty()) sesionesLocales.remove(idGrupo);
        }
    }

    /**
     * Publica un mensaje en el canal Redis del grupo para distribuirlo a todos
     * los pods. Si Redis no está disponible, el mensaje se entrega solo a las
     * sesiones locales de este pod.
     *
     * @param idGrupo UUID del grupo como {@code String}
     * @param mensaje mensaje a publicar
     */
    public void publicar(String idGrupo, MensajeWs mensaje) {
        try (Jedis jedis = proveedorRedis.obtenerPool().getResource()) {
            String json = mapeador.writeValueAsString(mensaje);
            jedis.publish(PREFIJO_CANAL + idGrupo, json);
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "Error publicando en Redis Pub/Sub — enviando solo local", e);
            entregarLocalmente(idGrupo, mensaje);
        }
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * Bucle principal del hilo suscriptor. Se reconecta automáticamente a Redis
     * si la conexión se pierde, con un retraso de 3 segundos entre intentos.
     */
    private void bucleRedisSuscriptor() {
        while (ejecutando) {
            try (Jedis jedis = proveedorRedis.obtenerPool().getResource()) {
                pubSub = new JedisPubSub() {
                    @Override
                    public void onPMessage(
                            String patron, String canal, String mensaje) {
                        String idGrupo = canal.substring(PREFIJO_CANAL.length());
                        entregarASesionesLocales(idGrupo, mensaje);
                    }
                };
                jedis.psubscribe(pubSub, PREFIJO_CANAL + "*");
            } catch (Exception e) {
                if (ejecutando) {
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
     */
    private void entregarLocalmente(String idGrupo, MensajeWs mensaje) {
        try {
            entregarASesionesLocales(idGrupo, mapeador.writeValueAsString(mensaje));
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Error en fallback local", ex);
        }
    }

    /**
     * Envía el texto JSON de un mensaje a todas las sesiones WebSocket activas
     * del grupo en este pod.
     */
    private void entregarASesionesLocales(String idGrupo, String json) {
        Set<jakarta.websocket.Session> sesiones =
                sesionesLocales.getOrDefault(idGrupo, Set.of());
        for (jakarta.websocket.Session s : sesiones) {
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
