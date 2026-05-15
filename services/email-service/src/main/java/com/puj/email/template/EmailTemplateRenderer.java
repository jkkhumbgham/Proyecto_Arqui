package com.puj.email.template;

import com.puj.events.EmailNotificationEvent;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

/**
 * Renderiza templates HTML para cada tipo de correo.
 * Los templates usan marcadores {{key}} sustituidos por templateParams del evento.
 */
@ApplicationScoped
public class EmailTemplateRenderer {

    public record RenderedEmail(String subject, String htmlBody) {}

    public RenderedEmail render(EmailNotificationEvent event) {
        return switch (event.getEmailType()) {
            case WELCOME               -> renderWelcome(event);
            case ENROLLMENT_CONFIRMED  -> renderEnrollmentConfirmed(event);
            case ASSESSMENT_GRADED     -> renderAssessmentGraded(event);
            case COURSE_COMPLETED      -> renderCourseCompleted(event);
            case PASSWORD_RESET        -> renderPasswordReset(event);
        };
    }

    // ── Templates ─────────────────────────────────────────────────────────────

    private RenderedEmail renderWelcome(EmailNotificationEvent e) {
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto;">
              <div style="background:#1a3a6b;padding:24px;text-align:center;">
                <h1 style="color:#fff;margin:0;font-size:22px;">
                  Plataforma de Aprendizaje Adaptativo
                </h1>
                <p style="color:#c8d8f0;margin:4px 0 0;">Pontificia Universidad Javeriana · 2026</p>
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
            interpolate(html, e.getTemplateParams()));
    }

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
            "Inscripción confirmada: " + e.getTemplateParams().getOrDefault("courseTitle", ""),
            interpolate(html, e.getTemplateParams()));
    }

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
        enriched.put("scoreColor",  passed ? "#27ae60" : "#c0392b");
        enriched.put("passLabel",   passed ? "¡Aprobado!" : "No aprobado");
        enriched.put("adaptiveSection", interpolate(adaptiveSection, params));

        return new RenderedEmail(
            "Resultado de tu evaluación: " + params.getOrDefault("assessmentTitle", ""),
            interpolate(html, enriched));
    }

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
            "¡Completaste el curso: " + e.getTemplateParams().getOrDefault("courseTitle", "") + "!",
            interpolate(html, e.getTemplateParams()));
    }

    private RenderedEmail renderPasswordReset(EmailNotificationEvent e) {
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto;">
              <div style="background:#c0392b;padding:24px;text-align:center;">
                <h1 style="color:#fff;margin:0;font-size:20px;">Restablecimiento de contraseña</h1>
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
            interpolate(html, e.getTemplateParams()));
    }

    // ── Interpolación de {{key}} ───────────────────────────────────────────────

    private String interpolate(String template, Map<String, String> params) {
        if (params == null) return template;
        String result = template;
        for (var entry : params.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }
}
