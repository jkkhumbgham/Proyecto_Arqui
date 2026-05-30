package com.puj.notificaciones.envio.infraestructura;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.puj.eventos.EventoNotificacionCorreo;
import com.puj.notificaciones.envio.aplicacion.RenderizadorPlantilla;
import com.puj.notificaciones.envio.aplicacion.ServicioEnvioCorreo;
import com.rabbitmq.client.*;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Consumidor de mensajes RabbitMQ para el servicio de correo electrónico.
 *
 * <p>Escucha la cola {@code email.notifications} y procesa eventos de tipo
 * {@link EventoNotificacionCorreo}. Implementa un patrón de reintento con
 * retroceso lineal y Dead Letter Exchange (DLX):</p>
 *
 * <ul>
 *   <li><strong>Intento 1-2:</strong> NACK con {@code requeue=true} — el mensaje
 *       vuelve a la cabeza de la cola para ser reprocesado.</li>
 *   <li><strong>Intento 3:</strong> NACK con {@code requeue=false} — el mensaje
 *       se enruta al Dead Letter Exchange para análisis posterior.</li>
 * </ul>
 *
 * <p>El conteo de intentos se obtiene del header {@code x-delivery-count}
 * que RabbitMQ incrementa automáticamente en cada reencola. La reconexión
 * al broker se realiza manualmente cada 5 segundos en caso de pérdida de
 * canal o conexión; la recuperación automática de la librería cliente está
 * deshabilitada para mantener control total del ciclo de vida.</p>
 *
 * <p>El bean se inicializa al arrancar el contexto CDI
 * ({@code @Initialized(ApplicationScoped.class)}) y se detiene limpiamente
 * en {@code @PreDestroy} cerrando canal, conexión y el executor.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class ConsumidorCorreo {

    private static final Logger LOG         = Logger.getLogger(ConsumidorCorreo.class.getName());
    private static final String COLA        = "email.notifications";
    private static final int    MAX_REINTENTOS = 3;

    private static final String RABBITMQ_HOST  =
            System.getenv().getOrDefault("RABBITMQ_HOST",     "localhost");
    private static final int    RABBITMQ_PUERTO =
            Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5672"));
    private static final String RABBITMQ_USUARIO =
            System.getenv().getOrDefault("RABBITMQ_USER",     "guest");
    private static final String RABBITMQ_CLAVE =
            System.getenv().getOrDefault("RABBITMQ_PASSWORD", "guest");
    private static final String RABBITMQ_VHOST =
            System.getenv().getOrDefault("RABBITMQ_VHOST",    "/");

    /** Servicio SMTP utilizado para el envío real de correos. */
    @Inject private ServicioEnvioCorreo  servicioSmtp;

    /** Renderizador de plantillas HTML para cada tipo de correo. */
    @Inject private RenderizadorPlantilla renderizador;

    /** Mapeador JSON configurado con soporte para tipos de fecha/hora de Java 8+. */
    private final ObjectMapper mapeadorJson =
            new ObjectMapper().registerModule(new JavaTimeModule());

    /** Conexión activa al broker RabbitMQ; puede ser {@code null} entre reconexiones. */
    private Connection conexion;

    /** Canal AMQP activo; puede ser {@code null} entre reconexiones. */
    private Channel canal;

    /** Executor de hilo único que aloja el bucle de consumo y reconexión. */
    private ExecutorService executor;

    /**
     * Indica si el consumidor debe seguir en ejecución.
     * Se establece {@code false} en {@link #detener()} para salir del bucle principal.
     */
    private volatile boolean ejecutando = false;

    // ── Ciclo de vida CDI ─────────────────────────────────────────────────────

    /**
     * Inicializa el consumidor cuando el scope {@code ApplicationScoped} arranca.
     *
     * @param ignorado evento CDI de inicialización del scope; no se utiliza directamente.
     */
    public void alIniciar(
            @Observes @Initialized(ApplicationScoped.class) Object ignorado) {
        ejecutando = true;
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread hilo = new Thread(r, "email-rabbitmq-consumer");
            hilo.setDaemon(true);
            return hilo;
        });
        executor.submit(this::iniciarConsumo);
        LOG.info("ConsumidorCorreo arrancado — escuchando cola: " + COLA);
    }

    /**
     * Detiene el consumidor de forma ordenada al destruir el bean CDI.
     */
    @PreDestroy
    void detener() {
        ejecutando = false;
        cerrarCanal();
        cerrarConexion();
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    // ── Bucle principal con reconexión automática ─────────────────────────────

    /**
     * Bucle principal de consumo con reconexión automática al broker RabbitMQ.
     *
     * <p>En cada iteración se intenta establecer una nueva conexión y registrar
     * un consumidor. Un {@link CountDownLatch} bloquea el hilo hasta que el
     * canal se cierra. Si {@link #ejecutando} sigue siendo {@code true}, se
     * espera 5 segundos antes de reintentar.</p>
     */
    private void iniciarConsumo() {
        while (ejecutando) {
            CountDownLatch latchContador = new CountDownLatch(1);
            try {
                conectar();
                // Libera el latch cuando el canal o la conexión se cierran.
                canal.addShutdownListener(causa -> latchContador.countDown());
                LOG.info("Conectado a RabbitMQ — esperando mensajes en: " + COLA);
                canal.basicConsume(
                        COLA, false,
                        this::procesarEntrega,
                        tag -> latchContador.countDown());
                // Bloquea este hilo hasta que el canal se cierre.
                latchContador.await();
            } catch (Exception e) {
                if (!ejecutando) break;
                LOG.log(Level.WARNING,
                        "Conexión RabbitMQ perdida — reintentando en 5s", e);
                latchContador.countDown();
                esperar(5_000);
            }
        }
    }

    /**
     * Establece una nueva conexión y canal con el broker RabbitMQ.
     *
     * <p>La recuperación automática de la librería cliente está deshabilitada
     * porque el bucle {@link #iniciarConsumo()} gestiona la reconexión
     * manualmente. Se establece QoS de 1 mensaje sin confirmar a la vez.</p>
     *
     * @throws Exception si la conexión o la creación del canal fallan.
     */
    private void conectar() throws Exception {
        ConnectionFactory fabrica = new ConnectionFactory();
        fabrica.setHost(RABBITMQ_HOST);
        fabrica.setPort(RABBITMQ_PUERTO);
        fabrica.setUsername(RABBITMQ_USUARIO);
        fabrica.setPassword(RABBITMQ_CLAVE);
        fabrica.setVirtualHost(RABBITMQ_VHOST);
        fabrica.setAutomaticRecoveryEnabled(false); // reconexión manejada manualmente
        fabrica.setConnectionTimeout(5_000);
        fabrica.setHandshakeTimeout(10_000);

        conexion = fabrica.newConnection();
        canal    = conexion.createChannel();
        canal.basicQos(1);
    }

    // ── Procesamiento de mensajes ─────────────────────────────────────────────

    /**
     * Procesa un mensaje entregado por RabbitMQ desde la cola {@code email.notifications}.
     *
     * <p>El flujo de procesamiento es:</p>
     * <ol>
     *   <li>Deserializar el cuerpo JSON a {@link EventoNotificacionCorreo}.</li>
     *   <li>Renderizar el correo HTML con {@link RenderizadorPlantilla}.</li>
     *   <li>Enviar el correo vía {@link ServicioEnvioCorreo#enviar}.</li>
     *   <li>Si el envío fue exitoso: ACK al broker.</li>
     *   <li>Si falló: delegar a {@link #rechazarConReintento} siguiendo la política.</li>
     * </ol>
     *
     * @param etiquetaConsumidor etiqueta del consumidor asignada por RabbitMQ; no se utiliza.
     * @param entrega            mensaje entregado por RabbitMQ.
     */
    private void procesarEntrega(String etiquetaConsumidor, Delivery entrega) {
        long   etiquetaEntrega = entrega.getEnvelope().getDeliveryTag();
        int    intento         = contarIntentos(entrega.getProperties());
        String cuerpo          =
                new String(entrega.getBody(), StandardCharsets.UTF_8);

        try {
            EventoNotificacionCorreo evento =
                    mapeadorJson.readValue(cuerpo, EventoNotificacionCorreo.class);
            RenderizadorPlantilla.CorreoRenderizado correo =
                    renderizador.renderizar(evento);

            boolean enviado = servicioSmtp.enviar(
                    evento.getCorreoDestinatario(),
                    evento.getNombreDestinatario(),
                    correo.asunto(),
                    correo.cuerpoHtml(),
                    correo.emailRemitente(),
                    correo.nombreRemitente());

            if (enviado) {
                canal.basicAck(etiquetaEntrega, false);
                LOG.fine("ACK — correo enviado a " + evento.getCorreoDestinatario()
                        + " tipo=" + evento.getTipoCorreo());
            } else {
                rechazarConReintento(etiquetaEntrega, intento, "SMTP falló");
            }

        } catch (Exception e) {
            LOG.log(Level.SEVERE,
                    "Error procesando mensaje (intento " + intento + "): " + cuerpo, e);
            try {
                rechazarConReintento(etiquetaEntrega, intento, e.getMessage());
            } catch (IOException io) {
                LOG.log(Level.WARNING, "Error enviando NACK", io);
            }
        }
    }

    /**
     * Envía un NACK aplicando la política de reintentos con retroceso lineal.
     *
     * <p>La estrategia de reintento es:</p>
     * <ul>
     *   <li><strong>Intentos 1-2</strong>: NACK con {@code requeue=true}. El mensaje
     *       regresa a la cola y se espera {@code 500 ms * intento} antes de retornar.</li>
     *   <li><strong>Intento 3+</strong>: NACK con {@code requeue=false}. RabbitMQ enruta
     *       el mensaje al Dead Letter Exchange (DLX).</li>
     * </ul>
     *
     * @param etiquetaEntrega etiqueta de entrega del mensaje a rechazar.
     * @param intento         número del intento actual (1-based).
     * @param motivo          descripción del motivo del fallo para el log.
     * @throws IOException si falla la comunicación AMQP al enviar el NACK.
     */
    private void rechazarConReintento(
            long etiquetaEntrega, int intento, String motivo) throws IOException {
        boolean reencolar = intento < MAX_REINTENTOS;
        canal.basicNack(etiquetaEntrega, false, reencolar);
        if (reencolar) {
            LOG.warning("NACK requeue=true (intento " + intento + "/" + MAX_REINTENTOS
                    + ") — " + motivo);
            esperar(500L * intento); // retroceso lineal antes del reencolo
        } else {
            LOG.severe("NACK requeue=false → DLX (intento " + intento + ") — " + motivo);
        }
    }

    /**
     * Extrae el número de intento actual desde el header {@code x-delivery-count}.
     *
     * @param propiedades propiedades AMQP del mensaje; puede ser {@code null}.
     * @return número de intento actual; {@code 1} si el header no está presente.
     */
    private int contarIntentos(AMQP.BasicProperties propiedades) {
        if (propiedades == null || propiedades.getHeaders() == null) return 1;
        Object conteo = propiedades.getHeaders().get("x-delivery-count");
        if (conteo instanceof Number n) return n.intValue() + 1;
        return 1;
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private void cerrarCanal() {
        try {
            if (canal != null) canal.close();
        } catch (Exception e) {
            LOG.warning("Error cerrando canal RabbitMQ: " + e.getMessage());
        }
    }

    private void cerrarConexion() {
        try {
            if (conexion != null) conexion.close();
        } catch (Exception e) {
            LOG.warning("Error cerrando conexión RabbitMQ: " + e.getMessage());
        }
    }

    private void esperar(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
