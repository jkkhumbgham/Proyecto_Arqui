package com.puj.users.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.puj.events.publisher.RabbitMQConnectionProvider;
import com.puj.users.service.GamificationService;
import com.rabbitmq.client.*;
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
public class GamificationConsumer {

    private static final Logger LOG       = Logger.getLogger(GamificationConsumer.class.getName());
    private static final String QUEUE     = "gamification.events";

    @Inject private RabbitMQConnectionProvider connectionProvider;
    @Inject private GamificationService        gamificationService;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private Channel          channel;
    private ExecutorService  executor;

    @PostConstruct
    void start() {
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "gamification-consumer");
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
                channel.queueBind(QUEUE, RabbitMQConnectionProvider.EXCHANGE, "analytics.forum_post_created");
                channel.basicQos(10);

                channel.basicConsume(QUEUE, false, (tag, delivery) -> {
                    try {
                        handle(delivery.getProperties().getType(),
                               new String(delivery.getBody(), StandardCharsets.UTF_8));
                        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Error procesando evento de gamificación", e);
                        try { channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false); }
                        catch (IOException ex) { LOG.log(Level.WARNING, "Error en nack", ex); }
                    }
                }, tag -> LOG.warning("Gamification consumer cancelled: " + tag));

                LOG.info("GamificationConsumer activo en queue: " + QUEUE);
                return;

            } catch (Exception e) {
                retries++;
                LOG.log(Level.WARNING, "GamificationConsumer no pudo conectar (intento " + retries + ")", e);
                try { Thread.sleep(5000); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void handle(String eventType, String json) throws Exception {
        if (eventType == null) {
            JsonNode node = mapper.readTree(json);
            eventType = node.path("eventType").asText("");
        }
        JsonNode node = mapper.readTree(json);

        switch (eventType) {
            case "LESSON_COMPLETED" -> {
                UUID userId = UUID.fromString(node.path("userId").asText());
                String courseId = node.path("courseId").asText();
                gamificationService.awardPoints(userId, "LESSON_COMPLETED", courseId);
            }
            case "ASSESSMENT_SUBMITTED" -> {
                UUID userId = UUID.fromString(node.path("userId").asText());
                String assessmentId = node.path("assessmentId").asText();
                boolean passed = node.path("passed").asBoolean(false);
                gamificationService.awardAssessmentPoints(userId, assessmentId, passed);
            }
            case "FORUM_POST_CREATED" -> {
                UUID userId = UUID.fromString(node.path("userId").asText());
                boolean isThread = node.path("isThread").asBoolean(false);
                String forumId = node.path("forumId").asText();
                gamificationService.awardPoints(userId,
                        isThread ? "FORUM_THREAD_CREATED" : "FORUM_POST_CREATED", forumId);
            }
            default -> LOG.fine("Evento ignorado por gamification: " + eventType);
        }
    }

    @PreDestroy
    void stop() {
        try { if (channel != null && channel.isOpen()) channel.close(); } catch (Exception ignored) {}
        if (executor != null) executor.shutdownNow();
    }
}
