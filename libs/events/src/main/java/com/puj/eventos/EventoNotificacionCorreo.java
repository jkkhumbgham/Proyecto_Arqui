package com.puj.eventos;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Evento que solicita el envío de una notificación por correo electrónico.
 *
 * <p>Cualquier microservicio de la plataforma puede publicar este evento cuando
 * necesite que el servicio de correo entregue un mensaje al destinatario. El campo
 * {@link TipoCorreo} discrimina la plantilla a utilizar, mientras que
 * {@code parametrosPlantilla} contiene las variables específicas de dicha plantilla.</p>
 *
 * <p>Se identifica con el tipo {@code "EMAIL_NOTIFICATION"} en el sistema de
 * mensajería y en la deserialización polimórfica de {@link EventoBase}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public class EventoNotificacionCorreo extends EventoBase {

    /**
     * Enumeración de los tipos de correo electrónico soportados por la plataforma.
     *
     * <p>Cada valor se corresponde con una plantilla de correo predefinida en el
     * servicio de notificaciones.</p>
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public enum TipoCorreo {
        /** Bienvenida al nuevo usuario tras su registro exitoso. */
        WELCOME,

        /** Enlace para restablecer la contraseña olvidada. */
        PASSWORD_RESET,

        /** Confirmación de inscripción a un curso. */
        ENROLLMENT_CONFIRMED,

        /** Notificación del resultado de una evaluación calificada. */
        ASSESSMENT_GRADED,

        /** Felicitación por completar un curso en su totalidad. */
        COURSE_COMPLETED
    }

    /** Dirección de correo electrónico del destinatario. */
    @JsonProperty("recipientEmail")
    private String              correoDestinatario;

    /** Nombre completo o nombre de pila del destinatario para personalizar el saludo. */
    @JsonProperty("recipientName")
    private String              nombreDestinatario;

    /** Tipo de correo que determina la plantilla a usar. */
    @JsonProperty("emailType")
    private TipoCorreo          tipoCorreo;

    /**
     * Parámetros variables que serán interpolados en la plantilla del correo.
     * Las claves y valores dependen del {@link TipoCorreo} seleccionado.
     */
    @JsonProperty("templateParams")
    private Map<String, String> parametrosPlantilla;

    /**
     * Constructor sin argumentos requerido por Jackson para la deserialización.
     */
    public EventoNotificacionCorreo() {
        super();
    }

    /**
     * Constructor principal que inicializa todos los campos del evento.
     *
     * @param correoDestinatario dirección de correo del destinatario
     * @param nombreDestinatario nombre del destinatario para personalización
     * @param tipoCorreo         tipo de correo que selecciona la plantilla a usar
     * @param parametrosPlantilla mapa de variables a interpolar en la plantilla
     */
    public EventoNotificacionCorreo(
            String correoDestinatario,
            String nombreDestinatario,
            TipoCorreo tipoCorreo,
            Map<String, String> parametrosPlantilla) {
        super("EMAIL_NOTIFICATION", "platform");
        this.correoDestinatario   = correoDestinatario;
        this.nombreDestinatario   = nombreDestinatario;
        this.tipoCorreo           = tipoCorreo;
        this.parametrosPlantilla  = parametrosPlantilla;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String              getCorreoDestinatario()  { return correoDestinatario; }
    public String              getNombreDestinatario()  { return nombreDestinatario; }
    public TipoCorreo          getTipoCorreo()          { return tipoCorreo; }
    public Map<String, String> getParametrosPlantilla() { return parametrosPlantilla; }

    // -------------------------------------------------------------------------
    // Setters (requeridos por Jackson para deserialización)
    // -------------------------------------------------------------------------

    public void setCorreoDestinatario(String correoDestinatario)         { this.correoDestinatario = correoDestinatario; }
    public void setNombreDestinatario(String nombreDestinatario)         { this.nombreDestinatario = nombreDestinatario; }
    public void setTipoCorreo(TipoCorreo tipoCorreo)                     { this.tipoCorreo = tipoCorreo; }
    public void setParametrosPlantilla(Map<String, String> parametrosPlantilla) { this.parametrosPlantilla = parametrosPlantilla; }
}
