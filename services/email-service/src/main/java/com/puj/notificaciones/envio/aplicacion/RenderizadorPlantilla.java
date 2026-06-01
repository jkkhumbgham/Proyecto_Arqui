package com.puj.notificaciones.envio.aplicacion;

import com.puj.eventos.EventoNotificacionCorreo;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

/**
 * Renderizador de plantillas HTML para las notificaciones por correo electrónico.
 *
 * <p>Cada tipo de correo definido en {@link EventoNotificacionCorreo.TipoCorreo}
 * tiene una plantilla HTML inline que se popula con los parámetros del evento
 * mediante la sustitución de marcadores {@code {{key}}}. Las plantillas son
 * auto-contenidas (estilos inline) para maximizar la compatibilidad con
 * clientes de correo.</p>
 *
 * <p>La dirección y el nombre del remitente se resuelven por tipo de correo
 * consultando las variables de entorno {@code SMTP_FROM_<TIPO>} y
 * {@code SMTP_FROM_NAME_<TIPO>}. Si no están definidas, se usan los valores
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
public class RenderizadorPlantilla {

    /**
     * Resultado inmutable de la renderización de una plantilla de correo.
     *
     * @param asunto    asunto del correo ya interpolado.
     * @param cuerpoHtml cuerpo HTML completo ya interpolado.
     * @param emailRemitente dirección del remitente resuelta para este tipo de correo.
     * @param nombreRemitente nombre del remitente resuelto para este tipo de correo.
     */
    public record CorreoRenderizado(
            String asunto,
            String cuerpoHtml,
            String emailRemitente,
            String nombreRemitente) {}

    private static final String REMITENTE_GLOBAL =
            System.getenv().getOrDefault("SMTP_FROM",      "no-reply@puj.edu.co");
    private static final String NOMBRE_REMITENTE_GLOBAL =
            System.getenv().getOrDefault("SMTP_FROM_NAME", "Plataforma de Aprendizaje PUJ");

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Renderiza el correo correspondiente al tipo de evento recibido.
     *
     * @param evento evento de notificación con tipo, destinatario y parámetros.
     * @return {@link CorreoRenderizado} con asunto, HTML y datos del remitente listos.
     * @throws IllegalArgumentException si {@code evento.getTipoCorreo()} no tiene
     *         una rama en el switch.
     */
    public CorreoRenderizado renderizar(EventoNotificacionCorreo evento) {
        CorreoRenderizado base = switch (evento.getTipoCorreo()) {
            case WELCOME              -> renderizarBienvenida(evento);
            case ENROLLMENT_CONFIRMED -> renderizarInscripcionConfirmada(evento);
            case ASSESSMENT_GRADED    -> renderizarEvaluacionCalificada(evento);
            case COURSE_COMPLETED     -> renderizarCursoCompletado(evento);
            case PASSWORD_RESET       -> renderizarRestablecimientoContrasena(evento);
        };
        return new CorreoRenderizado(
                base.asunto(),
                base.cuerpoHtml(),
                resolverRemitente(evento.getTipoCorreo()),
                resolverNombreRemitente(evento.getTipoCorreo()));
    }

    // ── Resolución de remitente ───────────────────────────────────────────────

    private String resolverRemitente(EventoNotificacionCorreo.TipoCorreo tipo) {
        String especifico = System.getenv("SMTP_FROM_" + tipo.name());
        return especifico != null && !especifico.isBlank() ? especifico : REMITENTE_GLOBAL;
    }

    private String resolverNombreRemitente(EventoNotificacionCorreo.TipoCorreo tipo) {
        String especifico = System.getenv("SMTP_FROM_NAME_" + tipo.name());
        return especifico != null && !especifico.isBlank()
                ? especifico : NOMBRE_REMITENTE_GLOBAL;
    }

    // ── Plantillas de correo ──────────────────────────────────────────────────

    private CorreoRenderizado renderizarBienvenida(EventoNotificacionCorreo e) {
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
        return new CorreoRenderizado(
                "Bienvenido/a a la Plataforma de Aprendizaje — PUJ",
                interpolar(html, e.getParametrosPlantilla()),
                null, null);
    }

    private CorreoRenderizado renderizarInscripcionConfirmada(
            EventoNotificacionCorreo e) {
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
        return new CorreoRenderizado(
                "Inscripción confirmada: "
                        + e.getParametrosPlantilla().getOrDefault("courseTitle", ""),
                interpolar(html, e.getParametrosPlantilla()),
                null, null);
    }

    private CorreoRenderizado renderizarEvaluacionCalificada(
            EventoNotificacionCorreo e) {
        var parametros = e.getParametrosPlantilla();
        boolean aprobado =
                "true".equalsIgnoreCase(parametros.getOrDefault("passed", "false"));

        String html = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto;">
              <div style="background:#1a3a6b;padding:24px;text-align:center;">
                <h1 style="color:#fff;margin:0;font-size:20px;">Resultado de evaluación</h1>
              </div>
              <div style="padding:32px 24px;background:#f9fafb;">
                <p>Hola <strong>{{firstName}}</strong>,</p>
                <p>Tu evaluación <strong>{{assessmentTitle}}</strong> ha sido calificada.</p>
                <div style="text-align:center;margin:24px 0;">
                  <div style="font-size:48px;font-weight:700;color:{{colorPuntaje}};">{{score}}%</div>
                  <div style="font-size:16px;color:{{colorPuntaje}};font-weight:600;">{{etiquetaAprobacion}}</div>
                </div>
                {{seccionAdaptativa}}
              </div>
              <div style="background:#e8ecf4;padding:16px;text-align:center;font-size:12px;color:#888;">
                Plataforma PUJ 2026 · Notificación automática
              </div>
            </div>
            """;

        String seccionAdaptativa = parametros.containsKey("recommendation")
                ? """
                  <div style="background:#e8f4fd;border-left:4px solid #3498db;
                              padding:14px 18px;border-radius:4px;margin-top:16px;">
                    <strong>Recomendación adaptativa:</strong>
                    <p style="margin:6px 0 0;">{{recommendation}}</p>
                  </div>
                  """
                : "";

        var enriquecido = new java.util.HashMap<>(parametros);
        enriquecido.put("colorPuntaje",       aprobado ? "#27ae60" : "#c0392b");
        enriquecido.put("etiquetaAprobacion", aprobado ? "¡Aprobado!" : "No aprobado");
        enriquecido.put("seccionAdaptativa",  interpolar(seccionAdaptativa, parametros));

        return new CorreoRenderizado(
                "Resultado de tu evaluación: "
                        + parametros.getOrDefault("assessmentTitle", ""),
                interpolar(html, enriquecido),
                null, null);
    }

    private CorreoRenderizado renderizarCursoCompletado(EventoNotificacionCorreo e) {
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
        return new CorreoRenderizado(
                "¡Completaste el curso: "
                        + e.getParametrosPlantilla().getOrDefault("courseTitle", "") + "!",
                interpolar(html, e.getParametrosPlantilla()),
                null, null);
    }

    private CorreoRenderizado renderizarRestablecimientoContrasena(
            EventoNotificacionCorreo e) {
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
        return new CorreoRenderizado(
                "Restablecimiento de contraseña — PUJ",
                interpolar(html, e.getParametrosPlantilla()),
                null, null);
    }

    // ── Motor de interpolación ────────────────────────────────────────────────

    /**
     * Sustituye todos los marcadores {@code {{clave}}} en la plantilla por sus valores.
     *
     * @param plantilla cadena de texto con marcadores a sustituir.
     * @param parametros mapa de clave-valor con los valores de sustitución;
     *                   puede ser {@code null}.
     * @return plantilla con todos los marcadores sustituidos por sus valores.
     */
    private String interpolar(String plantilla, Map<String, String> parametros) {
        if (parametros == null) return plantilla;
        String resultado = plantilla;
        for (var entrada : parametros.entrySet()) {
            resultado = resultado.replace(
                    "{{" + entrada.getKey() + "}}", entrada.getValue());
        }
        return resultado;
    }
}
