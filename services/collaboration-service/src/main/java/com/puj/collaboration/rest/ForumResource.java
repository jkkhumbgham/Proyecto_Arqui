package com.puj.collaboration.rest;

import com.puj.collaboration.dto.PostRequest;
import com.puj.collaboration.dto.ThreadRequest;
import com.puj.collaboration.entity.*;
import com.puj.collaboration.entity.Thread;
import com.puj.collaboration.repository.ForumRepository;

import com.puj.security.rbac.AuthenticatedUser;
import com.puj.security.rbac.RequiresRole;
import com.puj.security.rbac.Role;
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
 * Recurso REST para la gestión de foros, hilos y posts de la plataforma.
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
public class ForumResource {

    @Inject private ForumRepository   forumRepo;
    @Inject private AuthenticatedUser authenticatedUser;

    /**
     * Lista todos los foros activos de la plataforma.
     *
     * @return respuesta 200 con la lista de mapas de datos de cada foro
     */
    @GET
    @Operation(summary = "Listar todos los foros")
    public Response listForums() {
        List<Map<String, Object>> result = forumRepo.findAll().stream()
                .map(this::toForumMap)
                .collect(Collectors.toList());
        return Response.ok(result).build();
    }

    /**
     * Obtiene el foro asociado a un curso.
     *
     * @param courseId UUID del curso
     * @return respuesta 200 con los datos del foro, o 404 si no existe
     */
    @GET
    @Path("/courses/{courseId}")
    @Operation(summary = "Obtener foro de un curso")
    public Response getForumByCourse(@PathParam("courseId") UUID courseId) {
        return forumRepo.findByCourse(courseId)
                .map(f -> Response.ok(f).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Lista los hilos activos de un foro con el número de posts por hilo.
     *
     * <p>Los hilos fijados aparecen primero, luego por fecha descendente.</p>
     *
     * @param forumId UUID del foro
     * @return respuesta 200 con la lista de mapas de datos de cada hilo
     */
    @GET
    @Path("/{forumId}/threads")
    @Operation(summary = "Listar hilos de un foro")
    public Response listThreads(@PathParam("forumId") UUID forumId) {
        List<Object[]> rows = forumRepo.findThreadsWithPostCount(forumId);
        List<Map<String, Object>> result = rows.stream()
                .map(row -> toThreadMap(row))
                .collect(Collectors.toList());
        return Response.ok(result).build();
    }

    /**
     * Lista los posts activos de un hilo en orden cronológico.
     *
     * @param threadId UUID del hilo
     * @return respuesta 200 con el mapa {@code {locked, posts}},
     *         donde {@code posts} es la lista de datos de cada post
     * @throws NotFoundException si el hilo no existe o está eliminado
     */
    @GET
    @Path("/threads/{threadId}/posts")
    @Operation(summary = "Listar posts de un hilo")
    public Response listPosts(@PathParam("threadId") UUID threadId) {
        Thread thread = forumRepo.findThreadById(threadId)
                .orElseThrow(() -> new NotFoundException("Hilo no encontrado."));
        List<Map<String, Object>> postList = forumRepo.findPostsByThread(threadId)
                .stream()
                .map(this::toPostMap)
                .collect(Collectors.toList());
        Map<String, Object> response = new HashMap<>();
        response.put("locked", thread.isLocked());
        response.put("posts",  postList);
        return Response.ok(response).build();
    }

    /**
     * Crea un nuevo foro para un curso.
     *
     * @param forum datos del foro a crear (title, courseId, description)
     * @return respuesta 201 con el foro creado
     */
    @POST
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Transactional
    @Operation(summary = "Crear foro para un curso (INSTRUCTOR/ADMIN)")
    public Response createForum(Forum forum) {
        forum.setCreatedBy(UUID.fromString(authenticatedUser.getUserId()));
        forumRepo.save(forum);
        return Response.status(Response.Status.CREATED).entity(forum).build();
    }

    /**
     * Crea un hilo de discusión en un foro, incluyendo el primer post.
     *
     * @param forumId UUID del foro donde se crea el hilo
     * @param req     título del hilo y contenido del primer post
     * @return respuesta 201 con el ID y título del hilo creado
     * @throws NotFoundException si el foro no existe o está eliminado
     */
    @POST
    @Path("/{forumId}/threads")
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR, Role.ADMIN})
    @Transactional
    @Operation(summary = "Crear hilo en un foro")
    public Response createThread(
            @PathParam("forumId") UUID forumId,
            @Valid ThreadRequest req) {

        Forum forum = forumRepo.findById(forumId)
                .orElseThrow(() -> new NotFoundException("Foro no encontrado."));

        Thread thread = new Thread();
        thread.setForum(forum);
        thread.setTitle(req.title());
        thread.setAuthorId(UUID.fromString(authenticatedUser.getUserId()));
        forumRepo.saveThread(thread);

        Post firstPost = new Post();
        firstPost.setThread(thread);
        firstPost.setAuthorId(thread.getAuthorId());
        firstPost.setContent(req.content());
        forumRepo.savePost(firstPost);

        return Response.status(Response.Status.CREATED)
                .entity(Map.of(
                        "id",    thread.getId().toString(),
                        "title", thread.getTitle()))
                .build();
    }

    /**
     * Publica una respuesta (post) en un hilo de discusión.
     *
     * @param threadId UUID del hilo donde se publica la respuesta
     * @param req      contenido de la respuesta
     * @return respuesta 201 con el UUID del post creado
     * @throws NotFoundException   si el hilo no existe o está eliminado
     * @throws BadRequestException si el hilo está bloqueado
     */
    @POST
    @Path("/threads/{threadId}/posts")
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR, Role.ADMIN})
    @Transactional
    @Operation(summary = "Responder a un hilo")
    public Response createPost(
            @PathParam("threadId") UUID threadId,
            @Valid PostRequest req) {

        Thread thread = forumRepo.findThreadById(threadId)
                .orElseThrow(() -> new NotFoundException("Hilo no encontrado."));

        if (thread.isLocked()) {
            throw new BadRequestException(
                    "El hilo está cerrado y no acepta respuestas.");
        }

        Post post = new Post();
        post.setThread(thread);
        post.setAuthorId(UUID.fromString(authenticatedUser.getUserId()));
        post.setContent(req.content());
        forumRepo.savePost(post);

        return Response.status(Response.Status.CREATED)
                .entity(Map.of("id", post.getId().toString()))
                .build();
    }

    /**
     * Elimina un hilo de discusión de forma lógica (moderación).
     *
     * @param threadId UUID del hilo a eliminar
     * @return respuesta 204 sin contenido
     */
    @DELETE
    @Path("/threads/{threadId}")
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN, Role.DIRECTOR})
    @Transactional
    @Operation(summary = "Eliminar hilo (moderador)")
    public Response deleteThread(@PathParam("threadId") UUID threadId) {
        forumRepo.findThreadById(threadId).ifPresent(t -> {
            t.softDelete();
            forumRepo.mergeThread(t);
        });
        return Response.noContent().build();
    }

    /**
     * Alterna el estado de bloqueo de un hilo (locked/unlocked).
     *
     * @param threadId UUID del hilo a bloquear o desbloquear
     * @return respuesta 200 con el nuevo estado de bloqueo
     * @throws NotFoundException si el hilo no existe o está eliminado
     */
    @PUT
    @Path("/threads/{threadId}/lock")
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN, Role.DIRECTOR})
    @Transactional
    @Operation(summary = "Bloquear/desbloquear hilo (moderador)")
    public Response lockThread(@PathParam("threadId") UUID threadId) {
        Thread thread = forumRepo.findThreadById(threadId)
                .orElseThrow(() -> new NotFoundException("Hilo no encontrado."));
        thread.setLocked(!thread.isLocked());
        forumRepo.mergeThread(thread);
        return Response.ok(Map.of("locked", thread.isLocked())).build();
    }

    /**
     * Elimina un post de forma lógica (moderación de contenido).
     *
     * @param postId UUID del post a eliminar
     * @return respuesta 204 sin contenido
     */
    @DELETE
    @Path("/posts/{postId}")
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Transactional
    @Operation(summary = "Moderar/eliminar post (INSTRUCTOR/ADMIN)")
    public Response deletePost(@PathParam("postId") UUID postId) {
        forumRepo.findPostById(postId).ifPresent(p -> {
            p.softDelete();
            forumRepo.savePost(p);
        });
        return Response.noContent().build();
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * Proyecta un {@link Forum} al mapa de datos para la respuesta JSON.
     *
     * @param f foro a proyectar
     * @return mapa con los campos {@code id}, {@code title}, {@code courseId}
     *         y opcionalmente {@code description}
     */
    private Map<String, Object> toForumMap(Forum f) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",       f.getId().toString());
        m.put("title",    f.getTitle());
        m.put("courseId",
                f.getCourseId() != null ? f.getCourseId().toString() : "");
        if (f.getDescription() != null) m.put("description", f.getDescription());
        return m;
    }

    /**
     * Proyecta un par {@code [Thread, postCount]} al mapa de datos para la
     * respuesta JSON del listado de hilos.
     *
     * @param row arreglo donde {@code row[0]} es el {@link Thread} y
     *            {@code row[1]} es el conteo de posts como {@link Number}
     * @return mapa con los campos del hilo y el conteo de posts
     */
    private Map<String, Object> toThreadMap(Object[] row) {
        Thread t     = (Thread) row[0];
        long   count = ((Number) row[1]).longValue();
        Map<String, Object> m = new HashMap<>();
        m.put("id",        t.getId().toString());
        m.put("title",     t.getTitle());
        m.put("authorId",  t.getAuthorId().toString());
        m.put("locked",    t.isLocked());
        m.put("pinned",    t.isPinned());
        m.put("createdAt", t.getCreatedAt().toString());
        m.put("postCount", count);
        return m;
    }

    /**
     * Proyecta un {@link Post} al mapa de datos para la respuesta JSON.
     *
     * @param p post a proyectar
     * @return mapa con los campos {@code id}, {@code content}, {@code authorId}
     *         y {@code createdAt}
     */
    private Map<String, Object> toPostMap(Post p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",        p.getId().toString());
        m.put("content",   p.getContent());
        m.put("authorId",  p.getAuthorId().toString());
        m.put("createdAt", p.getCreatedAt().toString());
        return m;
    }
}
