package com.puj.email.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.puj.email.service.SmtpEmailService;
import com.puj.email.template.EmailTemplateRenderer;
import com.puj.events.EmailNotificationEvent;
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
 * {@link EmailNotificationEvent}. Implementa un patrón de reintento con
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
public class EmailConsumer {

    private static final Logger LOG         = Logger.getLogger(EmailConsumer.class.getName());
    private static final String QUEUE       = "email.notifications";
    private static final int    MAX_RETRIES = 3;

    private static final String RABBITMQ_HOST  =
            System.getenv().getOrDefault("RABBITMQ_HOST",     "localhost");
    private static final int    RABBITMQ_PORT  =
            Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5672"));
    private static final String RABBITMQ_USER  =
            System.getenv().getOrDefault("RABBITMQ_USER",     "guest");
    private static final String RABBITMQ_PASS  =
            System.getenv().getOrDefault("RABBITMQ_PASSWORD", "guest");
    private static final String RABBITMQ_VHOST =
            System.getenv().getOrDefault("RABBITMQ_VHOST",    "/");

    /** Servicio SMTP utilizado para el envío real de correos. */
    @Inject private SmtpEmailService smtpService;

    /** Renderizador de plantillas HTML para cada tipo de correo. */
    @Inject private EmailTemplateRenderer renderer;

    /** Mapeador JSON configurado con soporte para tipos de fecha/hora de Java 8+. */
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /** Conexión activa al broker RabbitMQ; puede ser {@code null} entre reconexiones. */
    private Connection connection;

    /** Canal AMQP activo; puede ser {@code null} entre reconexiones. */
    private Channel channel;

    /** Executor de hilo único que aloja el bucle de consumo y reconexión. */
    private ExecutorService executor;

    /**
     * Indica si el consumidor debe seguir en ejecución.
     * Se establece {@code false} en {@link #stop()} para salir del bucle principal.
     */
    private volatile boolean running = false;

    // ── Ciclo de vida CDI ─────────────────────────────────────────────────────

    /**
     * Inicializa el consumidor cuando el scope {@code ApplicationScoped} arranca.
     *
     * <p>Crea un executor de hilo único marcado como daemon y envía la tarea
     * de consumo {@link #runConsumer()} para ejecución en segundo plano.</p>
     *
     * @param ignored evento CDI de inicialización del scope; no se utiliza directamente.
     */
    public void onStart(@Observes @Initialized(ApplicationScoped.class) Object ignored) {
        running  = true;
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "email-rabbitmq-consumer");
            t.setDaemon(true);
            return t;
        });
        executor.submit(this::runConsumer);
        LOG.info("EmailConsumer arrancado — escuchando cola: " + QUEUE);
    }

    /**
     * Detiene el consumidor de forma ordenada al destruir el bean CDI.
     *
     * <p>Cierra el canal y la conexión AMQP de forma independiente para garantizar
     * que los errores en el cierre del canal no impidan cerrar la conexión.
     * Finalmente interrumpe el executor.</p>
     */
    @PreDestroy
    void stop() {
        running = false;
        closeChannel();
        closeConnection();
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
     * canal se cierra (por error o por petición de parada). Si {@link #running}
     * sigue siendo {@code true}, se espera 5 segundos antes de reintentar.</p>
     *
     * <p>El flujo en cada iteración es:</p>
     * <ol>
     *   <li>Llamar a {@link #connect()} para abrir conexión y canal.</li>
     *   <li>Registrar un {@link ShutdownListener} que libera el latch al cerrarse.</li>
     *   <li>Registrar el consumidor con {@code basicConsume} en modo manual-ack.</li>
     *   <li>Bloquear en {@code latch.await()} hasta que el canal caiga.</li>
     *   <li>Si se pierde la conexión y {@link #running} es {@code true}, esperar
     *       5 segundos y reintentar.</li>
     * </ol>
     */
    private void runConsumer() {
        while (running) {
            CountDownLatch closeLatch = new CountDownLatch(1);
            try {
                connect();
                // Libera el latch cuando el canal o la conexión se cierran.
                channel.addShutdownListener(cause -> closeLatch.countDown());
                LOG.info("Conectado a RabbitMQ — esperando mensajes en: " + QUEUE);
                channel.basicConsume(QUEUE, false, this::handleDelivery, tag -> closeLatch.countDown());
                // Bloquea este hilo hasta que el canal se cierre.
                closeLatch.await();
            } catch (Exception e) {
                if (!running) break;
                LOG.log(Level.WARNING, "Conexión RabbitMQ perdida — reintentando en 5s", e);
                closeLatch.countDown();
                sleep(5_000);
            }
        }
    }

    /**
     * Establece una nueva conexión y canal con el broker RabbitMQ.
     *
     * <p>Configura la fábrica de conexiones con los parámetros obtenidos de las
     * variables de entorno. La recuperación automática de la librería cliente
     * está deshabilitada porque el bucle {@link #runConsumer()} gestiona la
     * reconexión manualmente. Se establece QoS de 1 mensaje sin confirmar a la
     * vez para garantizar procesamiento ordenado.</p>
     *
     * <p>La topología (exchanges, colas y bindings) se declara a través de
     * {@code infra/rabbitmq/definitions.json} y no se repite aquí.</p>
     *
     * @throws Exception si la conexión o la creación del canal fallan.
     */
    private void connect() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBITMQ_HOST);
        factory.setPort(RABBITMQ_PORT);
        factory.setUsername(RABBITMQ_USER);
        factory.setPassword(RABBITMQ_PASS);
        factory.setVirtualHost(RABBITMQ_VHOST);
        factory.setAutomaticRecoveryEnabled(false); // reconexión manejada manualmente
        factory.setConnectionTimeout(5_000);
        factory.setHandshakeTimeout(10_000);

        connection = factory.newConnection();
        channel    = connection.createChannel();
        channel.basicQos(1);
    }

    // ── Procesamiento de mensajes ──────────────────────────────────────────────

    /**
     * Procesa un mensaje entregado por RabbitMQ desde la cola {@code email.notifications}.
     *
     * <p>El flujo de procesamiento es:</p>
     * <ol>
     *   <li>Deserializar el cuerpo JSON a {@link EmailNotificationEvent}.</li>
     *   <li>Renderizar el correo HTML con {@link EmailTemplateRenderer}.</li>
     *   <li>Enviar el correo vía {@link SmtpEmailService#send}.</li>
     *   <li>Si el envío fue exitoso: ACK al broker.</li>
     *   <li>Si falló: delegar a {@link #nackWithRetry} siguiendo la política de reintentos.</li>
     * </ol>
     *
     * @param consumerTag etiqueta del consumidor asignada por RabbitMQ; no se utiliza.
     * @param delivery    mensaje entregado, incluyendo envelope, propiedades y cuerpo.
     */
    private void handleDelivery(String consumerTag, Delivery delivery) {
        long   deliveryTag = delivery.getEnvelope().getDeliveryTag();
        int    attempt     = getAttemptCount(delivery.getProperties());
        String body        = new String(delivery.getBody(), StandardCharsets.UTF_8);

        try {
            EmailNotificationEvent event = mapper.readValue(body, EmailNotificationEvent.class);
            EmailTemplateRenderer.RenderedEmail email = renderer.render(event);

            boolean sent = smtpService.send(
                    event.getRecipientEmail(),
                    event.getRecipientName(),
                    email.subject(),
                    email.htmlBody(),
                    email.fromEmail(),
                    email.fromName());

            if (sent) {
                channel.basicAck(deliveryTag, false);
                LOG.fine("ACK — correo enviado a " + event.getRecipientEmail()
                        + " tipo=" + event.getEmailType());
            } else {
                nackWithRetry(deliveryTag, attempt, "SMTP falló");
            }

        } catch (Exception e) {
            LOG.log(Level.SEVERE,
                    "Error procesando mensaje (intento " + attempt + "): " + body, e);
            try {
                nackWithRetry(deliveryTag, attempt, e.getMessage());
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
     *   <li><strong>Intentos 1-2</strong> ({@code attempt < MAX_RETRIES}):
     *       NACK con {@code requeue=true}. El mensaje regresa a la cola y se espera
     *       {@code 500 ms * attempt} antes de retornar, introduciendo un retroceso
     *       lineal para no saturar el broker.</li>
     *   <li><strong>Intento 3+</strong> ({@code attempt >= MAX_RETRIES}):
     *       NACK con {@code requeue=false}. RabbitMQ enruta el mensaje al
     *       Dead Letter Exchange (DLX) configurado en {@code definitions.json}.</li>
     * </ul>
     *
     * @param deliveryTag etiqueta de entrega del mensaje a rechazar.
     * @param attempt     número del intento actual (1-based).
     * @param reason      descripción del motivo del fallo para el log.
     * @throws IOException si falla la comunicación AMQP al enviar el NACK.
     */
    private void nackWithRetry(long deliveryTag, int attempt, String reason) throws IOException {
        boolean requeue = attempt < MAX_RETRIES;
        channel.basicNack(deliveryTag, false, requeue);
        if (requeue) {
            LOG.warning("NACK requeue=true (intento " + attempt + "/" + MAX_RETRIES
                    + ") — " + reason);
            sleep(500L * attempt); // retroceso lineal antes del requeue
        } else {
            LOG.severe("NACK requeue=false → DLX (intento " + attempt + ") — " + reason);
        }
    }

    /**
     * Extrae el número de intento actual desde el header {@code x-delivery-count}.
     *
     * <p>RabbitMQ incrementa automáticamente {@code x-delivery-count} cada vez que
     * reencola un mensaje. El valor en el header representa cuántas veces fue
     * reencolado previamente, por lo que se retorna {@code count + 1} para obtener
     * el número de intento actual (1-based).</p>
     *
     * @param props propiedades AMQP del mensaje; puede ser {@code null}.
     * @return número de intento actual; {@code 1} si el header no está presente.
     */
    private int getAttemptCount(AMQP.BasicProperties props) {
        if (props == null || props.getHeaders() == null) return 1;
        Object count = props.getHeaders().get("x-delivery-count");
        if (count instanceof Number n) return n.intValue() + 1;
        return 1;
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    /**
     * Cierra el canal AMQP activo, ignorando errores si ya estaba cerrado.
     */
    private void closeChannel() {
        try {
            if (channel != null) channel.close();
        } catch (Exception e) {
            LOG.warning("Error cerrando canal RabbitMQ: " + e.getMessage());
        }
    }

    /**
     * Cierra la conexión AMQP activa, ignorando errores si ya estaba cerrada.
     */
    private void closeConnection() {
        try {
            if (connection != null) connection.close();
        } catch (Exception e) {
            LOG.warning("Error cerrando conexión RabbitMQ: " + e.getMessage());
        }
    }

    /**
     * Suspende el hilo actual el tiempo indicado, restaurando el flag de
     * interrupción si el hilo es interrumpido durante la espera.
     *
     * @param ms milisegundos a esperar.
     */
    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
