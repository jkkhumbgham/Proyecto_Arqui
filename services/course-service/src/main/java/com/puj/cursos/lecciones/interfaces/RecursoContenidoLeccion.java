package com.puj.cursos.lecciones.interfaces;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.puj.cursos.lecciones.dominio.ContenidoLeccion;
import com.puj.cursos.lecciones.dominio.Leccion;
import com.puj.seguridad.rbac.RequiereRol;
import com.puj.seguridad.rbac.Rol;
import com.puj.seguridad.rbac.UsuarioAutenticado;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

/**
 * Recurso REST para la gestión de bloques de contenido adicional dentro de una lección.
 *
 * <p>Expone endpoints bajo la ruta {@code /api/v1/lessons/{lessonId}/contents} que permiten
 * listar, crear, actualizar y eliminar bloques de {@link ContenidoLeccion}. Las operaciones de
 * escritura validan que el usuario autenticado sea el instructor propietario del curso.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Path("/lessons/{lessonId}/contents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RecursoContenidoLeccion {

    @PersistenceContext(unitName = "CourseServicePU")
    private EntityManager em;

    @Inject
    private UsuarioAutenticado usuarioAutenticado;

    /**
     * DTO de entrada para crear o actualizar un bloque de contenido.
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public record SolicitudContenido(
            @JsonProperty("title")       String titulo,
            @JsonProperty("description") String descripcion,
            @JsonProperty("contentType") String tipoContenido,
            @JsonProperty("contentUrl")  String urlContenido) {}

    /**
     * DTO de salida que representa un bloque de contenido de lección.
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public record RespuestaContenido(
            UUID   id,
            String title,
            String description,
            String contentType,
            String contentUrl,
            int    orderIndex
    ) {}

    /**
     * Lista todos los bloques de contenido de una lección, ordenados por índice ascendente.
     *
     * @param  idLeccion identificador de la lección
     * @return respuesta 200 con la lista de bloques de contenido
     */
    @GET
    @Transactional
    public Response listar(@PathParam("lessonId") UUID idLeccion) {
        List<ContenidoLeccion> items = em.createQuery(
                "SELECT c FROM ContenidoLeccion c"
                + " WHERE c.leccion.id = :lid ORDER BY c.ordenIndex ASC",
                ContenidoLeccion.class)
                .setParameter("lid", idLeccion)
                .getResultList();

        List<RespuestaContenido> resultado = items.stream()
                .map(c -> new RespuestaContenido(
                        c.getId(),
                        c.obtenerTitulo(),
                        c.obtenerDescripcion(),
                        c.obtenerTipoContenido(),
                        c.obtenerUrlContenido(),
                        c.obtenerOrdenIndex()))
                .toList();

        return Response.ok(resultado).build();
    }

    /**
     * Crea un nuevo bloque de contenido en la lección indicada.
     *
     * @param  idLeccion identificador de la lección
     * @param  solicitud datos del bloque de contenido a crear
     * @return respuesta 201 con el DTO del bloque creado, 404 o 403 si aplica
     */
    @POST
    @Transactional
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN})
    public Response crear(@PathParam("lessonId") UUID idLeccion, SolicitudContenido solicitud) {
        Leccion leccion = em.find(Leccion.class, idLeccion);
        if (leccion == null || leccion.estaEliminada()) {
            return Response.status(404)
                    .entity("{\"message\":\"Lección no encontrada\"}")
                    .build();
        }

        UUID idInstructor = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        if (!leccion.obtenerModulo().obtenerCurso().obtenerIdInstructor().equals(idInstructor)) {
            return Response.status(403)
                    .entity("{\"message\":\"Sin permiso\"}")
                    .build();
        }

        long siguienteOrden = em.createQuery(
                "SELECT COUNT(c) FROM ContenidoLeccion c WHERE c.leccion.id = :lid",
                Long.class)
                .setParameter("lid", idLeccion)
                .getSingleResult();

        ContenidoLeccion contenido = new ContenidoLeccion();
        contenido.establecerLeccion(leccion);
        contenido.establecerTitulo(solicitud.titulo() != null ? solicitud.titulo() : "Sin título");
        contenido.establecerDescripcion(solicitud.descripcion());
        contenido.establecerTipoContenido(solicitud.tipoContenido());
        contenido.establecerUrlContenido(solicitud.urlContenido());
        contenido.establecerOrdenIndex((int) siguienteOrden);
        em.persist(contenido);

        return Response.status(201).entity(new RespuestaContenido(
                contenido.getId(),
                contenido.obtenerTitulo(),
                contenido.obtenerDescripcion(),
                contenido.obtenerTipoContenido(),
                contenido.obtenerUrlContenido(),
                contenido.obtenerOrdenIndex()
        )).build();
    }

    /**
     * Actualiza los datos de un bloque de contenido existente.
     *
     * @param  idLeccion   identificador de la lección
     * @param  idContenido identificador del bloque de contenido
     * @param  solicitud   campos a actualizar
     * @return respuesta 200, 404 o 403 según el resultado
     */
    @PUT
    @Path("/{contentId}")
    @Transactional
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN})
    public Response actualizar(
            @PathParam("lessonId")  UUID idLeccion,
            @PathParam("contentId") UUID idContenido,
            SolicitudContenido solicitud) {
        ContenidoLeccion contenido = em.find(ContenidoLeccion.class, idContenido);
        if (contenido == null || !contenido.obtenerLeccion().getId().equals(idLeccion)) {
            return Response.status(404)
                    .entity("{\"message\":\"Contenido no encontrado\"}")
                    .build();
        }
        UUID idInstructor = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        if (!contenido.obtenerLeccion().obtenerModulo().obtenerCurso()
                .obtenerIdInstructor().equals(idInstructor)) {
            return Response.status(403)
                    .entity("{\"message\":\"Sin permiso\"}")
                    .build();
        }
        if (solicitud.titulo() != null && !solicitud.titulo().isBlank()) contenido.establecerTitulo(solicitud.titulo());
        if (solicitud.descripcion() != null) contenido.establecerDescripcion(solicitud.descripcion());
        if (solicitud.tipoContenido() != null) contenido.establecerTipoContenido(solicitud.tipoContenido());
        contenido.establecerUrlContenido(solicitud.urlContenido());
        return Response.ok(new RespuestaContenido(
                contenido.getId(),
                contenido.obtenerTitulo(),
                contenido.obtenerDescripcion(),
                contenido.obtenerTipoContenido(),
                contenido.obtenerUrlContenido(),
                contenido.obtenerOrdenIndex()
        )).build();
    }

    /**
     * Elimina un bloque de contenido de la lección.
     *
     * @param  idLeccion   identificador de la lección
     * @param  idContenido identificador del bloque de contenido a eliminar
     * @return respuesta 204, 404 o 403 según el resultado
     */
    @DELETE
    @Path("/{contentId}")
    @Transactional
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN})
    public Response eliminar(
            @PathParam("lessonId")  UUID idLeccion,
            @PathParam("contentId") UUID idContenido) {
        ContenidoLeccion contenido = em.find(ContenidoLeccion.class, idContenido);
        if (contenido == null || !contenido.obtenerLeccion().getId().equals(idLeccion)) {
            return Response.status(404)
                    .entity("{\"message\":\"Contenido no encontrado\"}")
                    .build();
        }
        UUID idInstructor = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        if (!contenido.obtenerLeccion().obtenerModulo().obtenerCurso()
                .obtenerIdInstructor().equals(idInstructor)) {
            return Response.status(403)
                    .entity("{\"message\":\"Sin permiso\"}")
                    .build();
        }
        em.remove(contenido);
        return Response.noContent().build();
    }
}
