package com.puj.cursos.lecciones.interfaces;

import com.puj.cursos.lecciones.aplicacion.ServicioLecciones;
import com.puj.cursos.lecciones.dominio.Leccion;
import com.puj.seguridad.rbac.RequiereRol;
import com.puj.seguridad.rbac.Rol;
import com.puj.seguridad.rbac.UsuarioAutenticado;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;
import java.util.UUID;

/**
 * Recurso REST para la gestión de lecciones y el registro de progreso de los estudiantes.
 *
 * <p>Expone endpoints bajo la ruta base {@code /api/v1/lessons} para consultar,
 * actualizar y borrar lecciones, así como para marcar una lección como completada.
 * Al completar una lección, se recalcula el progreso de la matrícula y se publica
 * un evento de analítica.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Path("/lessons")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Lecciones")
public class RecursoLecciones {

    @PersistenceContext(unitName = "CourseServicePU")
    private EntityManager em;

    @Inject private UsuarioAutenticado usuarioAutenticado;
    @Inject private ServicioLecciones  servicioLecciones;

    /**
     * DTO de salida que representa el detalle de una lección.
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public record RespuestaLeccion(
            UUID    id,
            String  title,
            String  content,
            int     orderIndex,
            Integer durationMinutes,
            UUID    moduleId,
            UUID    courseId,
            String  contentType,
            String  contentUrl
    ) {}

    /**
     * DTO de entrada para actualizar los datos de una lección.
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public record SolicitudActualizarLeccion(
            @NotBlank String titulo,
            String  contenido,
            Integer ordenIndex,
            Integer duracionMinutos,
            String  tipoContenido,
            String  urlContenido
    ) {}

    /**
     * Obtiene el detalle de una lección por su identificador (acceso público).
     *
     * @param  id identificador de la lección
     * @return respuesta 200 con el DTO de la lección, o 404 si no existe
     */
    @GET
    @Path("/{id}")
    @Transactional
    @Operation(summary = "Obtener lección por ID (público)")
    public Response buscarPorId(@PathParam("id") UUID id) {
        Leccion leccion = em.find(Leccion.class, id);
        if (leccion == null || leccion.estaEliminada()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"message\":\"Lección no encontrada: " + id + "\"}")
                    .build();
        }
        return Response.ok(new RespuestaLeccion(
                leccion.getId(),
                leccion.obtenerTitulo(),
                leccion.obtenerContenido(),
                leccion.obtenerOrdenIndex(),
                leccion.obtenerDuracionMinutos(),
                leccion.obtenerModulo().getId(),
                leccion.obtenerModulo().obtenerCurso().getId(),
                leccion.obtenerTipoContenido(),
                leccion.obtenerUrlContenido()
        )).build();
    }

    /**
     * Actualiza los datos de una lección existente.
     *
     * @param  id       identificador de la lección a actualizar
     * @param  solicitud campos a actualizar
     * @return respuesta 200, 404 o 403 según el resultado
     */
    @PUT
    @Path("/{id}")
    @Transactional
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN})
    @Operation(summary = "Actualizar lección")
    public Response actualizar(@PathParam("id") UUID id, SolicitudActualizarLeccion solicitud) {
        Leccion leccion = em.find(Leccion.class, id);
        if (leccion == null || leccion.estaEliminada()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"message\":\"Lección no encontrada\"}")
                    .build();
        }
        UUID idInstructor = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        if (!leccion.obtenerModulo().obtenerCurso().obtenerIdInstructor().equals(idInstructor)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"message\":\"No eres el instructor de este curso\"}")
                    .build();
        }
        leccion.establecerTitulo(solicitud.titulo());
        if (solicitud.contenido() != null)       leccion.establecerContenido(solicitud.contenido());
        if (solicitud.ordenIndex() != null)      leccion.establecerOrdenIndex(solicitud.ordenIndex());
        if (solicitud.duracionMinutos() != null) leccion.establecerDuracionMinutos(solicitud.duracionMinutos());
        if (solicitud.tipoContenido() != null)   leccion.establecerTipoContenido(solicitud.tipoContenido());
        if (solicitud.urlContenido() != null)    leccion.establecerUrlContenido(solicitud.urlContenido());
        em.merge(leccion);
        return Response.ok(Map.of(
                "id",    leccion.getId(),
                "title", leccion.obtenerTitulo()
        )).build();
    }

    /**
     * Realiza el borrado lógico de una lección.
     *
     * @param  id identificador de la lección a eliminar
     * @return respuesta 204, 404 o 403 según el resultado
     */
    @DELETE
    @Path("/{id}")
    @Transactional
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN})
    @Operation(summary = "Eliminar lección (soft delete)")
    public Response eliminar(@PathParam("id") UUID id) {
        Leccion leccion = em.find(Leccion.class, id);
        if (leccion == null || leccion.estaEliminada()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"message\":\"Lección no encontrada\"}")
                    .build();
        }
        UUID idInstructor = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        if (!leccion.obtenerModulo().obtenerCurso().obtenerIdInstructor().equals(idInstructor)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"message\":\"No eres el instructor de este curso\"}")
                    .build();
        }
        leccion.eliminarLogicamente();
        em.merge(leccion);
        return Response.noContent().build();
    }

    /**
     * Marca una lección como completada por el estudiante autenticado.
     *
     * @param  idLeccion identificador de la lección a marcar como completada
     * @return respuesta 200 con datos de progreso, o 404 si la lección no existe
     */
    @POST
    @Path("/{id}/complete")
    @Transactional
    @RequiereRol({Rol.STUDENT})
    @Operation(summary = "Marcar lección como completada")
    public Response completar(@PathParam("id") UUID idLeccion) {
        Leccion leccion = em.find(Leccion.class, idLeccion);
        if (leccion == null || leccion.estaEliminada()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"message\":\"Lección no encontrada\"}")
                    .build();
        }
        UUID idUsuario = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        Map<String, Object> resultado = servicioLecciones.completar(leccion, idUsuario);

        return Response.ok(Map.of(
                "lessonId",       idLeccion,
                "alreadyDone",    resultado.get("yaCompletada"),
                "completedCount", resultado.get("cantidadCompletadas"),
                "totalLessons",   resultado.get("totalLecciones"),
                "progressPct",    resultado.get("porcentajeProgreso")
        )).build();
    }
}
