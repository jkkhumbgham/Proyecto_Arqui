package com.puj.notificaciones.envio.aplicacion;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import java.util.Properties;

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
public class ServicioEnvioCorreo {

    private static final String  HOST_SMTP     =
            System.getenv().getOrDefault("SMTP_HOST",     "mailhog");
    private static final int     PUERTO_SMTP   =
            Integer.parseInt(System.getenv().getOrDefault("SMTP_PORT", "1025"));
    private static final String  USUARIO_SMTP  =
            System.getenv().getOrDefault("SMTP_USER",     "");
    private static final String  CLAVE_SMTP    =
            System.getenv().getOrDefault("SMTP_PASSWORD", "");
    private static final boolean SMTP_ACTIVO   =
            Boolean.parseBoolean(System.getenv().getOrDefault("SMTP_ENABLED",  "true"));
    private static final boolean AUTH_SMTP     =
            Boolean.parseBoolean(System.getenv().getOrDefault("SMTP_AUTH",     "false"));
    private static final boolean STARTTLS_SMTP =
            Boolean.parseBoolean(System.getenv().getOrDefault("SMTP_STARTTLS", "false"));

    /** Sesión JavaMail compartida, configurada una sola vez en {@link #inicializar()}. */
    private Session sesionCorreo;

    // ── Ciclo de vida CDI ─────────────────────────────────────────────────────

    /**
     * Inicializa la sesión JavaMail con la configuración de variables de entorno.
     */
    @PostConstruct
    void inicializar() {
        Properties propiedades = construirPropiedadesSmtp();

        if (AUTH_SMTP && !USUARIO_SMTP.isEmpty()) {
            sesionCorreo = Session.getInstance(propiedades, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(USUARIO_SMTP, CLAVE_SMTP);
                }
            });
        } else {
            sesionCorreo = Session.getInstance(propiedades);
        }
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Envía un correo electrónico HTML al destinatario indicado.
     *
     * <p>Si {@code SMTP_ENABLED} es {@code false}, simula el envío y retorna
     * {@code true} sin conectarse al servidor.</p>
     *
     * @param direccionDestino dirección de correo del destinatario.
     * @param nombreDestino    nombre del destinatario.
     * @param asunto           asunto del mensaje.
     * @param cuerpoHtml       cuerpo del mensaje en HTML.
     * @param emailRemitente   dirección del remitente.
     * @param nombreRemitente  nombre del remitente.
     * @return {@code true} si el correo fue enviado (o simulado) exitosamente;
     *         {@code false} si ocurrió algún error.
     */
    public boolean enviar(String direccionDestino, String nombreDestino,
                          String asunto, String cuerpoHtml,
                          String emailRemitente, String nombreRemitente) {
        if (!SMTP_ACTIVO) {
            return true;
        }

        try {
            MimeMessage mensaje = crearMensaje(
                    direccionDestino, nombreDestino, asunto, cuerpoHtml,
                    emailRemitente, nombreRemitente);
            Transport.send(mensaje);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private Properties construirPropiedadesSmtp() {
        Properties propiedades = new Properties();
        propiedades.put("mail.smtp.host",              HOST_SMTP);
        propiedades.put("mail.smtp.port",              PUERTO_SMTP);
        propiedades.put("mail.smtp.auth",              String.valueOf(AUTH_SMTP));
        propiedades.put("mail.smtp.starttls.enable",   String.valueOf(STARTTLS_SMTP));
        propiedades.put("mail.smtp.connectiontimeout", "5000");
        propiedades.put("mail.smtp.timeout",           "10000");
        if (STARTTLS_SMTP) {
            propiedades.put("mail.smtp.ssl.trust", HOST_SMTP);
        }
        return propiedades;
    }

    private MimeMessage crearMensaje(
            String direccionDestino, String nombreDestino,
            String asunto, String cuerpoHtml,
            String emailRemitente, String nombreRemitente) throws Exception {

        MimeMessage mensaje = new MimeMessage(sesionCorreo);
        mensaje.setFrom(new InternetAddress(emailRemitente, nombreRemitente));
        mensaje.setRecipient(Message.RecipientType.TO,
                new InternetAddress(direccionDestino, nombreDestino));
        mensaje.setSubject(asunto, "UTF-8");

        // multipart/alternative: text/plain (fallback) + text/html
        MimeMultipart multiparte = new MimeMultipart("alternative");

        MimeBodyPart parteTexto = new MimeBodyPart();
        parteTexto.setText(eliminarHtml(cuerpoHtml), "UTF-8");

        MimeBodyPart parteHtml = new MimeBodyPart();
        parteHtml.setContent(cuerpoHtml, "text/html; charset=UTF-8");

        multiparte.addBodyPart(parteTexto);
        multiparte.addBodyPart(parteHtml);
        mensaje.setContent(multiparte);
        return mensaje;
    }

    private String eliminarHtml(String html) {
        return html.replaceAll("<[^>]+>", "").replaceAll("\\s{2,}", " ").trim();
    }
}
