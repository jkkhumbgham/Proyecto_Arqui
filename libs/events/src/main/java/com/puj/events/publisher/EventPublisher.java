package com.puj.events.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.puj.events.BaseEvent;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class EventPublisher {

    private static final Logger LOG = Logger.getLogger(EventPublisher.class.getName());

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

            LOG.fine("Evento publicado: " + event.getEventType() + " [" + event.getEventId() + "]");

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error publicando evento: " + event.getEventType(), e);
        }
    }

    public void publishAnalytics(BaseEvent event) {
        publish(event, "analytics." + event.getEventType().toLowerCase());
    }

    public void publishEmail(BaseEvent event) {
        publish(event, "email." + event.getEventType().toLowerCase());
    }
}
