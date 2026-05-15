package com.puj.collaboration.rest;

import com.puj.collaboration.dto.PostRequest;
import com.puj.collaboration.dto.ThreadRequest;
import com.puj.collaboration.entity.*;
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

import java.util.UUID;

@Path("/forums")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Foros")
public class ForumResource {

    @Inject private ForumRepository   forumRepo;
    @Inject private AuthenticatedUser authenticatedUser;

    @GET
    @Path("/courses/{courseId}")
    @Operation(summary = "Obtener foro de un curso")
    public Response getForumByCourse(@PathParam("courseId") UUID courseId) {
        return forumRepo.findByCourse(courseId)
                .map(f -> Response.ok(f).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
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

        return Response.status(Response.Status.CREATED).entity(thread).build();
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

        return Response.status(Response.Status.CREATED).entity(post).build();
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
