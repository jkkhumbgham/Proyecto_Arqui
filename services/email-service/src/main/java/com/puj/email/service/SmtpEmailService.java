package com.puj.email.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio de envío de correos electrónicos mediante el protocolo SMTP.
 *
 * <p>Construye una sesión JavaMail al inicio del ciclo de vida CDI y la
 * reutiliza para todos los envíos. Cada correo se entrega en formato
 * {@code multipart/alternative} con dos partes:</p>
 * <ol>
 *   <li>{@code text/plain} — versión de texto plano generada automáticamente
 *       al eliminar las etiquetas HTML del cuerpo; sirve como fallback para
 *       clientes sin soporte HTML.</li>
 *   <li>{@code text/html} — versión HTML completa.</li>
 * </ol>
 *
 * <p>La configuración se obtiene íntegramente de variables de entorno para
 * facilitar la portabilidad entre entornos (desarrollo con MailHog,
 * producción con SMTP real). La variable {@code SMTP_ENABLED=false} permite
 * deshabilitar el envío real y simular un envío exitoso, útil en pruebas.</p>
 *
 * <p>Variables de entorno reconocidas:</p>
 * <ul>
 *   <li>{@code SMTP_HOST} — host del servidor SMTP (default: {@code mailhog}).</li>
 *   <li>{@code SMTP_PORT} — puerto SMTP (default: {@code 1025}).</li>
 *   <li>{@code SMTP_USER} — usuario para autenticación (default: vacío).</li>
 *   <li>{@code SMTP_PASSWORD} — contraseña para autenticación (default: vacío).</li>
 *   <li>{@code SMTP_ENABLED} — si es {@code false}, simula envíos sin red
 *       (default: {@code true}).</li>
 *   <li>{@code SMTP_AUTH} — habilita autenticación SMTP (default: {@code false}).</li>
 *   <li>{@code SMTP_STARTTLS} — habilita STARTTLS (default: {@code false}).</li>
 * </ul>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class SmtpEmailService {

    private static final Logger LOG = Logger.getLogger(SmtpEmailService.class.getName());

    private static final String  SMTP_HOST     =
            System.getenv().getOrDefault("SMTP_HOST",     "mailhog");
    private static final int     SMTP_PORT     =
            Integer.parseInt(System.getenv().getOrDefault("SMTP_PORT", "1025"));
    private static final String  SMTP_USER     =
            System.getenv().getOrDefault("SMTP_USER",     "");
    private static final String  SMTP_PASS     =
            System.getenv().getOrDefault("SMTP_PASSWORD", "");
    private static final boolean SMTP_ENABLED  =
            Boolean.parseBoolean(System.getenv().getOrDefault("SMTP_ENABLED",  "true"));
    // false por defecto → compatible con MailHog; true en producción con SMTP real
    private static final boolean SMTP_AUTH     =
            Boolean.parseBoolean(System.getenv().getOrDefault("SMTP_AUTH",     "false"));
    private static final boolean SMTP_STARTTLS =
            Boolean.parseBoolean(System.getenv().getOrDefault("SMTP_STARTTLS", "false"));

    /** Sesión JavaMail compartida, configurada una sola vez en {@link #init()}. */
    private Session mailSession;

    // ── Ciclo de vida CDI ─────────────────────────────────────────────────────

    /**
     * Inicializa la sesión JavaMail con la configuración de variables de entorno.
     *
     * <p>Si {@code SMTP_AUTH} es {@code true} y {@code SMTP_USER} no está vacío,
     * la sesión se crea con un {@link Authenticator} que provee las credenciales.
     * En caso contrario, la sesión no usa autenticación (compatible con MailHog).</p>
     *
     * <p>Cuando {@code SMTP_STARTTLS} está habilitado, se agrega
     * {@code mail.smtp.ssl.trust} apuntando al host para evitar errores de
     * certificado en entornos con TLS autofirmado.</p>
     */
    @PostConstruct
    void init() {
        Properties props = buildSmtpProperties();

        if (SMTP_AUTH && !SMTP_USER.isEmpty()) {
            mailSession = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(SMTP_USER, SMTP_PASS);
                }
            });
        } else {
            mailSession = Session.getInstance(props);
        }

        LOG.info("SmtpEmailService inicializado — host=" + SMTP_HOST + ":" + SMTP_PORT
                + " enabled=" + SMTP_ENABLED);
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Envía un correo electrónico HTML al destinatario indicado.
     *
     * <p>El mensaje se construye como {@code multipart/alternative} con una parte
     * {@code text/plain} (generada al eliminar etiquetas HTML del cuerpo) y una
     * parte {@code text/html}. Si {@code SMTP_ENABLED} es {@code false}, el método
     * registra un mensaje informativo y devuelve {@code true} sin conectarse al
     * servidor, lo cual es útil en entornos de prueba.</p>
     *
     * @param toAddress dirección de correo electrónico del destinatario.
     * @param toName    nombre del destinatario para el encabezado {@code To}.
     * @param subject   asunto del mensaje en texto plano.
     * @param htmlBody  cuerpo del mensaje en formato HTML con codificación UTF-8.
     * @param fromEmail dirección del remitente para el encabezado {@code From}.
     * @param fromName  nombre del remitente para el encabezado {@code From}.
     * @return {@code true} si el correo fue enviado (o simulado) exitosamente;
     *         {@code false} si ocurrió algún error durante el envío.
     */
    public boolean send(String toAddress, String toName, String subject, String htmlBody,
                        String fromEmail, String fromName) {
        if (!SMTP_ENABLED) {
            LOG.info("SMTP deshabilitado — simulando envío a " + toAddress + " | " + subject);
            return true;
        }

        try {
            MimeMessage message = buildMessage(toAddress, toName, subject, htmlBody,
                    fromEmail, fromName);
            Transport.send(message);
            LOG.info("Correo enviado a " + toAddress + " | " + subject);
            return true;

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error enviando correo a " + toAddress, e);
            return false;
        }
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * Construye el objeto {@link Properties} con la configuración SMTP.
     *
     * <p>Incluye tiempos de espera de conexión (5 s) y de operación (10 s).
     * Si STARTTLS está habilitado, agrega la confianza explícita al host.</p>
     *
     * @return propiedades listas para instanciar una {@link Session}.
     */
    private Properties buildSmtpProperties() {
        Properties props = new Properties();
        props.put("mail.smtp.host",              SMTP_HOST);
        props.put("mail.smtp.port",              SMTP_PORT);
        props.put("mail.smtp.auth",              String.valueOf(SMTP_AUTH));
        props.put("mail.smtp.starttls.enable",   String.valueOf(SMTP_STARTTLS));
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout",           "10000");
        if (SMTP_STARTTLS) {
            props.put("mail.smtp.ssl.trust", SMTP_HOST);
        }
        return props;
    }

    /**
     * Construye el {@link MimeMessage} listo para enviar.
     *
     * <p>El cuerpo se estructura como {@code multipart/alternative}:
     * primero la parte {@code text/plain} (fallback) y luego la parte
     * {@code text/html}. Los clientes de correo modernos eligen la última
     * parte que pueden renderizar, por lo que el orden es relevante.</p>
     *
     * @param toAddress dirección de correo del destinatario.
     * @param toName    nombre del destinatario.
     * @param subject   asunto del mensaje.
     * @param htmlBody  cuerpo HTML del mensaje.
     * @param fromEmail dirección del remitente.
     * @param fromName  nombre del remitente.
     * @return mensaje MIME construido y listo para ser enviado.
     * @throws Exception si algún campo tiene formato inválido o falla la construcción.
     */
    private MimeMessage buildMessage(String toAddress, String toName, String subject,
                                     String htmlBody, String fromEmail, String fromName)
            throws Exception {
        MimeMessage message = new MimeMessage(mailSession);
        message.setFrom(new InternetAddress(fromEmail, fromName));
        message.setRecipient(Message.RecipientType.TO,
                new InternetAddress(toAddress, toName));
        message.setSubject(subject, "UTF-8");

        // multipart/alternative: text/plain (fallback) + text/html
        MimeMultipart multipart = new MimeMultipart("alternative");

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(stripHtml(htmlBody), "UTF-8");

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(htmlBody, "text/html; charset=UTF-8");

        multipart.addBodyPart(textPart);
        multipart.addBodyPart(htmlPart);
        message.setContent(multipart);
        return message;
    }

    /**
     * Elimina todas las etiquetas HTML de una cadena y colapsa espacios múltiples.
     *
     * <p>Se utiliza para generar la versión texto plano del cuerpo del correo
     * a partir del HTML. El resultado puede contener artefactos de formato
     * menores, pero es suficiente como fallback de lectura.</p>
     *
     * @param html cadena con marcado HTML a limpiar.
     * @return texto sin etiquetas HTML, con espacios colapsados y sin espacios
     *         al inicio o al final.
     */
    private String stripHtml(String html) {
        return html.replaceAll("<[^>]+>", "").replaceAll("\\s{2,}", " ").trim();
    }
}
