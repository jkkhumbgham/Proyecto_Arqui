package com.puj.events.publisher;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class RabbitMQConnectionProvider {

    private static final Logger LOG = Logger.getLogger(RabbitMQConnectionProvider.class.getName());

    public static final String EXCHANGE      = "platform.events";
    public static final String QUEUE_ANALYTICS = "analytics.results";
    public static final String QUEUE_EMAIL     = "email.notifications";

    private Connection connection;

    @PostConstruct
    void init() {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(getEnv("RABBITMQ_HOST", "localhost"));
            factory.setPort(Integer.parseInt(getEnv("RABBITMQ_PORT", "5672")));
            factory.setUsername(getEnv("RABBITMQ_USER", "guest"));
            factory.setPassword(getEnv("RABBITMQ_PASSWORD", "guest"));
            factory.setVirtualHost(getEnv("RABBITMQ_VHOST", "/"));
            factory.setAutomaticRecoveryEnabled(true);
            factory.setNetworkRecoveryInterval(5000);

            this.connection = factory.newConnection("puj-platform");
            LOG.info("RabbitMQ connection established.");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "No se pudo conectar a RabbitMQ — eventos asíncronos deshabilitados.", e);
        }
    }

    @PreDestroy
    void close() {
        try {
            if (connection != null && connection.isOpen()) connection.close();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error cerrando conexión RabbitMQ", e);
        }
    }

    public Connection getConnection() { return connection; }

    public boolean isAvailable() {
        return connection != null && connection.isOpen();
    }

    private String getEnv(String key, String def) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : def;
    }
}
