package com.puj.courses.rest;

import com.puj.courses.entity.Lesson;
import com.puj.courses.entity.Module;
import com.puj.security.rbac.AuthenticatedUser;
import com.puj.security.rbac.RequiresRole;
import com.puj.security.rbac.Role;
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

@Path("/modules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Módulos")
public class ModuleResource {

    @PersistenceContext(unitName = "CourseServicePU")
    private EntityManager em;

    @Inject private AuthenticatedUser authenticatedUser;

    public record ModuleUpdateRequest(@NotBlank String title, String description, Integer orderIndex) {}
    public record LessonCreateRequest(
            @NotBlank String title,
            String content,
            Integer orderIndex,
            Integer durationMinutes,
            String contentType,
            String contentUrl,
            Boolean supplementary
    ) {}

    @GET
    @Path("/{id}")
    @Transactional
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN, Role.STUDENT, Role.DIRECTOR})
    @Operation(summary = "Obtener módulo por ID")
    public Response findById(@PathParam("id") UUID id) {
        Module m = em.find(Module.class, id);
        if (m == null || m.isDeleted()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"message\":\"Módulo no encontrado\"}").build();
        }
        return Response.ok(Map.of(
                "id",          m.getId(),
                "title",       m.getTitle(),
                "description", m.getDescription() != null ? m.getDescription() : "",
                "orderIndex",  m.getOrderIndex(),
                "courseId",    m.getCourse().getId()
        )).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Actualizar módulo")
    public Response update(@PathParam("id") UUID id, ModuleUpdateRequest req) {
        Module m = em.find(Module.class, id);
        if (m == null || m.isDeleted()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"message\":\"Módulo no encontrado\"}").build();
        }
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        if (!m.getCourse().getInstructorId().equals(instructorId)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"message\":\"No eres el instructor de este curso\"}").build();
        }
        m.setTitle(req.title());
        if (req.description() != null) m.setDescription(req.description());
        if (req.orderIndex() != null)  m.setOrderIndex(req.orderIndex());
        em.merge(m);
        return Response.ok(Map.of("id", m.getId(), "title", m.getTitle())).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Eliminar módulo (soft delete)")
    public Response delete(@PathParam("id") UUID id) {
        Module m = em.find(Module.class, id);
        if (m == null || m.isDeleted()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"message\":\"Módulo no encontrado\"}").build();
        }
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        if (!m.getCourse().getInstructorId().equals(instructorId)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"message\":\"No eres el instructor de este curso\"}").build();
        }
        m.softDelete();
        em.merge(m);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/lessons")
    @Transactional
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Crear lección en un módulo")
    public Response createLesson(@PathParam("id") UUID moduleId, LessonCreateRequest req) {
        Module m = em.find(Module.class, moduleId);
        if (m == null || m.isDeleted()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"message\":\"Módulo no encontrado\"}").build();
        }
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        if (!m.getCourse().getInstructorId().equals(instructorId)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"message\":\"No eres el instructor de este curso\"}").build();
        }
        int nextOrder = m.getLessons().stream()
                .filter(l -> !l.isDeleted())
                .mapToInt(Lesson::getOrderIndex).max().orElse(0) + 1;

        Lesson lesson = new Lesson();
        lesson.setModule(m);
        lesson.setTitle(req.title());
        lesson.setContent(req.content());
        lesson.setOrderIndex(req.orderIndex() != null ? req.orderIndex() : nextOrder);
        lesson.setDurationMinutes(req.durationMinutes());
        if (req.contentType() != null && !req.contentType().isBlank()) lesson.setContentType(req.contentType());
        if (req.contentUrl() != null && !req.contentUrl().isBlank())   lesson.setContentUrl(req.contentUrl());
        if (Boolean.TRUE.equals(req.supplementary()))                  lesson.setSupplementary(true);
        em.persist(lesson);

        return Response.status(Response.Status.CREATED).entity(Map.of(
                "id",              lesson.getId(),
                "title",           lesson.getTitle(),
                "orderIndex",      lesson.getOrderIndex(),
                "moduleId",        moduleId,
                "courseId",        m.getCourse().getId()
        )).build();
    }
}
