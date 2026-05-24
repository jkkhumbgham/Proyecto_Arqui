package com.puj.collaboration.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.puj.collaboration.service.NotificationService;
import com.puj.events.publisher.RabbitMQConnectionProvider;
import com.rabbitmq.client.Channel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class NotificationConsumer {

    private static final Logger LOG   = Logger.getLogger(NotificationConsumer.class.getName());
    private static final String QUEUE = "notifications.events";

    @Inject private RabbitMQConnectionProvider connectionProvider;
    @Inject private NotificationService        notificationService;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private Channel         channel;
    private ExecutorService executor;

    @PostConstruct
    void start() {
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "notification-consumer");
            t.setDaemon(true);
            return t;
        });
        executor.submit(this::connectAndConsume);
    }

    private void connectAndConsume() {
        int retries = 0;
        while (retries < 10) {
            try {
                if (!connectionProvider.isAvailable()) {
                    Thread.sleep(3000);
                    retries++;
                    continue;
                }
                channel = connectionProvider.getConnection().createChannel();
                channel.queueDeclare(QUEUE, true, false, false, null);
                channel.queueBind(QUEUE, RabbitMQConnectionProvider.EXCHANGE, "analytics.lesson_completed");
                channel.queueBind(QUEUE, RabbitMQConnectionProvider.EXCHANGE, "analytics.assessment_submitted");
                channel.queueBind(QUEUE, RabbitMQConnectionProvider.EXCHANGE, "analytics.certificate_issued");
                channel.queueBind(QUEUE, RabbitMQConnectionProvider.EXCHANGE, "analytics.learning_path_enrolled");
                channel.basicQos(10);

                channel.basicConsume(QUEUE, false, (tag, delivery) -> {
                    try {
                        handleDelivery(delivery.getProperties().getType(),
                                new String(delivery.getBody(), StandardCharsets.UTF_8));
                        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Error procesando notificación", e);
                        try { channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false); }
                        catch (IOException ex) { LOG.log(Level.WARNING, "Error en nack", ex); }
                    }
                }, tag -> LOG.warning("Notification consumer cancelled: " + tag));

                LOG.info("NotificationConsumer activo en queue: " + QUEUE);
                return;
            } catch (Exception e) {
                retries++;
                LOG.log(Level.WARNING, "NotificationConsumer no pudo conectar (intento " + retries + ")", e);
                try { Thread.sleep(5000); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void handleDelivery(String eventType, String json) throws Exception {
        JsonNode node = mapper.readTree(json);
        if (eventType == null) eventType = node.path("eventType").asText("");

        switch (eventType) {
            case "LESSON_COMPLETED" -> notificationService.persist(
                    UUID.fromString(node.path("userId").asText()),
                    "LESSON_COMPLETED",
                    "Lección completada",
                    "Has completado una lección.",
                    node.path("lessonId").asText(null));

            case "ASSESSMENT_SUBMITTED" -> {
                if (node.path("passed").asBoolean(false)) {
                    notificationService.persist(
                            UUID.fromString(node.path("userId").asText()),
                            "ASSESSMENT_PASSED",
                            "¡Evaluación aprobada!",
                            "Aprobaste con " + String.format("%.1f", node.path("score").asDouble()) + " pts.",
                            node.path("assessmentId").asText(null));
                }
            }

            case "CERTIFICATE_ISSUED" -> notificationService.persist(
                    UUID.fromString(node.path("userId").asText()),
                    "CERTIFICATE_ISSUED",
                    "¡Certificado disponible!",
                    "Tu certificado del curso \"" + node.path("courseTitle").asText() + "\" está listo.",
                    node.path("courseId").asText(null));

            case "LEARNING_PATH_ENROLLED" -> notificationService.persist(
                    UUID.fromString(node.path("userId").asText()),
                    "PATH_ENROLLED",
                    "Inscripción en ruta",
                    "Te inscribiste en la ruta \"" + node.path("learningPathTitle").asText() + "\".",
                    node.path("learningPathId").asText(null));

            default -> { /* ignore */ }
        }
    }

    @PreDestroy
    void stop() {
        try { if (channel != null && channel.isOpen()) channel.close(); } catch (Exception ignored) {}
        if (executor != null) executor.shutdownNow();
    }
}
