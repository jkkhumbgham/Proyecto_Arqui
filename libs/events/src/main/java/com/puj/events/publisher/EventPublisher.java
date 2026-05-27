package com.puj.events.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.puj.events.BaseEvent;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class EventPublisher {

    private static final Logger LOG = Logger.getLogger(EventPublisher.class.getName());

    private static final String MASSTRANSIT_NS = "urn:message:Puj.Analytics.Messages:";
    private static final Map<String, String> ANALYTICS_TYPE_MAP = Map.of(
        "COURSE_ENROLLED",      "CourseEnrolledMessage",
        "ASSESSMENT_SUBMITTED", "AssessmentSubmittedMessage",
        "USER_REGISTERED",      "UserRegisteredMessage",
        "USER_LOGGED_IN",       "UserLoggedInMessage",
        "LESSON_COMPLETED",     "LessonCompletedMessage"
    );

    private final ObjectMapper mapper;

    @Inject
    private RabbitMQConnectionProvider connectionProvider;

    public EventPublisher() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void publish(BaseEvent event, String routingKey) {
        if (!connectionProvider.isAvailable()) {
            LOG.warning("RabbitMQ no disponible — evento descartado: " + event.getEventType());
            return;
        }

        try (Channel channel = connectionProvider.getConnection().createChannel()) {
            byte[] body = mapper.writeValueAsBytes(event);

            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .contentType("application/json")
                    .deliveryMode(2)
                    .messageId(event.getEventId())
                    .type(event.getEventType())
                    .build();

            channel.basicPublish(
                    RabbitMQConnectionProvider.EXCHANGE,
                    routingKey,
                    props,
                    body
            );

            LOG.info("Evento publicado: " + event.getEventType() + " [" + event.getEventId() + "]");

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error publicando evento: " + event.getEventType(), e);
        }
    }

    public void publishAnalytics(BaseEvent event) {
        if (!connectionProvider.isAvailable()) {
            LOG.warning("RabbitMQ no disponible — evento analytics descartado: " + event.getEventType());
            return;
        }

        String massTransitType = ANALYTICS_TYPE_MAP.get(event.getEventType());
        if (massTransitType == null) {
            LOG.warning("Sin mapping MassTransit para evento: " + event.getEventType());
            return;
        }

        try (Channel channel = connectionProvider.getConnection().createChannel()) {
            // Build MassTransit JSON envelope so analytics-service (.NET) can deserialize
            ObjectNode messageNode = (ObjectNode) mapper.valueToTree(event);

            ObjectNode envelope = mapper.createObjectNode();
            envelope.putArray("messageType").add(MASSTRANSIT_NS + massTransitType);
            envelope.set("message", messageNode);
            envelope.put("messageId", event.getEventId());
            envelope.put("sentTime", event.getOccurredAt().toString());

            byte[] body = mapper.writeValueAsBytes(envelope);

            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .contentType("application/vnd.masstransit+json")
                    .deliveryMode(2)
                    .messageId(event.getEventId())
                    .type(event.getEventType())
                    .build();

            channel.basicPublish(
                    RabbitMQConnectionProvider.EXCHANGE,
                    "analytics." + event.getEventType().toLowerCase(),
                    props,
                    body
            );

            LOG.info("Evento analytics publicado [MassTransit]: " + event.getEventType()
                    + " [" + event.getEventId() + "] → analytics." + event.getEventType().toLowerCase());

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error publicando evento analytics: " + event.getEventType(), e);
            // Intentar reconexión lazy antes del siguiente evento
            connectionProvider.isAvailable();
        }
    }

    public void publishEmail(BaseEvent event) {
        publish(event, "email." + event.getEventType().toLowerCase());
    }
}
