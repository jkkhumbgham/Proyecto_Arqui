package com.puj.eventos.publicador;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Proveedor de conexión AMQP a RabbitMQ para la plataforma PUJ.
 *
 * <p>Gestiona el ciclo de vida de una única conexión compartida ({@link Connection})
 * dentro del alcance de la aplicación ({@code @ApplicationScoped}). La conexión se
 * intenta establecer de forma no bloqueante durante el arranque del servicio
 * ({@link #init()}); si RabbitMQ no está disponible en ese momento (escenario
 * frecuente en entornos Kubernetes donde los pods arrancan en paralelo), se reintenta
 * automáticamente en cada publicación mediante reconexión lazy en
 * {@link #estaDisponible()}.</p>
 *
 * <p>El cliente AMQP está configurado con recuperación automática ({@code automatic
 * recovery}) e intervalo de 5 segundos entre reintentos.</p>
 *
 * <h2>Variables de entorno</h2>
 * <table border="1">
 *   <tr><th>Variable</th><th>Valor por defecto</th></tr>
 *   <tr><td>{@code RABBITMQ_HOST}</td><td>{@code localhost}</td></tr>
 *   <tr><td>{@code RABBITMQ_PORT}</td><td>{@code 5672}</td></tr>
 *   <tr><td>{@code RABBITMQ_USER}</td><td>{@code guest}</td></tr>
 *   <tr><td>{@code RABBITMQ_PASSWORD}</td><td>{@code guest}</td></tr>
 *   <tr><td>{@code RABBITMQ_VHOST}</td><td>{@code /}</td></tr>
 * </table>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class ProveedorConexionRabbitMQ {

    private static final Logger LOG =
            Logger.getLogger(ProveedorConexionRabbitMQ.class.getName());

    /**
     * Nombre del exchange de tipo {@code topic} al que se publican todos los
     * eventos de la plataforma.
     */
    public static final String EXCHANGE = "platform.events";

    /**
     * Nombre de la cola donde el servicio de analíticas deposita sus resultados.
     */
    public static final String QUEUE_ANALYTICS = "analytics.results";

    /**
     * Nombre de la cola donde el servicio de correo recibe las solicitudes de
     * notificación.
     */
    public static final String QUEUE_EMAIL = "email.notifications";

    /** Fábrica de conexiones AMQP configurada con los parámetros del entorno. */
    private ConnectionFactory fabrica;

    /** Conexión AMQP activa, o {@code null} si RabbitMQ no está disponible. */
    private Connection conexion;

    /**
     * Inicializa la fábrica de conexiones con los parámetros leídos de las
     * variables de entorno y realiza el primer intento de conexión a RabbitMQ.
     *
     * <p>Si la conexión falla en el arranque, no se lanza ninguna excepción;
     * la reconexión se realizará de forma lazy en la primera publicación.</p>
     */
    @PostConstruct
    void init() {
        fabrica = new ConnectionFactory();
        fabrica.setHost(obtenerEnv("RABBITMQ_HOST", "localhost"));
        fabrica.setPort(Integer.parseInt(obtenerEnv("RABBITMQ_PORT", "5672")));
        fabrica.setUsername(obtenerEnv("RABBITMQ_USER", "guest"));
        fabrica.setPassword(obtenerEnv("RABBITMQ_PASSWORD", "guest"));
        fabrica.setVirtualHost(obtenerEnv("RABBITMQ_VHOST", "/"));
        fabrica.setAutomaticRecoveryEnabled(true);
        fabrica.setNetworkRecoveryInterval(5000);
        fabrica.setConnectionTimeout(3000);
        // Intento inicial no bloqueante: si falla se reintenta en cada publish
        intentarConectar("startup");
    }

    /**
     * Cierra la conexión AMQP de forma ordenada al destruir el bean.
     *
     * <p>Los errores durante el cierre se registran como {@code WARNING} pero no
     * se propagan.</p>
     */
    @PreDestroy
    void close() {
        try {
            if (conexion != null && conexion.isOpen()) {
                conexion.close();
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error cerrando conexión RabbitMQ", e);
        }
    }

    /**
     * Devuelve la conexión AMQP activa, o {@code null} si no hay conexión establecida.
     *
     * <p>Se recomienda verificar disponibilidad con {@link #estaDisponible()} antes de
     * usar esta conexión.</p>
     *
     * @return conexión AMQP activa, o {@code null}
     */
    public Connection obtenerConexion() {
        return conexion;
    }

    /**
     * Indica si hay una conexión AMQP activa y realiza una reconexión lazy si no la hay.
     *
     * <p>Este método cubre el escenario de Kubernetes donde RabbitMQ puede no estar
     * listo cuando el microservicio arranca. En lugar de fallar en el arranque, la
     * reconexión se intenta aquí de forma transparente antes de cada publicación.</p>
     *
     * @return {@code true} si la conexión está abierta después de la comprobación
     */
    public boolean estaDisponible() {
        if (conexion != null && conexion.isOpen()) {
            return true;
        }
        intentarConectar("lazy-reconnect");
        return conexion != null && conexion.isOpen();
    }

    // -------------------------------------------------------------------------
    // Helpers privados
    // -------------------------------------------------------------------------

    /**
     * Intenta crear una nueva conexión AMQP; si ya existe una abierta no hace nada.
     *
     * <p>El método es {@code synchronized} para evitar que múltiples hilos de
     * publicación intenten conectarse simultáneamente. Los errores de conexión se
     * registran como {@code WARNING} y dejan {@code conexion} en {@code null}.</p>
     *
     * @param contexto etiqueta de contexto incluida en el mensaje de log
     *                 (p. ej. {@code "startup"}, {@code "lazy-reconnect"})
     */
    private synchronized void intentarConectar(String contexto) {
        if (conexion != null && conexion.isOpen()) {
            return;
        }
        try {
            conexion = fabrica.newConnection("puj-platform");
            LOG.info("RabbitMQ conectado (" + contexto + ").");
        } catch (Exception e) {
            LOG.warning("RabbitMQ no disponible (" + contexto
                    + ") — se reintentará en el próximo evento.");
        }
    }

    /**
     * Devuelve el valor de una variable de entorno o el valor por defecto si está ausente
     * o en blanco.
     *
     * @param clave    variable de entorno a leer
     * @param defecto  valor por defecto cuando la variable no existe o está vacía
     * @return valor de la variable de entorno o {@code defecto}
     */
    private String obtenerEnv(String clave, String defecto) {
        String valor = System.getenv(clave);
        return (valor != null && !valor.isBlank()) ? valor : defecto;
    }
}
