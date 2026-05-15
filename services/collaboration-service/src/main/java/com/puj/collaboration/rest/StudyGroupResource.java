package com.puj.collaboration.rest;

import com.puj.collaboration.entity.ChatMessage;
import com.puj.collaboration.entity.StudyGroup;
import com.puj.collaboration.repository.StudyGroupRepository;
import com.puj.collaboration.service.StudyGroupService;
import com.puj.security.auth.AuthenticatedUser;
import com.puj.security.rbac.RequiresRole;
import com.puj.security.rbac.Role;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/v1/groups")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class StudyGroupResource {

    @Inject private StudyGroupService    service;
    @Inject private StudyGroupRepository repo;
    @Inject private AuthenticatedUser    currentUser;

    public record CreateGroupRequest(
            @NotBlank String name,
            UUID courseId,
            int maxMembers
    ) {}

    @GET
    @Path("/courses/{courseId}")
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR, Role.ADMIN})
    public Response listByCourse(@PathParam("courseId") UUID courseId) {
        List<StudyGroup> groups = service.findByCourse(courseId);
        return Response.ok(groups.stream().map(g -> Map.of(
                "id",         g.getId(),
                "name",       g.getName(),
                "courseId",   g.getCourseId(),
                "maxMembers", g.getMaxMembers(),
                "memberCount", g.getMembers().stream().filter(m -> !m.isDeleted()).count()
        )).toList()).build();
    }

    @POST
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR})
    public Response create(CreateGroupRequest req) {
        StudyGroup g = service.create(req.name(), req.courseId(),
                req.maxMembers() > 0 ? req.maxMembers() : 10);
        return Response.status(Response.Status.CREATED)
                .entity(Map.of("id", g.getId(), "name", g.getName())).build();
    }

    @POST
    @Path("/{id}/join")
    @RequiresRole({Role.STUDENT})
    public Response join(@PathParam("id") UUID groupId) {
        service.join(groupId);
        return Response.ok(Map.of("message", "Te has unido al grupo.")).build();
    }

    @GET
    @Path("/{id}/messages")
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR, Role.ADMIN})
    public Response getMessages(@PathParam("id") UUID groupId,
                                @QueryParam("limit") @DefaultValue("50") int limit) {
        List<ChatMessage> messages = repo.findRecentMessages(groupId, Math.min(limit, 100));
        return Response.ok(messages.stream().map(m -> Map.of(
                "id",          m.getId(),
                "authorId",    m.getAuthorId(),
                "authorEmail", m.getAuthorEmail(),
                "content",     m.getContent(),
                "sentAt",      m.getSentAt().toString()
        )).toList()).build();
    }
}
