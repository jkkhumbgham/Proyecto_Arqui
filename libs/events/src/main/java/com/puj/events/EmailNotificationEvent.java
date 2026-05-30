package com.puj.events;

import java.util.Map;

/**
 * Evento que solicita el envío de una notificación por correo electrónico.
 *
 * <p>Cualquier microservicio de la plataforma puede publicar este evento cuando
 * necesite que el servicio de correo entregue un mensaje al destinatario. El campo
 * {@link EmailType} discrimina la plantilla a utilizar, mientras que
 * {@code templateParams} contiene las variables específicas de dicha plantilla.</p>
 *
 * <p>Se identifica con el tipo {@code "EMAIL_NOTIFICATION"} en el sistema de
 * mensajería y en la deserialización polimórfica de {@link BaseEvent}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public class EmailNotificationEvent extends BaseEvent {

    /**
     * Enumeración de los tipos de correo electrónico soportados por la plataforma.
     *
     * <p>Cada valor se corresponde con una plantilla de correo predefinida en el
     * servicio de notificaciones.</p>
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public enum EmailType {
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
    private String              recipientEmail;

    /** Nombre completo o nombre de pila del destinatario para personalizar el saludo. */
    private String              recipientName;

    /** Tipo de correo que determina la plantilla a usar. */
    private EmailType           emailType;

    /**
     * Parámetros variables que serán interpolados en la plantilla del correo.
     * Las claves y valores dependen del {@link EmailType} seleccionado.
     */
    private Map<String, String> templateParams;

    /**
     * Constructor sin argumentos requerido por Jackson para la deserialización.
     */
    public EmailNotificationEvent() {
        super();
    }

    /**
     * Constructor principal que inicializa todos los campos del evento.
     *
     * @param recipientEmail dirección de correo del destinatario
     * @param recipientName  nombre del destinatario para personalización
     * @param emailType      tipo de correo que selecciona la plantilla a usar
     * @param templateParams mapa de variables a interpolar en la plantilla
     */
    public EmailNotificationEvent(
            String recipientEmail,
            String recipientName,
            EmailType emailType,
            Map<String, String> templateParams) {
        super("EMAIL_NOTIFICATION", "platform");
        this.recipientEmail = recipientEmail;
        this.recipientName  = recipientName;
        this.emailType      = emailType;
        this.templateParams = templateParams;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /**
     * Devuelve la dirección de correo electrónico del destinatario.
     *
     * @return correo del destinatario
     */
    public String getRecipientEmail() {
        return recipientEmail;
    }

    /**
     * Devuelve el nombre del destinatario.
     *
     * @return nombre del destinatario
     */
    public String getRecipientName() {
        return recipientName;
    }

    /**
     * Devuelve el tipo de correo que determina la plantilla a utilizar.
     *
     * @return tipo de correo electrónico
     */
    public EmailType getEmailType() {
        return emailType;
    }

    /**
     * Devuelve el mapa de parámetros a interpolar en la plantilla del correo.
     *
     * @return parámetros de la plantilla como pares clave-valor
     */
    public Map<String, String> getTemplateParams() {
        return templateParams;
    }

    // -------------------------------------------------------------------------
    // Setters (requeridos por Jackson para deserialización)
    // -------------------------------------------------------------------------

    /**
     * Establece la dirección de correo del destinatario.
     *
     * @param recipientEmail correo electrónico del destinatario
     */
    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    /**
     * Establece el nombre del destinatario.
     *
     * @param recipientName nombre del destinatario
     */
    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    /**
     * Establece el tipo de correo electrónico.
     *
     * @param emailType tipo que selecciona la plantilla a usar
     */
    public void setEmailType(EmailType emailType) {
        this.emailType = emailType;
    }

    /**
     * Establece los parámetros de la plantilla del correo.
     *
     * @param templateParams mapa de variables para la plantilla
     */
    public void setTemplateParams(Map<String, String> templateParams) {
        this.templateParams = templateParams;
    }
}
