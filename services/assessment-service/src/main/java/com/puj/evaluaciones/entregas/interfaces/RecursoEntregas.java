package com.puj.evaluaciones.entregas.interfaces;

import com.puj.evaluaciones.entregas.aplicacion.ServicioEntregas;
import com.puj.evaluaciones.entregas.dominio.Entrega;
import com.puj.evaluaciones.entregas.dominio.EstadoEntrega;
import com.puj.evaluaciones.entregas.dominio.RepositorioEntregas;
import com.puj.evaluaciones.entregas.interfaces.dto.ResultadoEntrega;
import com.puj.evaluaciones.entregas.interfaces.dto.SolicitudEntrega;
import com.puj.evaluaciones.evaluaciones.dominio.Evaluacion;
import com.puj.evaluaciones.evaluaciones.dominio.RepositorioEvaluaciones;
import com.puj.seguridad.rbac.UsuarioAutenticado;
import com.puj.seguridad.rbac.RequiereRol;
import com.puj.seguridad.rbac.Rol;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Recurso REST para la gestión de entregas (intentos de evaluación).
 *
 * <p>Cubre el ciclo completo de un intento: inicio ({@code IN_PROGRESS}),
 * envío de respuestas ({@code GRADED}) e historial. También expone endpoints
 * para métricas de curso utilizados por el módulo de bloqueo de contenido.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Path("/submissions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Entregas")
public class RecursoEntregas {

    @Inject private ServicioEntregas      servicioEntregas;
    @Inject private RepositorioEntregas   repoEntregas;
    @Inject private RepositorioEvaluaciones repoEvaluaciones;
    @Inject private UsuarioAutenticado     usuarioAutenticado;

    /**
     * DTO interno para iniciar una entrega.
     */
    public record SolicitudInicio(String assessmentId) {}

    /**
     * Inicia una nueva entrega en estado {@code IN_PROGRESS}.
     *
     * <p>El frontend debe llamar este endpoint antes de mostrar las preguntas.
     * Verifica que el estudiante no haya alcanzado el límite de intentos.</p>
     *
     * @param req cuerpo con el identificador de la evaluación como {@code String}
     * @return respuesta 201 con el UUID de la entrega creada,
     *         o 400 si se alcanzó el límite de intentos
     * @throws NotFoundException si la evaluación no existe
     */
    @POST
    @RequiereRol(Rol.STUDENT)
    @Transactional
    @Operation(summary = "Iniciar una entrega (ESTUDIANTE)")
    public Response iniciar(SolicitudInicio req) {
        UUID idEvaluacion = UUID.fromString(req.assessmentId());
        UUID idUsuario    = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());

        Evaluacion evaluacion = repoEvaluaciones.buscarPorId(idEvaluacion)
                .orElseThrow(() -> new NotFoundException(
                        "Evaluación no encontrada: " + idEvaluacion));

        long intentos = repoEvaluaciones.contarIntentos(idUsuario, idEvaluacion);
        if (intentos >= evaluacion.getMaxIntentos()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("message",
                            "Has alcanzado el número máximo de intentos ("
                            + evaluacion.getMaxIntentos() + ")."))
                    .build();
        }

        Entrega entrega = new Entrega();
        entrega.setIdUsuario(idUsuario);
        entrega.setEvaluacion(evaluacion);
        entrega.setNumeroIntento((int) intentos + 1);
        entrega.setEstado(EstadoEntrega.IN_PROGRESS);
        repoEntregas.guardar(entrega);

        return Response.status(Response.Status.CREATED)
                .entity(Map.of("id", entrega.getId().toString()))
                .build();
    }

    /**
     * Retorna el historial de evaluaciones completadas del estudiante autenticado.
     *
     * @param pagina número de página (base 0, por defecto 0)
     * @param tamano tamaño de página (por defecto 50)
     * @return respuesta 200 con la lista paginada de entregas completadas
     */
    @GET
    @Path("/my")
    @RequiereRol(Rol.STUDENT)
    @Transactional
    @Operation(summary = "Historial de entregas del estudiante (ESTUDIANTE)")
    public Response misEntregas(
            @QueryParam("page") @DefaultValue("0") int pagina,
            @QueryParam("size") @DefaultValue("50") int tamano) {

        UUID idUsuario = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        List<Entrega> entregas = repoEntregas.buscarCompletadasPorUsuario(
                idUsuario, pagina, tamano);

        List<Map<String, Object>> resultado = entregas.stream()
                .map(e -> construirFilaEntrega(e, calcularPorcentaje(e)))
                .collect(Collectors.toList());

        return Response.ok(resultado).build();
    }

    /**
     * Calcula el promedio del mejor intento por evaluación de un usuario.
     *
     * <p>Utilizado por el servicio de cursos para determinar el bloqueo de módulos.
     * Si {@code userId} no se proporciona, se usa el usuario autenticado.</p>
     *
     * @param idUsuarioStr     UUID del usuario como {@code String}, o {@code null}
     *                         para usar el usuario autenticado
     * @param idsEvaluacionesStr UUIDs de evaluaciones separados por coma
     * @return respuesta 200 con el mapa {@code {avgScorePct: double}}
     */
    @GET
    @Path("/avg-for-assessments")
    @RequiereRol({Rol.STUDENT, Rol.INSTRUCTOR, Rol.ADMIN})
    @Transactional
    @Operation(summary = "Promedio de puntaje para un conjunto de evaluaciones (bloqueo de módulos)")
    public Response promedioPorEvaluaciones(
            @QueryParam("userId")        String idUsuarioStr,
            @QueryParam("assessmentIds") String idsEvaluacionesStr) {

        if (idsEvaluacionesStr == null || idsEvaluacionesStr.isBlank()) {
            return Response.ok(Map.of("avgScorePct", 0.0)).build();
        }

        UUID idUsuario = (idUsuarioStr != null && !idUsuarioStr.isBlank())
                ? UUID.fromString(idUsuarioStr)
                : UUID.fromString(usuarioAutenticado.obtenerIdUsuario());

        List<UUID> idsEvaluaciones = Arrays.stream(idsEvaluacionesStr.split(","))
                .map(String::trim).filter(s -> !s.isBlank())
                .map(UUID::fromString).collect(Collectors.toList());

        List<Entrega> todasEntregas =
                repoEntregas.buscarPorUsuarioYEvaluaciones(idUsuario, idsEvaluaciones);

        Map<UUID, Double> mejorPorEvaluacion = new HashMap<>();
        for (Entrega e : todasEntregas) {
            UUID idEval = e.getEvaluacion().getId();
            mejorPorEvaluacion.merge(idEval, calcularPorcentaje(e), Math::max);
        }

        double promedio = idsEvaluaciones.stream()
                .mapToDouble(id -> mejorPorEvaluacion.getOrDefault(id, 0.0))
                .average().orElse(0.0);

        return Response.ok(Map.of("avgScorePct", redondear1(promedio))).build();
    }

    /**
     * Retorna métricas agregadas de todas las evaluaciones de un curso.
     *
     * <p>Para cada evaluación incluye el número de entregas, el promedio
     * de puntaje y la tasa de aprobación.</p>
     *
     * @param idCurso UUID del curso
     * @return respuesta 200 con la lista de métricas por evaluación
     */
    @GET
    @Path("/course-metrics/{courseId}")
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN})
    @Operation(summary = "Métricas por evaluación de un curso (INSTRUCTOR/ADMIN)")
    public Response metricasCurso(@PathParam("courseId") UUID idCurso) {
        List<Evaluacion> evaluaciones = repoEvaluaciones.buscarPorCurso(idCurso, 0, 1000);

        List<Map<String, Object>> resultado = evaluaciones.stream()
                .map(this::construirFilaMetricaCurso)
                .collect(Collectors.toList());

        return Response.ok(resultado).build();
    }

    /**
     * Envía y califica las respuestas de una entrega existente (en estado
     * {@code IN_PROGRESS}).
     *
     * <p>Verifica que la entrega pertenezca al usuario autenticado antes de
     * delegar la calificación en {@link ServicioEntregas}.</p>
     *
     * @param idEntrega UUID de la entrega a calificar
     * @param req       respuestas y duración del intento
     * @return respuesta 200 con el {@link ResultadoEntrega} de la calificación,
     *         o 403 si la entrega no pertenece al usuario
     * @throws NotFoundException si la entrega no existe
     */
    @POST
    @Path("/{id}/submit")
    @RequiereRol(Rol.STUDENT)
    @Transactional
    @Operation(summary = "Enviar y calificar respuestas (ESTUDIANTE)")
    public Response enviar(
            @PathParam("id") UUID idEntrega,
            @Valid SolicitudEntrega req) {

        UUID idUsuario = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        Entrega entrega = repoEntregas.buscarPorId(idEntrega)
                .orElseThrow(() -> new NotFoundException(
                        "Entrega no encontrada: " + idEntrega));

        if (!entrega.getIdUsuario().equals(idUsuario)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("message",
                            "No tienes permiso para enviar esta entrega."))
                    .build();
        }

        ResultadoEntrega resultado = servicioEntregas.calificarExistente(entrega, req);
        return Response.ok(resultado).build();
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * Calcula el porcentaje de puntaje de una entrega.
     *
     * @param e entrega calificada
     * @return porcentaje (0-100), o 0.0 si el puntaje máximo es nulo o cero
     */
    private double calcularPorcentaje(Entrega e) {
        if (e.getPuntuacion() == null || e.getPuntuacionMaxima() == null
                || e.getPuntuacionMaxima().compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return e.getPuntuacion().doubleValue()
               / e.getPuntuacionMaxima().doubleValue() * 100.0;
    }

    /**
     * Redondea un valor a un decimal.
     *
     * @param v valor a redondear
     * @return valor redondeado a 1 decimal
     */
    private double redondear1(double v) { return Math.round(v * 10.0) / 10.0; }

    /**
     * Construye el mapa de datos de una fila del historial de entregas
     * del estudiante.
     *
     * @param e   entrega a proyectar
     * @param pct porcentaje de puntaje ya calculado
     * @return mapa con los campos de la entrega
     */
    private Map<String, Object> construirFilaEntrega(Entrega e, double pct) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("submissionId",    e.getId().toString());
        m.put("assessmentId",    e.getEvaluacion().getId().toString());
        m.put("assessmentTitle", e.getEvaluacion().getTitulo());
        m.put("courseId",        e.getEvaluacion().getIdCurso().toString());
        m.put("score",           e.getPuntuacion());
        m.put("maxScore",        e.getPuntuacionMaxima());
        m.put("scorePct",        redondear1(pct));
        m.put("passed",          e.isAprobado());
        m.put("attemptNumber",   e.getNumeroIntento());
        m.put("submittedAt",     e.getEntregadaEn() != null
                ? e.getEntregadaEn().toString() : null);
        return m;
    }

    /**
     * Construye el mapa de métricas para una evaluación dentro de un curso.
     *
     * @param ev evaluación a proyectar
     * @return mapa con las métricas de la evaluación
     */
    private Map<String, Object> construirFilaMetricaCurso(Evaluacion ev) {
        List<Entrega> entregas = repoEntregas.buscarPorEvaluacion(ev.getId());
        long aprobados = entregas.stream().filter(Entrega::isAprobado).count();
        double promedio = entregas.isEmpty() ? 0.0
                : entregas.stream().mapToDouble(this::calcularPorcentaje).average().orElse(0.0);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("assessmentId",    ev.getId().toString());
        m.put("assessmentTitle", ev.getTitulo());
        m.put("submissionCount", entregas.size());
        m.put("avgScorePct",     redondear1(promedio));
        m.put("passRate",        entregas.isEmpty() ? 0.0
                : redondear1((double) aprobados / entregas.size() * 100.0));
        return m;
    }
}
