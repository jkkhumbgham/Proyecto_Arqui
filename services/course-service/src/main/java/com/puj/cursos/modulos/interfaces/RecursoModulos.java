package com.puj.cursos.modulos.interfaces;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.puj.cursos.lecciones.dominio.Leccion;
import com.puj.cursos.modulos.dominio.Modulo;
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
 * Recurso REST para la gestión de módulos y la creación de lecciones dentro de ellos.
 *
 * <p>Expone endpoints bajo la ruta base {@code /api/v1/modules} para consultar,
 * actualizar, borrar lógicamente módulos y crear lecciones en su interior.
 * Las operaciones de escritura validan que el instructor autenticado sea propietario
 * del curso al que pertenece el módulo.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Path("/modules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Módulos")
public class RecursoModulos {

    @PersistenceContext(unitName = "CourseServicePU")
    private EntityManager em;

    @Inject private UsuarioAutenticado usuarioAutenticado;

    /**
     * DTO de entrada para actualizar los datos de un módulo.
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public record SolicitudActualizarModulo(
            @JsonProperty("title")       @NotBlank String  titulo,
            @JsonProperty("description")           String  descripcion,
            @JsonProperty("orderIndex")            Integer ordenIndex) {}

    /**
     * DTO de entrada para crear una lección dentro de un módulo.
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public record SolicitudCrearLeccion(
            @JsonProperty("title")           @NotBlank String  titulo,
            @JsonProperty("content")                   String  contenido,
            @JsonProperty("orderIndex")                Integer ordenIndex,
            @JsonProperty("durationMinutes")           Integer duracionMinutos,
            @JsonProperty("contentType")               String  tipoContenido,
            @JsonProperty("contentUrl")                String  urlContenido,
            @JsonProperty("supplementary")             Boolean esSuplementaria
    ) {}

    /**
     * Obtiene el detalle básico de un módulo por su identificador.
     *
     * @param  id identificador del módulo
     * @return respuesta 200 o 404
     */
    @GET
    @Path("/{id}")
    @Transactional
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN, Rol.STUDENT, Rol.DIRECTOR})
    @Operation(summary = "Obtener módulo por ID")
    public Response buscarPorId(@PathParam("id") UUID id) {
        Modulo m = em.find(Modulo.class, id);
        if (m == null || m.estaEliminado()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"message\":\"Módulo no encontrado\"}")
                    .build();
        }
        return Response.ok(Map.of(
                "id",          m.getId(),
                "title",       m.obtenerTitulo(),
                "description", m.obtenerDescripcion() != null ? m.obtenerDescripcion() : "",
                "orderIndex",  m.obtenerOrdenIndex(),
                "courseId",    m.obtenerCurso().getId()
        )).build();
    }

    /**
     * Actualiza los datos de un módulo existente.
     *
     * @param  id       identificador del módulo a actualizar
     * @param  solicitud campos a actualizar
     * @return respuesta 200, 404 o 403 según el resultado
     */
    @PUT
    @Path("/{id}")
    @Transactional
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN})
    @Operation(summary = "Actualizar módulo")
    public Response actualizar(@PathParam("id") UUID id, SolicitudActualizarModulo solicitud) {
        Modulo m = em.find(Modulo.class, id);
        if (m == null || m.estaEliminado()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"message\":\"Módulo no encontrado\"}")
                    .build();
        }
        UUID idInstructor = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        if (!m.obtenerCurso().obtenerIdInstructor().equals(idInstructor)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"message\":\"No eres el instructor de este curso\"}")
                    .build();
        }
        m.establecerTitulo(solicitud.titulo());
        if (solicitud.descripcion() != null) m.establecerDescripcion(solicitud.descripcion());
        if (solicitud.ordenIndex() != null)  m.establecerOrdenIndex(solicitud.ordenIndex());
        em.merge(m);
        return Response.ok(Map.of("id", m.getId(), "title", m.obtenerTitulo())).build();
    }

    /**
     * Realiza el borrado lógico de un módulo.
     *
     * @param  id identificador del módulo a eliminar
     * @return respuesta 204, 404 o 403 según el resultado
     */
    @DELETE
    @Path("/{id}")
    @Transactional
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN})
    @Operation(summary = "Eliminar módulo (soft delete)")
    public Response eliminar(@PathParam("id") UUID id) {
        Modulo m = em.find(Modulo.class, id);
        if (m == null || m.estaEliminado()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"message\":\"Módulo no encontrado\"}")
                    .build();
        }
        UUID idInstructor = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        if (!m.obtenerCurso().obtenerIdInstructor().equals(idInstructor)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"message\":\"No eres el instructor de este curso\"}")
                    .build();
        }
        m.eliminarLogicamente();
        em.merge(m);
        return Response.noContent().build();
    }

    /**
     * Crea una nueva lección dentro del módulo indicado.
     *
     * @param  idModulo  identificador del módulo padre
     * @param  solicitud datos de la lección a crear
     * @return respuesta 201 con los datos básicos de la lección, 404 o 403 si aplica
     */
    @POST
    @Path("/{id}/lessons")
    @Transactional
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN})
    @Operation(summary = "Crear lección en un módulo")
    public Response crearLeccion(@PathParam("id") UUID idModulo, SolicitudCrearLeccion solicitud) {
        Modulo m = em.find(Modulo.class, idModulo);
        if (m == null || m.estaEliminado()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"message\":\"Módulo no encontrado\"}")
                    .build();
        }
        UUID idInstructor = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        if (!m.obtenerCurso().obtenerIdInstructor().equals(idInstructor)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"message\":\"No eres el instructor de este curso\"}")
                    .build();
        }
        int siguienteOrden = m.obtenerLecciones().stream()
                .filter(l -> !l.estaEliminada())
                .mapToInt(Leccion::obtenerOrdenIndex)
                .max()
                .orElse(0) + 1;

        Leccion leccion = new Leccion();
        leccion.establecerModulo(m);
        leccion.establecerTitulo(solicitud.titulo());
        leccion.establecerContenido(solicitud.contenido());
        leccion.establecerOrdenIndex(solicitud.ordenIndex() != null ? solicitud.ordenIndex() : siguienteOrden);
        leccion.establecerDuracionMinutos(solicitud.duracionMinutos());
        if (solicitud.tipoContenido() != null && !solicitud.tipoContenido().isBlank()) {
            leccion.establecerTipoContenido(solicitud.tipoContenido());
        }
        if (solicitud.urlContenido() != null && !solicitud.urlContenido().isBlank()) {
            leccion.establecerUrlContenido(solicitud.urlContenido());
        }
        if (Boolean.TRUE.equals(solicitud.esSuplementaria())) {
            leccion.establecerEsSuplementaria(true);
        }
        em.persist(leccion);

        return Response.status(Response.Status.CREATED).entity(Map.of(
                "id",         leccion.getId(),
                "title",      leccion.obtenerTitulo(),
                "orderIndex", leccion.obtenerOrdenIndex(),
                "moduleId",   idModulo,
                "courseId",   m.obtenerCurso().getId()
        )).build();
    }
}
