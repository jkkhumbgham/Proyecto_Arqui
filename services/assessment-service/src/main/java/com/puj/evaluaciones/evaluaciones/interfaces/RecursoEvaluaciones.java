package com.puj.evaluaciones.evaluaciones.interfaces;

import com.puj.evaluaciones.entregas.aplicacion.ServicioEntregas;
import com.puj.evaluaciones.entregas.dominio.RepositorioEntregas;
import com.puj.evaluaciones.entregas.interfaces.dto.ResultadoEntrega;
import com.puj.evaluaciones.entregas.interfaces.dto.SolicitudEntrega;
import com.puj.evaluaciones.evaluaciones.dominio.Evaluacion;
import com.puj.evaluaciones.evaluaciones.dominio.OpcionRespuesta;
import com.puj.evaluaciones.evaluaciones.dominio.Pregunta;
import com.puj.evaluaciones.evaluaciones.dominio.RepositorioEvaluaciones;
import com.puj.evaluaciones.evaluaciones.dominio.TipoPregunta;
import com.puj.evaluaciones.evaluaciones.interfaces.dto.DetalleEvaluacion;
import com.puj.evaluaciones.evaluaciones.interfaces.dto.ResumenEvaluacion;
import com.puj.evaluaciones.evaluaciones.interfaces.dto.SolicitudActualizarEvaluacion;
import com.puj.evaluaciones.evaluaciones.interfaces.dto.SolicitudCrearEvaluacion;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recurso REST para la gestión de evaluaciones.
 *
 * <p>Expone los endpoints CRUD sobre {@link Evaluacion} y delega la lógica de
 * calificación en {@link ServicioEntregas}. Los instructores pueden crear,
 * actualizar y eliminar evaluaciones; los estudiantes pueden consultarlas y
 * enviar sus respuestas.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Path("/assessments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Evaluaciones")
public class RecursoEvaluaciones {

    @Inject private ServicioEntregas       servicioEntregas;
    @Inject private RepositorioEvaluaciones repoEvaluaciones;
    @Inject private RepositorioEntregas    repoEntregas;
    @Inject private UsuarioAutenticado      usuarioAutenticado;

    /**
     * Lista todas las evaluaciones activas con su resumen (sin preguntas).
     *
     * @return respuesta 200 con la lista de {@link ResumenEvaluacion}
     */
    @GET
    @Transactional
    @RequiereRol({Rol.STUDENT, Rol.INSTRUCTOR, Rol.ADMIN, Rol.DIRECTOR})
    @Operation(summary = "Listar todas las evaluaciones activas")
    public Response buscarTodas() {
        List<ResumenEvaluacion> resumenes = repoEvaluaciones.buscarTodas()
                .stream()
                .map(ResumenEvaluacion::from)
                .toList();
        return Response.ok(resumenes).build();
    }

    /**
     * Lista las evaluaciones creadas por el instructor autenticado.
     *
     * @return respuesta 200 con la lista de {@link ResumenEvaluacion} del instructor
     */
    @GET
    @Path("/my")
    @Transactional
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN})
    @Operation(summary = "Mis evaluaciones (INSTRUCTOR)")
    public Response misEvaluaciones() {
        UUID idInstructor = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        List<ResumenEvaluacion> resumenes = repoEvaluaciones.buscarPorInstructor(idInstructor)
                .stream().map(ResumenEvaluacion::from).toList();
        return Response.ok(resumenes).build();
    }

    /**
     * Obtiene el detalle completo de una evaluación por su identificador.
     *
     * @param idEvaluacion UUID de la evaluación a consultar
     * @return respuesta 200 con {@link DetalleEvaluacion}, o 404 si no existe
     */
    @GET
    @Path("/{id}")
    @Transactional
    @RequiereRol({Rol.STUDENT, Rol.INSTRUCTOR, Rol.ADMIN, Rol.DIRECTOR})
    @Operation(summary = "Obtener evaluación por ID (sin revelar respuestas correctas)")
    public Response buscarPorId(@PathParam("id") UUID idEvaluacion) {
        return repoEvaluaciones.buscarPorId(idEvaluacion)
                .map(e -> Response.ok(DetalleEvaluacion.from(e)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"message\":\"Evaluación no encontrada: "
                                + idEvaluacion + "\"}")
                        .build());
    }

    /**
     * Lista las evaluaciones activas de un curso (resumen, sin preguntas).
     *
     * @param idCurso UUID del curso
     * @return respuesta 200 con la lista de {@link ResumenEvaluacion} del curso
     */
    @GET
    @Path("/course/{courseId}")
    @Transactional
    @RequiereRol({Rol.STUDENT, Rol.INSTRUCTOR, Rol.ADMIN, Rol.DIRECTOR})
    @Operation(summary = "Listar evaluaciones de un curso (resumen, sin preguntas)")
    public Response buscarPorCurso(@PathParam("courseId") UUID idCurso) {
        List<ResumenEvaluacion> resumenes = repoEvaluaciones.buscarPorCurso(idCurso)
                .stream()
                .map(ResumenEvaluacion::from)
                .toList();
        return Response.ok(resumenes).build();
    }

    /**
     * Obtiene los resultados de todos los estudiantes en una evaluación.
     *
     * <p>Solo el instructor propietario puede consultar los resultados.</p>
     *
     * @param idEvaluacion UUID de la evaluación
     * @return respuesta 200 con el mapa de resultados, 403 si no es el propietario,
     *         o 404 si la evaluación no existe
     */
    @GET
    @Path("/{id}/submissions")
    @Transactional
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN})
    @Operation(summary = "Resultados de estudiantes para una evaluación (INSTRUCTOR)")
    public Response obtenerEntregas(@PathParam("id") UUID idEvaluacion) {
        Evaluacion evaluacion = repoEvaluaciones.buscarPorId(idEvaluacion)
                .orElseThrow(() -> new NotFoundException("Evaluación no encontrada"));
        UUID idInstructor = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());

        if (!evaluacion.getIdInstructor().equals(idInstructor)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"message\":\"No eres el instructor de esta evaluación\"}")
                    .build();
        }

        List<Map<String, Object>> resultados = construirResultadosEntregas(idEvaluacion);
        return Response.ok(Map.of(
                "assessmentId",    idEvaluacion,
                "assessmentTitle", evaluacion.getTitulo(),
                "submissions",     resultados
        )).build();
    }

    /**
     * Crea una evaluación con sus preguntas y opciones de respuesta.
     *
     * @param req datos de la evaluación a crear, incluyendo preguntas y opciones
     * @return respuesta 201 con el {@link DetalleEvaluacion} de la evaluación creada
     */
    @POST
    @Transactional
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN})
    @Operation(summary = "Crear evaluación con preguntas (INSTRUCTOR)")
    public Response crear(@Valid SolicitudCrearEvaluacion req) {
        UUID idInstructor = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        Evaluacion evaluacion = construirEvaluacionDesdeRequest(req, idInstructor);

        if (req.preguntas() != null) {
            evaluacion.getPreguntas().addAll(
                    construirPreguntas(req.preguntas(), evaluacion));
        }
        repoEvaluaciones.guardar(evaluacion);
        return Response.status(Response.Status.CREATED)
                .entity(DetalleEvaluacion.from(evaluacion)).build();
    }

    /**
     * Actualiza los datos y preguntas de una evaluación existente.
     *
     * <p>Solo el instructor propietario puede actualizar la evaluación.
     * Si se proporciona la lista de preguntas, reemplaza completamente
     * las preguntas existentes.</p>
     *
     * @param idEvaluacion UUID de la evaluación a actualizar
     * @param req          datos parciales a aplicar
     * @return respuesta 200 con el {@link DetalleEvaluacion} actualizado,
     *         403 si no es el propietario, o 404 si la evaluación no existe
     */
    @PUT
    @Path("/{id}")
    @Transactional
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN})
    @Operation(summary = "Actualizar evaluación (INSTRUCTOR)")
    public Response actualizar(
            @PathParam("id") UUID idEvaluacion,
            SolicitudActualizarEvaluacion req) {

        return repoEvaluaciones.buscarPorId(idEvaluacion)
                .map(e -> {
                    UUID idInstructor = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
                    if (!e.getIdInstructor().equals(idInstructor)) {
                        return Response.status(Response.Status.FORBIDDEN)
                                .entity("{\"message\":\"No eres el instructor "
                                        + "de esta evaluación\"}")
                                .build();
                    }
                    aplicarCamposActualizacion(e, req);
                    if (req.preguntas() != null) {
                        e.getPreguntas().clear();
                        e.getPreguntas().addAll(construirPreguntas(req.preguntas(), e));
                    }
                    repoEvaluaciones.guardar(e);
                    return Response.ok(DetalleEvaluacion.from(e)).build();
                })
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"message\":\"Evaluación no encontrada\"}").build());
    }

    /**
     * Elimina una evaluación de forma lógica (soft delete).
     *
     * <p>Solo el instructor propietario puede eliminar la evaluación.</p>
     *
     * @param idEvaluacion UUID de la evaluación a eliminar
     * @return respuesta 204 sin contenido, 403 si no es el propietario,
     *         o 404 si la evaluación no existe
     */
    @DELETE
    @Path("/{id}")
    @Transactional
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN})
    @Operation(summary = "Eliminar evaluación (soft delete)")
    public Response eliminar(@PathParam("id") UUID idEvaluacion) {
        return repoEvaluaciones.buscarPorId(idEvaluacion)
                .map(e -> {
                    UUID idInstructor = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
                    if (!e.getIdInstructor().equals(idInstructor)) {
                        return Response.status(Response.Status.FORBIDDEN)
                                .entity("{\"message\":\"No eres el instructor "
                                        + "de esta evaluación\"}")
                                .build();
                    }
                    e.eliminarLogicamente();
                    repoEvaluaciones.guardar(e);
                    return Response.noContent().build();
                })
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"message\":\"Evaluación no encontrada\"}").build());
    }

    /**
     * Envía y califica las respuestas de un estudiante para una evaluación.
     *
     * @param idEvaluacion UUID de la evaluación
     * @param req          respuestas y duración del intento
     * @return respuesta 200 con el {@link ResultadoEntrega}
     */
    @POST
    @Path("/{id}/submit")
    @RequiereRol(Rol.STUDENT)
    @Operation(summary = "Enviar respuestas de una evaluación")
    public Response enviar(
            @PathParam("id") UUID idEvaluacion,
            @Valid SolicitudEntrega req) {

        UUID idUsuario = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        ResultadoEntrega resultado = servicioEntregas.entregar(idUsuario, idEvaluacion, req);
        return Response.ok(resultado).build();
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * Construye la entidad {@link Evaluacion} a partir del request de creación.
     *
     * @param req          datos del request
     * @param idInstructor UUID del instructor autenticado
     * @return instancia de {@link Evaluacion} lista para persistir
     */
    private Evaluacion construirEvaluacionDesdeRequest(
            SolicitudCrearEvaluacion req,
            UUID idInstructor) {

        Evaluacion e = new Evaluacion();
        e.setTitulo(req.titulo());
        e.setIdCurso(req.idCurso());
        e.setIdLeccion(req.idLeccion());
        e.setDescripcion(req.descripcion());
        e.setIdInstructor(idInstructor);
        if (req.porcentajeAprobacion() != null) e.setPorcentajeAprobacion(req.porcentajeAprobacion());
        if (req.maxIntentos() != null)          e.setMaxIntentos(req.maxIntentos());
        return e;
    }

    /**
     * Construye la lista de entidades {@link Pregunta} con sus opciones a partir
     * de los DTOs del request.
     *
     * @param solicitudesPreguntas lista de DTOs de preguntas
     * @param evaluacion           evaluación propietaria de las preguntas
     * @return lista de {@link Pregunta} con sus {@link OpcionRespuesta}s configuradas
     */
    private List<Pregunta> construirPreguntas(
            List<SolicitudCrearEvaluacion.SolicitudPregunta> solicitudesPreguntas,
            Evaluacion evaluacion) {

        List<Pregunta> preguntas = new ArrayList<>();
        int idx = 0;
        for (SolicitudCrearEvaluacion.SolicitudPregunta sp : solicitudesPreguntas) {
            Pregunta p = new Pregunta();
            p.setEvaluacion(evaluacion);
            p.setTexto(sp.texto());
            p.setTipo(TipoPregunta.valueOf(sp.tipo()));
            p.setPuntos(sp.puntos() > 0 ? sp.puntos() : 1.0);
            p.setOrden(++idx);
            if (sp.opciones() != null) {
                p.getOpciones().addAll(construirOpciones(sp.opciones(), p));
            }
            preguntas.add(p);
        }
        return preguntas;
    }

    /**
     * Construye la lista de entidades {@link OpcionRespuesta} a partir de los DTOs.
     *
     * @param solicitudesOpciones lista de DTOs de opciones
     * @param pregunta            pregunta propietaria de las opciones
     * @return lista de {@link OpcionRespuesta} configuradas
     */
    private List<OpcionRespuesta> construirOpciones(
            List<SolicitudCrearEvaluacion.SolicitudOpcion> solicitudesOpciones,
            Pregunta pregunta) {

        List<OpcionRespuesta> opciones = new ArrayList<>();
        int idx = 0;
        for (SolicitudCrearEvaluacion.SolicitudOpcion so : solicitudesOpciones) {
            OpcionRespuesta op = new OpcionRespuesta();
            op.setPregunta(pregunta);
            op.setTexto(so.texto());
            op.setCorrecta(so.correcta());
            op.setOrden(++idx);
            opciones.add(op);
        }
        return opciones;
    }

    /**
     * Aplica los campos opcionales del request de actualización sobre la entidad.
     *
     * @param e   evaluación a modificar
     * @param req datos de actualización
     */
    private void aplicarCamposActualizacion(Evaluacion e, SolicitudActualizarEvaluacion req) {
        if (req.titulo() != null && !req.titulo().isBlank()) e.setTitulo(req.titulo());
        if (req.descripcion() != null)        e.setDescripcion(req.descripcion());
        e.setIdLeccion(req.idLeccion());
        if (req.porcentajeAprobacion() != null) e.setPorcentajeAprobacion(req.porcentajeAprobacion());
        if (req.maxIntentos() != null)          e.setMaxIntentos(req.maxIntentos());
    }

    /**
     * Construye la lista de mapas de resultados de entregas para la respuesta
     * del endpoint de resultados del instructor.
     *
     * @param idEvaluacion UUID de la evaluación
     * @return lista de mapas con los campos de cada entrega calificada
     */
    private List<Map<String, Object>> construirResultadosEntregas(UUID idEvaluacion) {
        return repoEntregas.buscarPorEvaluacion(idEvaluacion)
                .stream().map(e -> {
                    double pct = e.getPuntuacionMaxima() != null
                            && e.getPuntuacionMaxima().doubleValue() > 0
                            ? (e.getPuntuacion().doubleValue()
                               / e.getPuntuacionMaxima().doubleValue()) * 100.0
                            : 0.0;
                    return Map.<String, Object>of(
                            "submissionId", e.getId(),
                            "userId",       e.getIdUsuario(),
                            "score",        e.getPuntuacion() != null
                                    ? e.getPuntuacion() : BigDecimal.ZERO,
                            "maxScore",     e.getPuntuacionMaxima() != null
                                    ? e.getPuntuacionMaxima() : BigDecimal.ZERO,
                            "scorePct",     Math.round(pct * 10.0) / 10.0,
                            "passed",       e.isAprobado(),
                            "attempt",      e.getNumeroIntento(),
                            "submittedAt",  e.getEntregadaEn() != null
                                    ? e.getEntregadaEn().toString() : ""
                    );
                }).toList();
    }
}
