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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Consume la cola email.notifications de RabbitMQ.
 *
 * Estrategia de retry:
 *   - Intento 1 y 2: NACK con requeue=true → vuelve a la cabeza de la cola
 *   - Intento 3:     NACK con requeue=false → va al Dead Letter Exchange
 *
 * El header x-delivery-count (añadido por RabbitMQ) se usa para contar intentos.
 */
@ApplicationScoped
public class EmailConsumer {

    private static final Logger  LOG        = Logger.getLogger(EmailConsumer.class.getName());
    private static final String  QUEUE      = "email.notifications";
    private static final int     MAX_RETRIES = 3;

    private static final String RABBITMQ_HOST  = System.getenv().getOrDefault("RABBITMQ_HOST",  "localhost");
    private static final int    RABBITMQ_PORT  = Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5672"));
    private static final String RABBITMQ_USER  = System.getenv().getOrDefault("RABBITMQ_USER",  "guest");
    private static final String RABBITMQ_PASS  = System.getenv().getOrDefault("RABBITMQ_PASSWORD", "guest");
    private static final String RABBITMQ_VHOST = System.getenv().getOrDefault("RABBITMQ_VHOST", "/");

    @Inject private SmtpEmailService      smtpService;
    @Inject private EmailTemplateRenderer renderer;

    private final ObjectMapper    mapper   = new ObjectMapper().registerModule(new JavaTimeModule());
    private       Connection      connection;
    private       Channel         channel;
    private       ExecutorService executor;
    private volatile boolean      running  = false;

    /** CDI observer: se ejecuta cuando el ApplicationScope se inicializa (arranque del servidor). */
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

    @PreDestroy
    void stop() {
        running = false;
        try { if (channel    != null) channel.close();    } catch (Exception ignored) {}
        try { if (connection != null) connection.close(); } catch (Exception ignored) {}
        if (executor != null) executor.shutdownNow();
    }

    // ── Loop principal con reconexión automática ───────────────────────────────

    private void runConsumer() {
        while (running) {
            try {
                connect();
                LOG.info("Conectado a RabbitMQ — esperando mensajes en: " + QUEUE);
                // basicConsume bloqueante; sale solo por error o shutdown
                channel.basicConsume(QUEUE, false, this::handleDelivery, tag -> {});
                // si llegamos aquí sin excepción, el canal fue cancelado
            } catch (Exception e) {
                if (!running) break;
                LOG.log(Level.WARNING, "Conexión RabbitMQ perdida — reintentando en 5s", e);
                sleep(5_000);
            }
        }
    }

    private void connect() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBITMQ_HOST);
        factory.setPort(RABBITMQ_PORT);
        factory.setUsername(RABBITMQ_USER);
        factory.setPassword(RABBITMQ_PASS);
        factory.setVirtualHost(RABBITMQ_VHOST);
        factory.setAutomaticRecoveryEnabled(false); // manejamos la reconexión manualmente
        factory.setConnectionTimeout(5_000);
        factory.setHandshakeTimeout(10_000);

        connection = factory.newConnection();
        channel    = connection.createChannel();
        channel.basicQos(1); // procesar un mensaje a la vez (prefetch)
    }

    // ── Procesamiento de mensajes ──────────────────────────────────────────────

    private void handleDelivery(String consumerTag, Delivery delivery) {
        long deliveryTag = delivery.getEnvelope().getDeliveryTag();
        int  attempt     = getAttemptCount(delivery.getProperties());
        String body      = new String(delivery.getBody(), StandardCharsets.UTF_8);

        try {
            EmailNotificationEvent event = mapper.readValue(body, EmailNotificationEvent.class);
            EmailTemplateRenderer.RenderedEmail email = renderer.render(event);

            boolean sent = smtpService.send(
                    event.getRecipientEmail(),
                    event.getRecipientName(),
                    email.subject(),
                    email.htmlBody());

            if (sent) {
                channel.basicAck(deliveryTag, false);
                LOG.fine("ACK — correo enviado a " + event.getRecipientEmail()
                        + " tipo=" + event.getEmailType());
            } else {
                nackWithRetry(deliveryTag, attempt, "SMTP falló");
            }

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error procesando mensaje (intento " + attempt + "): " + body, e);
            try { nackWithRetry(deliveryTag, attempt, e.getMessage()); }
            catch (IOException io) { LOG.log(Level.WARNING, "Error enviando NACK", io); }
        }
    }

    private void nackWithRetry(long deliveryTag, int attempt, String reason) throws IOException {
        boolean requeue = attempt < MAX_RETRIES;
        channel.basicNack(deliveryTag, false, requeue);
        if (requeue) {
            LOG.warning("NACK requeue=true (intento " + attempt + "/" + MAX_RETRIES
                    + ") — " + reason);
            sleep(500L * attempt); // back-off lineal antes del requeue
        } else {
            LOG.severe("NACK requeue=false → DLX (intento " + attempt + ") — " + reason);
        }
    }

    private int getAttemptCount(AMQP.BasicProperties props) {
        if (props == null || props.getHeaders() == null) return 1;
        Object count = props.getHeaders().get("x-delivery-count");
        if (count instanceof Number n) return n.intValue() + 1;
        return 1;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
