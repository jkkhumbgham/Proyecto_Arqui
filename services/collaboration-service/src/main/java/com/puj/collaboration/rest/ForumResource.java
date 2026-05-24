package com.puj.collaboration.rest;

import com.puj.collaboration.dto.PostRequest;
import com.puj.collaboration.dto.ThreadRequest;
import com.puj.collaboration.entity.*;
import com.puj.collaboration.entity.Thread;
import com.puj.collaboration.repository.ForumRepository;
import com.puj.events.ForumPostCreatedEvent;
import com.puj.events.publisher.EventPublisher;
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

@Path("/forums")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Foros")
public class ForumResource {

    @Inject private ForumRepository   forumRepo;
    @Inject private EventPublisher    eventPublisher;
    @Inject private AuthenticatedUser authenticatedUser;

    @GET
    @Operation(summary = "Listar todos los foros")
    public Response listForums() {
        List<Map<String, Object>> result = forumRepo.findAll().stream().map(f -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id",       f.getId().toString());
            m.put("title",    f.getTitle());
            m.put("courseId", f.getCourseId() != null ? f.getCourseId().toString() : "");
            if (f.getDescription() != null) m.put("description", f.getDescription());
            return m;
        }).collect(Collectors.toList());
        return Response.ok(result).build();
    }

    @GET
    @Path("/courses/{courseId}")
    @Operation(summary = "Obtener foro de un curso")
    public Response getForumByCourse(@PathParam("courseId") UUID courseId) {
        return forumRepo.findByCourse(courseId)
                .map(f -> Response.ok(f).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("/{forumId}/threads")
    @Operation(summary = "Listar hilos de un foro")
    public Response listThreads(@PathParam("forumId") UUID forumId) {
        List<Object[]> rows = forumRepo.findThreadsWithPostCount(forumId);
        List<Map<String, Object>> result = rows.stream().map(row -> {
            Thread t = (Thread) row[0];
            long count = ((Number) row[1]).longValue();
            Map<String, Object> m = new HashMap<>();
            m.put("id",        t.getId().toString());
            m.put("title",     t.getTitle());
            m.put("authorId",  t.getAuthorId().toString());
            m.put("locked",    t.isLocked());
            m.put("pinned",    t.isPinned());
            m.put("createdAt", t.getCreatedAt().toString());
            m.put("postCount", count);
            return m;
        }).collect(Collectors.toList());
        return Response.ok(result).build();
    }

    @GET
    @Path("/threads/{threadId}/posts")
    @Operation(summary = "Listar posts de un hilo")
    public Response listPosts(@PathParam("threadId") UUID threadId) {
        Thread thread = forumRepo.findThreadById(threadId)
                .orElseThrow(() -> new NotFoundException("Hilo no encontrado."));
        List<Map<String, Object>> postList = forumRepo.findPostsByThread(threadId).stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id",        p.getId().toString());
            m.put("content",   p.getContent());
            m.put("authorId",  p.getAuthorId().toString());
            m.put("createdAt", p.getCreatedAt().toString());
            return m;
        }).collect(Collectors.toList());
        Map<String, Object> response = new HashMap<>();
        response.put("locked", thread.isLocked());
        response.put("posts",  postList);
        return Response.ok(response).build();
    }

    @POST
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Transactional
    @Operation(summary = "Crear foro para un curso (INSTRUCTOR/ADMIN)")
    public Response createForum(Forum forum) {
        forum.setCreatedBy(UUID.fromString(authenticatedUser.getUserId()));
        forumRepo.save(forum);
        return Response.status(Response.Status.CREATED).entity(forum).build();
    }

    @POST
    @Path("/{forumId}/threads")
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR, Role.ADMIN})
    @Transactional
    @Operation(summary = "Crear hilo en un foro")
    public Response createThread(@PathParam("forumId") UUID forumId,
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

        eventPublisher.publishAnalytics(new ForumPostCreatedEvent(
                thread.getAuthorId().toString(), forumId.toString(),
                forum.getCourseId() != null ? forum.getCourseId().toString() : "", true));

        return Response.status(Response.Status.CREATED)
                .entity(Map.of("id", thread.getId().toString(), "title", thread.getTitle()))
                .build();
    }

    @POST
    @Path("/threads/{threadId}/posts")
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR, Role.ADMIN})
    @Transactional
    @Operation(summary = "Responder a un hilo")
    public Response createPost(@PathParam("threadId") UUID threadId,
                               @Valid PostRequest req) {
        Thread thread = forumRepo.findThreadById(threadId)
                .orElseThrow(() -> new NotFoundException("Hilo no encontrado."));

        if (thread.isLocked()) {
            throw new BadRequestException("El hilo está cerrado y no acepta respuestas.");
        }

        Post post = new Post();
        post.setThread(thread);
        post.setAuthorId(UUID.fromString(authenticatedUser.getUserId()));
        post.setContent(req.content());
        forumRepo.savePost(post);

        eventPublisher.publishAnalytics(new ForumPostCreatedEvent(
                post.getAuthorId().toString(), threadId.toString(),
                thread.getForum().getCourseId() != null ? thread.getForum().getCourseId().toString() : "", false));

        return Response.status(Response.Status.CREATED)
                .entity(Map.of("id", post.getId().toString()))
                .build();
    }

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
}
