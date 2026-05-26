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

    private ConnectionFactory factory;
    private Connection        connection;

    @PostConstruct
    void init() {
        factory = new ConnectionFactory();
        factory.setHost(getEnv("RABBITMQ_HOST", "localhost"));
        factory.setPort(Integer.parseInt(getEnv("RABBITMQ_PORT", "5672")));
        factory.setUsername(getEnv("RABBITMQ_USER", "guest"));
        factory.setPassword(getEnv("RABBITMQ_PASSWORD", "guest"));
        factory.setVirtualHost(getEnv("RABBITMQ_VHOST", "/"));
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(5000);
        factory.setConnectionTimeout(3000);
        // Intento inicial no bloqueante: si falla se reintenta en cada publish
        tryConnect("startup");
    }

    /** Intenta (re)conectar a RabbitMQ. No lanza excepción — deja connection=null si falla. */
    private synchronized void tryConnect(String context) {
        if (connection != null && connection.isOpen()) return;
        try {
            connection = factory.newConnection("puj-platform");
            LOG.info("RabbitMQ conectado (" + context + ").");
        } catch (Exception e) {
            LOG.warning("RabbitMQ no disponible (" + context + ") — se reintentará en el próximo evento.");
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

    /**
     * Devuelve true si hay conexión activa. Si no la hay, intenta reconectar
     * en ese instante (lazy reconnect). Esto cubre el race condition de K8s
     * donde RabbitMQ puede no estar listo cuando el servicio arranca.
     */
    public boolean isAvailable() {
        if (connection != null && connection.isOpen()) return true;
        tryConnect("lazy-reconnect");
        return connection != null && connection.isOpen();
    }

    private String getEnv(String key, String def) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : def;
    }
}
