package com.puj.colaboracion.foros.interfaces;

import com.puj.colaboracion.foros.dominio.*;
import com.puj.colaboracion.foros.interfaces.dto.SolicitudHilo;
import com.puj.colaboracion.foros.interfaces.dto.SolicitudPublicacion;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Recurso REST para la gestión de foros, hilos y publicaciones de la plataforma.
 *
 * <p>Permite a instructores y administradores crear y moderar foros; a cualquier
 * usuario autenticado consultar el contenido; y a estudiantes, instructores y
 * administradores crear hilos y respuestas.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Path("/forums")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Foros")
public class RecursoForos {

    @Inject private RepositorioForos  repoForos;
    @Inject private UsuarioAutenticado usuarioAutenticado;

    /**
     * Lista todos los foros activos de la plataforma.
     *
     * @return respuesta 200 con la lista de mapas de datos de cada foro
     */
    @GET
    @Operation(summary = "Listar todos los foros")
    public Response listarForos() {
        List<Map<String, Object>> resultado = repoForos.buscarTodos().stream()
                .map(this::aMapaForo)
                .collect(Collectors.toList());
        return Response.ok(resultado).build();
    }

    /**
     * Obtiene el foro asociado a un curso.
     *
     * @param idCurso UUID del curso
     * @return respuesta 200 con los datos del foro, o 404 si no existe
     */
    @GET
    @Path("/courses/{courseId}")
    @Operation(summary = "Obtener foro de un curso")
    public Response obtenerForoPorCurso(@PathParam("courseId") UUID idCurso) {
        return repoForos.buscarPorCurso(idCurso)
                .map(f -> Response.ok(aMapaForo(f)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Lista los hilos activos de un foro con el número de publicaciones por hilo.
     *
     * <p>Los hilos fijados aparecen primero, luego por fecha descendente.</p>
     *
     * @param idForo UUID del foro
     * @return respuesta 200 con la lista de mapas de datos de cada hilo
     */
    @GET
    @Path("/{forumId}/threads")
    @Operation(summary = "Listar hilos de un foro")
    public Response listarHilos(@PathParam("forumId") UUID idForo) {
        List<Object[]> filas = repoForos.buscarHilosConConteoPublicaciones(idForo);
        List<Map<String, Object>> resultado = filas.stream()
                .map(this::aMapaHilo)
                .collect(Collectors.toList());
        return Response.ok(resultado).build();
    }

    /**
     * Lista las publicaciones activas de un hilo en orden cronológico.
     *
     * @param idHilo UUID del hilo
     * @return respuesta 200 con el mapa {@code {bloqueado, publicaciones}}
     * @throws NotFoundException si el hilo no existe o está eliminado
     */
    @GET
    @Path("/threads/{threadId}/posts")
    @Operation(summary = "Listar publicaciones de un hilo")
    public Response listarPublicaciones(@PathParam("threadId") UUID idHilo) {
        Hilo hilo = repoForos.buscarHiloPorId(idHilo)
                .orElseThrow(() -> new NotFoundException("Hilo no encontrado."));
        List<Map<String, Object>> listaPublicaciones =
                repoForos.buscarPublicacionesPorHilo(idHilo)
                .stream()
                .map(this::aMapaPublicacion)
                .collect(Collectors.toList());
        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("locked", hilo.estaBloqueado());
        respuesta.put("posts",  listaPublicaciones);
        return Response.ok(respuesta).build();
    }

    /**
     * Crea un nuevo foro para un curso.
     *
     * @param foro datos del foro a crear
     * @return respuesta 201 con el foro creado
     */
    @POST
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN})
    @Transactional
    @Operation(summary = "Crear foro para un curso (INSTRUCTOR/ADMIN)")
    public Response crearForo(Foro foro) {
        foro.setCreadoPor(UUID.fromString(usuarioAutenticado.obtenerIdUsuario()));
        repoForos.guardar(foro);
        return Response.status(Response.Status.CREATED).entity(foro).build();
    }

    /**
     * Crea un hilo de discusión en un foro, incluyendo la primera publicación.
     *
     * @param idForo UUID del foro donde se crea el hilo
     * @param req    título del hilo y contenido de la primera publicación
     * @return respuesta 201 con el ID y título del hilo creado
     * @throws NotFoundException si el foro no existe o está eliminado
     */
    @POST
    @Path("/{forumId}/threads")
    @RequiereRol({Rol.STUDENT, Rol.INSTRUCTOR, Rol.ADMIN})
    @Transactional
    @Operation(summary = "Crear hilo en un foro")
    public Response crearHilo(
            @PathParam("forumId") UUID idForo,
            @Valid SolicitudHilo req) {

        Foro foro = repoForos.buscarPorId(idForo)
                .orElseThrow(() -> new NotFoundException("Foro no encontrado."));

        Hilo hilo = new Hilo();
        hilo.setForo(foro);
        hilo.setTitulo(req.titulo());
        hilo.setIdAutor(UUID.fromString(usuarioAutenticado.obtenerIdUsuario()));
        repoForos.guardarHilo(hilo);

        Publicacion primeraPublicacion = new Publicacion();
        primeraPublicacion.setHilo(hilo);
        primeraPublicacion.setIdAutor(hilo.getIdAutor());
        primeraPublicacion.setContenido(req.contenido());
        repoForos.guardarPublicacion(primeraPublicacion);

        return Response.status(Response.Status.CREATED)
                .entity(Map.of(
                        "id",    hilo.getId().toString(),
                        "title", hilo.getTitulo()))
                .build();
    }

    /**
     * Publica una respuesta (publicación) en un hilo de discusión.
     *
     * @param idHilo UUID del hilo donde se publica la respuesta
     * @param req    contenido de la respuesta
     * @return respuesta 201 con el UUID de la publicación creada
     * @throws NotFoundException   si el hilo no existe o está eliminado
     * @throws BadRequestException si el hilo está bloqueado
     */
    @POST
    @Path("/threads/{threadId}/posts")
    @RequiereRol({Rol.STUDENT, Rol.INSTRUCTOR, Rol.ADMIN})
    @Transactional
    @Operation(summary = "Responder a un hilo")
    public Response crearPublicacion(
            @PathParam("threadId") UUID idHilo,
            @Valid SolicitudPublicacion req) {

        Hilo hilo = repoForos.buscarHiloPorId(idHilo)
                .orElseThrow(() -> new NotFoundException("Hilo no encontrado."));

        if (hilo.estaBloqueado()) {
            throw new BadRequestException(
                    "El hilo está cerrado y no acepta respuestas.");
        }

        Publicacion publicacion = new Publicacion();
        publicacion.setHilo(hilo);
        publicacion.setIdAutor(UUID.fromString(usuarioAutenticado.obtenerIdUsuario()));
        publicacion.setContenido(req.contenido());
        repoForos.guardarPublicacion(publicacion);

        return Response.status(Response.Status.CREATED)
                .entity(Map.of("id", publicacion.getId().toString()))
                .build();
    }

    /**
     * Elimina un hilo de discusión de forma lógica (moderación).
     *
     * @param idHilo UUID del hilo a eliminar
     * @return respuesta 204 sin contenido
     */
    @DELETE
    @Path("/threads/{threadId}")
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN, Rol.DIRECTOR})
    @Transactional
    @Operation(summary = "Eliminar hilo (moderador)")
    public Response eliminarHilo(@PathParam("threadId") UUID idHilo) {
        repoForos.buscarHiloPorId(idHilo).ifPresent(h -> {
            h.eliminarLogicamente();
            repoForos.fusionarHilo(h);
        });
        return Response.noContent().build();
    }

    /**
     * Alterna el estado de bloqueo de un hilo (bloqueado/desbloqueado).
     *
     * @param idHilo UUID del hilo a bloquear o desbloquear
     * @return respuesta 200 con el nuevo estado de bloqueo
     * @throws NotFoundException si el hilo no existe o está eliminado
     */
    @PUT
    @Path("/threads/{threadId}/lock")
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN, Rol.DIRECTOR})
    @Transactional
    @Operation(summary = "Bloquear/desbloquear hilo (moderador)")
    public Response bloquearHilo(@PathParam("threadId") UUID idHilo) {
        Hilo hilo = repoForos.buscarHiloPorId(idHilo)
                .orElseThrow(() -> new NotFoundException("Hilo no encontrado."));
        hilo.setBloqueado(!hilo.estaBloqueado());
        repoForos.fusionarHilo(hilo);
        return Response.ok(Map.of("bloqueado", hilo.estaBloqueado())).build();
    }

    /**
     * Elimina una publicación de forma lógica (moderación de contenido).
     *
     * @param idPublicacion UUID de la publicación a eliminar
     * @return respuesta 204 sin contenido
     */
    @DELETE
    @Path("/posts/{postId}")
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN})
    @Transactional
    @Operation(summary = "Moderar/eliminar publicación (INSTRUCTOR/ADMIN)")
    public Response eliminarPublicacion(@PathParam("postId") UUID idPublicacion) {
        repoForos.buscarPublicacionPorId(idPublicacion).ifPresent(p -> {
            p.eliminarLogicamente();
            repoForos.guardarPublicacion(p);
        });
        return Response.noContent().build();
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private Map<String, Object> aMapaForo(Foro f) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",          f.getId().toString());
        m.put("title",       f.getNombre());
        m.put("courseId",    f.getIdCurso() != null ? f.getIdCurso().toString() : "");
        if (f.getDescripcion() != null) m.put("description", f.getDescripcion());
        return m;
    }

    private Map<String, Object> aMapaHilo(Object[] fila) {
        Hilo h      = (Hilo) fila[0];
        long conteo = ((Number) fila[1]).longValue();
        Map<String, Object> m = new HashMap<>();
        m.put("id",        h.getId().toString());
        m.put("title",     h.getTitulo());
        m.put("authorId",  h.getIdAutor().toString());
        m.put("locked",    h.estaBloqueado());
        m.put("pinned",    h.estaFijado());
        m.put("createdAt", h.getCreadoEn().toString());
        m.put("postCount", conteo);
        return m;
    }

    private Map<String, Object> aMapaPublicacion(Publicacion p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",        p.getId().toString());
        m.put("content",   p.getContenido());
        m.put("authorId",  p.getIdAutor().toString());
        m.put("createdAt", p.getCreadoEn().toString());
        return m;
    }
}
