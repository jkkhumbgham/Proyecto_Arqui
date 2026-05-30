package com.puj.email.template;

import com.puj.events.EmailNotificationEvent;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

/**
 * Renderizador de plantillas HTML para las notificaciones por correo electrónico.
 *
 * <p>Cada tipo de correo definido en {@link EmailNotificationEvent.EmailType}
 * tiene una plantilla HTML inline que se popula con los parámetros del evento
 * mediante la sustitución de marcadores {@code {{key}}}. Las plantillas son
 * auto-contenidas (estilos inline) para maximizar la compatibilidad con
 * clientes de correo.</p>
 *
 * <p>La dirección y el nombre del remitente se resuelven por tipo de correo
 * consultando las variables de entorno {@code SMTP_FROM_<TYPE>} y
 * {@code SMTP_FROM_NAME_<TYPE>}. Si no están definidas, se usan los valores
 * globales {@code SMTP_FROM} y {@code SMTP_FROM_NAME}.</p>
 *
 * <p>Plantillas disponibles:</p>
 * <ul>
 *   <li>{@code WELCOME} — bienvenida al nuevo usuario registrado.</li>
 *   <li>{@code ENROLLMENT_CONFIRMED} — confirmación de inscripción a un curso.</li>
 *   <li>{@code ASSESSMENT_GRADED} — resultado de una evaluación calificada,
 *       con sección adaptativa opcional si el evento incluye recomendación.</li>
 *   <li>{@code COURSE_COMPLETED} — felicitación por completar un curso.</li>
 *   <li>{@code PASSWORD_RESET} — enlace de restablecimiento de contraseña.</li>
 * </ul>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class EmailTemplateRenderer {

    /**
     * Resultado inmutable de la renderización de una plantilla de correo.
     *
     * @param subject   asunto del correo ya interpolado.
     * @param htmlBody  cuerpo HTML completo ya interpolado.
     * @param fromEmail dirección del remitente resuelta para este tipo de correo.
     * @param fromName  nombre del remitente resuelto para este tipo de correo.
     */
    public record RenderedEmail(
            String subject,
            String htmlBody,
            String fromEmail,
            String fromName) {}

    private static final String GLOBAL_FROM =
            System.getenv().getOrDefault("SMTP_FROM",      "no-reply@puj.edu.co");
    private static final String GLOBAL_FROM_NAME =
            System.getenv().getOrDefault("SMTP_FROM_NAME", "Plataforma de Aprendizaje PUJ");

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Renderiza el correo correspondiente al tipo de evento recibido.
     *
     * <p>Selecciona la plantilla según {@link EmailNotificationEvent#getEmailType()},
     * interpola los parámetros del evento y resuelve el remitente específico
     * del tipo de correo.</p>
     *
     * @param event evento de notificación con tipo, destinatario y parámetros.
     * @return {@link RenderedEmail} con asunto, HTML y datos del remitente listos.
     * @throws IllegalArgumentException si {@code event.getEmailType()} no tiene
     *         una rama en el switch (no ocurre en la práctica con el enum sellado).
     */
    public RenderedEmail render(EmailNotificationEvent event) {
        RenderedEmail base = switch (event.getEmailType()) {
            case WELCOME              -> renderWelcome(event);
            case ENROLLMENT_CONFIRMED -> renderEnrollmentConfirmed(event);
            case ASSESSMENT_GRADED    -> renderAssessmentGraded(event);
            case COURSE_COMPLETED     -> renderCourseCompleted(event);
            case PASSWORD_RESET       -> renderPasswordReset(event);
        };
        return new RenderedEmail(
                base.subject(),
                base.htmlBody(),
                resolveFrom(event.getEmailType()),
                resolveFromName(event.getEmailType()));
    }

    // ── Resolución de remitente ────────────────────────────────────────────────

    /**
     * Resuelve la dirección del remitente para el tipo de correo indicado.
     *
     * <p>Consulta primero la variable de entorno {@code SMTP_FROM_<TYPE>}
     * (p. ej. {@code SMTP_FROM_WELCOME}). Si no está definida o está vacía,
     * devuelve el valor global {@code SMTP_FROM}.</p>
     *
     * @param type tipo de correo electrónico para el que se resuelve el remitente.
     * @return dirección de correo del remitente a utilizar.
     */
    private String resolveFrom(EmailNotificationEvent.EmailType type) {
        String specific = System.getenv("SMTP_FROM_" + type.name());
        return specific != null && !specific.isBlank() ? specific : GLOBAL_FROM;
    }

    /**
     * Resuelve el nombre del remitente para el tipo de correo indicado.
     *
     * <p>Consulta primero la variable de entorno {@code SMTP_FROM_NAME_<TYPE>}
     * (p. ej. {@code SMTP_FROM_NAME_PASSWORD_RESET}). Si no está definida o
     * está vacía, devuelve el valor global {@code SMTP_FROM_NAME}.</p>
     *
     * @param type tipo de correo electrónico para el que se resuelve el nombre.
     * @return nombre del remitente a utilizar.
     */
    private String resolveFromName(EmailNotificationEvent.EmailType type) {
        String specific = System.getenv("SMTP_FROM_NAME_" + type.name());
        return specific != null && !specific.isBlank() ? specific : GLOBAL_FROM_NAME;
    }

    // ── Plantillas de correo ───────────────────────────────────────────────────

    /**
     * Renderiza el correo de bienvenida al nuevo usuario.
     *
     * <p>Parámetros esperados en {@code templateParams}:</p>
     * <ul>
     *   <li>{@code firstName} — nombre de pila del usuario.</li>
     *   <li>{@code platformUrl} — URL de acceso a la plataforma.</li>
     * </ul>
     *
     * @param e evento de tipo {@code WELCOME}.
     * @return email renderizado con asunto y cuerpo HTML.
     */
    private RenderedEmail renderWelcome(EmailNotificationEvent e) {
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto;">
              <div style="background:#1a3a6b;padding:24px;text-align:center;">
                <h1 style="color:#fff;margin:0;font-size:22px;">
                  Plataforma de Aprendizaje Adaptativo
                </h1>
                <p style="color:#c8d8f0;margin:4px 0 0;">
                  Pontificia Universidad Javeriana · 2026
                </p>
              </div>
              <div style="padding:32px 24px;background:#f9fafb;">
                <h2 style="color:#1a3a6b;">¡Bienvenido/a, {{firstName}}!</h2>
                <p>Tu cuenta ha sido creada exitosamente. Ya puedes explorar el catálogo de cursos
                   y comenzar tu experiencia de aprendizaje adaptativo.</p>
                <div style="text-align:center;margin:28px 0;">
                  <a href="{{platformUrl}}" style="background:#1a3a6b;color:#fff;padding:12px 28px;
                     border-radius:6px;text-decoration:none;font-weight:bold;">
                    Ir a la plataforma
                  </a>
                </div>
                <p style="font-size:13px;color:#666;">
                  Al registrarte aceptaste el tratamiento de tus datos según la
                  <strong>Ley 1581 de 2012</strong>. Puedes solicitar la eliminación de tus datos
                  en cualquier momento escribiendo a privacidad@puj.edu.co.
                </p>
              </div>
              <div style="background:#e8ecf4;padding:16px;text-align:center;font-size:12px;color:#888;">
                Este correo fue enviado automáticamente. Por favor no respondas a este mensaje.
              </div>
            </div>
            """;
        return new RenderedEmail(
                "Bienvenido/a a la Plataforma de Aprendizaje — PUJ",
                interpolate(html, e.getTemplateParams()),
                null, null);
    }

    /**
     * Renderiza el correo de confirmación de inscripción a un curso.
     *
     * <p>Parámetros esperados en {@code templateParams}:</p>
     * <ul>
     *   <li>{@code firstName} — nombre de pila del estudiante.</li>
     *   <li>{@code courseTitle} — título del curso al que se inscribió.</li>
     *   <li>{@code enrolledAt} — fecha y hora de la inscripción formateada.</li>
     * </ul>
     *
     * @param e evento de tipo {@code ENROLLMENT_CONFIRMED}.
     * @return email renderizado con asunto y cuerpo HTML.
     */
    private RenderedEmail renderEnrollmentConfirmed(EmailNotificationEvent e) {
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto;">
              <div style="background:#1a3a6b;padding:24px;text-align:center;">
                <h1 style="color:#fff;margin:0;font-size:20px;">Inscripción confirmada</h1>
              </div>
              <div style="padding:32px 24px;background:#f9fafb;">
                <p>Hola <strong>{{firstName}}</strong>,</p>
                <p>Tu inscripción al curso <strong>{{courseTitle}}</strong> ha sido confirmada.</p>
                <div style="background:#e8f4fd;border-left:4px solid #1a3a6b;
                            padding:14px 18px;border-radius:4px;margin:20px 0;">
                  <p style="margin:0;"><strong>Curso:</strong> {{courseTitle}}</p>
                  <p style="margin:6px 0 0;"><strong>Fecha de inscripción:</strong> {{enrolledAt}}</p>
                </div>
                <p>Accede a la plataforma para comenzar tu aprendizaje.</p>
              </div>
              <div style="background:#e8ecf4;padding:16px;text-align:center;font-size:12px;color:#888;">
                Plataforma PUJ 2026 · Notificación automática
              </div>
            </div>
            """;
        return new RenderedEmail(
                "Inscripción confirmada: "
                        + e.getTemplateParams().getOrDefault("courseTitle", ""),
                interpolate(html, e.getTemplateParams()),
                null, null);
    }

    /**
     * Renderiza el correo de resultado de una evaluación calificada.
     *
     * <p>Parámetros esperados en {@code templateParams}:</p>
     * <ul>
     *   <li>{@code firstName} — nombre de pila del estudiante.</li>
     *   <li>{@code assessmentTitle} — título de la evaluación.</li>
     *   <li>{@code score} — calificación obtenida (0-100).</li>
     *   <li>{@code passed} — {@code "true"} si aprobó, {@code "false"} si no.</li>
     *   <li>{@code recommendation} — (opcional) texto de recomendación adaptativa;
     *       si está presente, se muestra una sección adicional con la sugerencia.</li>
     * </ul>
     *
     * <p>La plantilla enriquece los parámetros con {@code scoreColor} y
     * {@code passLabel} calculados a partir de {@code passed}.</p>
     *
     * @param e evento de tipo {@code ASSESSMENT_GRADED}.
     * @return email renderizado con asunto, cuerpo HTML y sección adaptativa opcional.
     */
    private RenderedEmail renderAssessmentGraded(EmailNotificationEvent e) {
        var params = e.getTemplateParams();
        boolean passed = "true".equalsIgnoreCase(params.getOrDefault("passed", "false"));

        String html = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto;">
              <div style="background:#1a3a6b;padding:24px;text-align:center;">
                <h1 style="color:#fff;margin:0;font-size:20px;">Resultado de evaluación</h1>
              </div>
              <div style="padding:32px 24px;background:#f9fafb;">
                <p>Hola <strong>{{firstName}}</strong>,</p>
                <p>Tu evaluación <strong>{{assessmentTitle}}</strong> ha sido calificada.</p>
                <div style="text-align:center;margin:24px 0;">
                  <div style="font-size:48px;font-weight:700;color:{{scoreColor}};">{{score}}%</div>
                  <div style="font-size:16px;color:{{scoreColor}};font-weight:600;">{{passLabel}}</div>
                </div>
                {{adaptiveSection}}
              </div>
              <div style="background:#e8ecf4;padding:16px;text-align:center;font-size:12px;color:#888;">
                Plataforma PUJ 2026 · Notificación automática
              </div>
            </div>
            """;

        String adaptiveSection = params.containsKey("recommendation")
                ? """
                  <div style="background:#e8f4fd;border-left:4px solid #3498db;
                              padding:14px 18px;border-radius:4px;margin-top:16px;">
                    <strong>Recomendación adaptativa:</strong>
                    <p style="margin:6px 0 0;">{{recommendation}}</p>
                  </div>
                  """
                : "";

        var enriched = new java.util.HashMap<>(params);
        enriched.put("scoreColor",      passed ? "#27ae60" : "#c0392b");
        enriched.put("passLabel",       passed ? "¡Aprobado!" : "No aprobado");
        enriched.put("adaptiveSection", interpolate(adaptiveSection, params));

        return new RenderedEmail(
                "Resultado de tu evaluación: "
                        + params.getOrDefault("assessmentTitle", ""),
                interpolate(html, enriched),
                null, null);
    }

    /**
     * Renderiza el correo de felicitación por completar un curso.
     *
     * <p>Parámetros esperados en {@code templateParams}:</p>
     * <ul>
     *   <li>{@code firstName} — nombre de pila del estudiante.</li>
     *   <li>{@code courseTitle} — título del curso completado.</li>
     * </ul>
     *
     * @param e evento de tipo {@code COURSE_COMPLETED}.
     * @return email renderizado con asunto y cuerpo HTML.
     */
    private RenderedEmail renderCourseCompleted(EmailNotificationEvent e) {
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto;">
              <div style="background:#27ae60;padding:24px;text-align:center;">
                <h1 style="color:#fff;margin:0;font-size:20px;">¡Curso completado!</h1>
              </div>
              <div style="padding:32px 24px;background:#f9fafb;text-align:center;">
                <p style="font-size:16px;">Felicitaciones <strong>{{firstName}}</strong>,</p>
                <p>Completaste el curso <strong>{{courseTitle}}</strong> con un progreso del 100%.</p>
                <div style="font-size:60px;">🎓</div>
                <p style="color:#666;font-size:14px;">
                  Puedes continuar explorando más cursos en la plataforma.
                </p>
              </div>
              <div style="background:#e8ecf4;padding:16px;text-align:center;font-size:12px;color:#888;">
                Plataforma PUJ 2026 · Notificación automática
              </div>
            </div>
            """;
        return new RenderedEmail(
                "¡Completaste el curso: "
                        + e.getTemplateParams().getOrDefault("courseTitle", "") + "!",
                interpolate(html, e.getTemplateParams()),
                null, null);
    }

    /**
     * Renderiza el correo de restablecimiento de contraseña.
     *
     * <p>Parámetros esperados en {@code templateParams}:</p>
     * <ul>
     *   <li>{@code firstName} — nombre de pila del usuario.</li>
     *   <li>{@code resetUrl} — URL del enlace de restablecimiento (expira en 15 min).</li>
     * </ul>
     *
     * @param e evento de tipo {@code PASSWORD_RESET}.
     * @return email renderizado con asunto y cuerpo HTML.
     */
    private RenderedEmail renderPasswordReset(EmailNotificationEvent e) {
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto;">
              <div style="background:#c0392b;padding:24px;text-align:center;">
                <h1 style="color:#fff;margin:0;font-size:20px;">
                  Restablecimiento de contraseña
                </h1>
              </div>
              <div style="padding:32px 24px;background:#f9fafb;">
                <p>Hola <strong>{{firstName}}</strong>,</p>
                <p>Recibimos una solicitud para restablecer la contraseña de tu cuenta.</p>
                <div style="text-align:center;margin:24px 0;">
                  <a href="{{resetUrl}}" style="background:#c0392b;color:#fff;padding:12px 28px;
                     border-radius:6px;text-decoration:none;font-weight:bold;">
                    Restablecer contraseña
                  </a>
                </div>
                <p style="font-size:13px;color:#666;">
                  Este enlace expira en <strong>15 minutos</strong>. Si no solicitaste esto,
                  ignora este correo — tu cuenta sigue siendo segura.
                </p>
              </div>
              <div style="background:#e8ecf4;padding:16px;text-align:center;font-size:12px;color:#888;">
                Plataforma PUJ 2026 · Notificación automática
              </div>
            </div>
            """;
        return new RenderedEmail(
                "Restablecimiento de contraseña — PUJ",
                interpolate(html, e.getTemplateParams()),
                null, null);
    }

    // ── Motor de interpolación ─────────────────────────────────────────────────

    /**
     * Sustituye todos los marcadores {@code {{key}}} en la plantilla por sus valores.
     *
     * <p>Itera sobre cada entrada del mapa de parámetros y reemplaza todas las
     * ocurrencias de {@code {{key}}} en la plantilla por el valor correspondiente.
     * Si {@code params} es {@code null}, devuelve la plantilla sin modificar.</p>
     *
     * @param template cadena de texto con marcadores {@code {{key}}} a sustituir.
     * @param params   mapa de clave-valor con los valores de sustitución;
     *                 puede ser {@code null}.
     * @return plantilla con todos los marcadores sustituidos por sus valores.
     */
    private String interpolate(String template, Map<String, String> params) {
        if (params == null) return template;
        String result = template;
        for (var entry : params.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }
}
