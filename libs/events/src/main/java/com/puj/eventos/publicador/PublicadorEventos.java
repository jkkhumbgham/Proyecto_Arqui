package com.puj.eventos.publicador;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.puj.eventos.EventoBase;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio central de publicación de eventos sobre RabbitMQ.
 *
 * <p>Expone tres métodos de publicación que difieren en el exchange / routing-key
 * de destino y en el formato del cuerpo del mensaje:</p>
 * <ul>
 *   <li>{@link #publicar(EventoBase, String)} — publicación genérica al exchange
 *       {@code platform.events} con routing-key arbitraria.</li>
 *   <li>{@link #publicarAnaliticas(EventoBase)} — encapsula el evento en un sobre
 *       compatible con MassTransit para ser consumido por el servicio .NET de
 *       analíticas.</li>
 *   <li>{@link #publicarCorreo(EventoBase)} — publica al routing-key
 *       {@code email.<tipo>} para que el servicio de correo lo procese.</li>
 * </ul>
 *
 * <p>Si RabbitMQ no está disponible en el momento de la publicación, el evento
 * se descarta con un {@code WARNING} en el log y se intenta una reconexión
 * lazy en la siguiente llamada a {@link ProveedorConexionRabbitMQ#estaDisponible()}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class PublicadorEventos {

    private static final Logger LOG = Logger.getLogger(PublicadorEventos.class.getName());

    /**
     * Prefijo de namespace MassTransit usado al construir el campo {@code messageType}
     * del sobre JSON que consume el servicio de analíticas (.NET).
     */
    private static final String MASSTRANSIT_NS = "urn:message:Puj.Analytics.Messages:";

    /**
     * Mapeo de tipos de evento de la plataforma al nombre de mensaje MassTransit
     * correspondiente en el servicio de analíticas.
     */
    private static final Map<String, String> ANALYTICS_TYPE_MAP = Map.of(
        "COURSE_ENROLLED",      "CourseEnrolledMessage",
        "ASSESSMENT_SUBMITTED", "AssessmentSubmittedMessage",
        "USER_REGISTERED",      "UserRegisteredMessage",
        "USER_LOGGED_IN",       "UserLoggedInMessage",
        "LESSON_COMPLETED",     "LessonCompletedMessage"
    );

    /** Serializador Jackson configurado con soporte para {@code java.time}. */
    private final ObjectMapper mapper;

    /** Proveedor de la conexión AMQP a RabbitMQ. */
    @Inject
    private ProveedorConexionRabbitMQ proveedorConexion;

    /**
     * Inicializa el serializador Jackson con el módulo {@code JavaTimeModule}
     * y deshabilita la escritura de fechas como timestamps numéricos.
     */
    public PublicadorEventos() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Publica un evento en el exchange {@code platform.events} con la routing-key indicada.
     *
     * <p>El mensaje se serializa como JSON con {@code contentType: application/json}
     * y modo de entrega persistente (deliveryMode 2). Si la conexión con RabbitMQ
     * no está disponible, el evento se descarta sin lanzar excepción.</p>
     *
     * @param evento     evento de dominio a publicar; no debe ser {@code null}
     * @param routingKey clave de enrutamiento AMQP del mensaje
     */
    public void publicar(EventoBase evento, String routingKey) {
        if (!proveedorConexion.estaDisponible()) {
            LOG.warning("RabbitMQ no disponible — evento descartado: " + evento.getEventType());
            return;
        }

        try (Channel channel = proveedorConexion.obtenerConexion().createChannel()) {
            byte[] cuerpo = mapper.writeValueAsBytes(evento);

            AMQP.BasicProperties props = construirPropiedadesEstandar(evento, "application/json");

            channel.basicPublish(
                    ProveedorConexionRabbitMQ.EXCHANGE,
                    routingKey,
                    props,
                    cuerpo
            );

            LOG.info("Evento publicado: " + evento.getEventType()
                    + " [" + evento.getIdEvento() + "]");

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error publicando evento: " + evento.getEventType(), e);
        }
    }

    /**
     * Publica un evento envuelto en un sobre MassTransit para el servicio de analíticas.
     *
     * <p>Construye un JSON con la estructura que MassTransit espera
     * ({@code messageType}, {@code message}, {@code messageId}, {@code sentTime}) y lo
     * envía al routing-key {@code analytics.<tipo_en_minúsculas>} con el content-type
     * {@code application/vnd.masstransit+json}.</p>
     *
     * <p>Si el tipo de evento no tiene un mapping en {@link #ANALYTICS_TYPE_MAP}, el
     * evento se descarta con un {@code WARNING} en el log.</p>
     *
     * @param evento evento de dominio a publicar hacia el servicio de analíticas
     */
    public void publicarAnaliticas(EventoBase evento) {
        if (!proveedorConexion.estaDisponible()) {
            LOG.warning("RabbitMQ no disponible — evento analytics descartado: "
                    + evento.getEventType());
            return;
        }

        String tipoMassTransit = ANALYTICS_TYPE_MAP.get(evento.getEventType());
        if (tipoMassTransit == null) {
            LOG.warning("Sin mapping MassTransit para evento: " + evento.getEventType());
            return;
        }

        try (Channel channel = proveedorConexion.obtenerConexion().createChannel()) {
            byte[] cuerpo = construirSobreMassTransit(evento, tipoMassTransit);

            AMQP.BasicProperties props = construirPropiedadesEstandar(
                    evento, "application/vnd.masstransit+json");

            channel.basicPublish(
                    ProveedorConexionRabbitMQ.EXCHANGE,
                    "analytics." + evento.getEventType().toLowerCase(),
                    props,
                    cuerpo
            );

            LOG.info("Evento analytics publicado [MassTransit]: " + evento.getEventType()
                    + " [" + evento.getIdEvento() + "] → analytics."
                    + evento.getEventType().toLowerCase());

        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "Error publicando evento analytics: " + evento.getEventType(), e);
            // Intentar reconexión lazy antes del siguiente evento
            proveedorConexion.estaDisponible();
        }
    }

    /**
     * Publica un evento de notificación por correo electrónico.
     *
     * <p>Delega en {@link #publicar(EventoBase, String)} usando la routing-key
     * {@code email.<tipo_en_minúsculas>}.</p>
     *
     * @param evento evento a publicar hacia el servicio de correo
     */
    public void publicarCorreo(EventoBase evento) {
        publicar(evento, "email." + evento.getEventType().toLowerCase());
    }

    // -------------------------------------------------------------------------
    // Helpers privados
    // -------------------------------------------------------------------------

    /**
     * Construye el cuerpo JSON del sobre MassTransit que consume el servicio de analíticas.
     *
     * <p>El sobre incluye los campos {@code messageType} (array con el URI de tipo),
     * {@code message} (el evento serializado), {@code messageId} y {@code sentTime}.</p>
     *
     * @param evento          evento que se incluirá como cuerpo del sobre
     * @param tipoMassTransit nombre del tipo MassTransit (sin prefijo de namespace)
     * @return bytes del JSON del sobre serializado
     * @throws Exception si Jackson no puede serializar el evento
     */
    private byte[] construirSobreMassTransit(EventoBase evento, String tipoMassTransit)
            throws Exception {
        ObjectNode nodoMensaje = (ObjectNode) mapper.valueToTree(evento);

        ObjectNode sobre = mapper.createObjectNode();
        sobre.putArray("messageType").add(MASSTRANSIT_NS + tipoMassTransit);
        sobre.set("message", nodoMensaje);
        sobre.put("messageId", evento.getIdEvento());
        sobre.put("sentTime", evento.getOcurridoEn().toString());

        return mapper.writeValueAsBytes(sobre);
    }

    /**
     * Construye las propiedades AMQP estándar para un mensaje de la plataforma.
     *
     * @param evento      evento del que se extraen el ID y el tipo
     * @param contentType valor del header {@code Content-Type} del mensaje
     * @return instancia de {@link AMQP.BasicProperties} lista para usar en
     *         {@code channel.basicPublish}
     */
    private AMQP.BasicProperties construirPropiedadesEstandar(EventoBase evento, String contentType) {
        return new AMQP.BasicProperties.Builder()
                .contentType(contentType)
                .deliveryMode(2)
                .messageId(evento.getIdEvento())
                .type(evento.getEventType())
                .build();
    }
}
