package com.puj.courses.rest;

import com.puj.courses.entity.Enrollment;
import com.puj.courses.entity.EnrollmentStatus;
import com.puj.courses.entity.Lesson;
import com.puj.courses.entity.LessonProgress;
import com.puj.events.LessonCompletedEvent;
import com.puj.events.publisher.EventPublisher;
import com.puj.security.rbac.AuthenticatedUser;
import com.puj.security.rbac.RequiresRole;
import com.puj.security.rbac.Role;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/lessons")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Lecciones")
public class LessonResource {

    @PersistenceContext(unitName = "CourseServicePU")
    private EntityManager em;

    @Inject private AuthenticatedUser authenticatedUser;
    @Inject private EventPublisher    eventPublisher;

    public record LessonResponse(
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

    public record LessonUpdateRequest(
            @NotBlank String title,
            String content,
            Integer orderIndex,
            Integer durationMinutes,
            String contentType,
            String contentUrl
    ) {}

    @GET
    @Path("/{id}")
    @Transactional
    @Operation(summary = "Obtener lección por ID (público)")
    public Response findById(@PathParam("id") UUID id) {
        Lesson lesson = em.find(Lesson.class, id);
        if (lesson == null || lesson.isDeleted()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"message\":\"Lección no encontrada: " + id + "\"}")
                    .build();
        }
        return Response.ok(new LessonResponse(
                lesson.getId(),
                lesson.getTitle(),
                lesson.getContent(),
                lesson.getOrderIndex(),
                lesson.getDurationMinutes(),
                lesson.getModule().getId(),
                lesson.getModule().getCourse().getId(),
                lesson.getContentType(),
                lesson.getContentUrl()
        )).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Actualizar lección")
    public Response update(@PathParam("id") UUID id, LessonUpdateRequest req) {
        Lesson lesson = em.find(Lesson.class, id);
        if (lesson == null || lesson.isDeleted()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"message\":\"Lección no encontrada\"}").build();
        }
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        if (!lesson.getModule().getCourse().getInstructorId().equals(instructorId)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"message\":\"No eres el instructor de este curso\"}").build();
        }
        lesson.setTitle(req.title());
        if (req.content() != null)         lesson.setContent(req.content());
        if (req.orderIndex() != null)      lesson.setOrderIndex(req.orderIndex());
        if (req.durationMinutes() != null) lesson.setDurationMinutes(req.durationMinutes());
        if (req.contentType() != null)     lesson.setContentType(req.contentType());
        if (req.contentUrl() != null)      lesson.setContentUrl(req.contentUrl());
        em.merge(lesson);
        return Response.ok(Map.of(
                "id",    lesson.getId(),
                "title", lesson.getTitle()
        )).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Eliminar lección (soft delete)")
    public Response delete(@PathParam("id") UUID id) {
        Lesson lesson = em.find(Lesson.class, id);
        if (lesson == null || lesson.isDeleted()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"message\":\"Lección no encontrada\"}").build();
        }
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        if (!lesson.getModule().getCourse().getInstructorId().equals(instructorId)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"message\":\"No eres el instructor de este curso\"}").build();
        }
        lesson.softDelete();
        em.merge(lesson);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/complete")
    @Transactional
    @RequiresRole({Role.STUDENT})
    @Operation(summary = "Marcar lección como completada")
    public Response complete(@PathParam("id") UUID lessonId) {
        Lesson lesson = em.find(Lesson.class, lessonId);
        if (lesson == null || lesson.isDeleted()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"message\":\"Lección no encontrada\"}").build();
        }
        UUID userId   = UUID.fromString(authenticatedUser.getUserId());
        UUID courseId = lesson.getModule().getCourse().getId();

        // Upsert progress record
        boolean alreadyDone;
        try {
            em.createQuery(
                "SELECT p FROM LessonProgress p WHERE p.userId = :uid AND p.lesson.id = :lid",
                LessonProgress.class)
                .setParameter("uid", userId)
                .setParameter("lid", lessonId)
                .getSingleResult();
            alreadyDone = true;
        } catch (NoResultException e) {
            LessonProgress progress = new LessonProgress();
            progress.setUserId(userId);
            progress.setLesson(lesson);
            progress.setCourseId(courseId);
            em.persist(progress);
            alreadyDone = false;
            eventPublisher.publishAnalytics(
                    new LessonCompletedEvent(userId.toString(), lessonId.toString(), courseId.toString()));
        }

        // Recalculate and update enrollment progress
        long totalLessons = em.createQuery(
            "SELECT COUNT(l) FROM Lesson l WHERE l.module.course.id = :cid" +
            " AND l.deletedAt IS NULL AND l.supplementary = false", Long.class)
            .setParameter("cid", courseId).getSingleResult();

        long completed = em.createQuery(
            "SELECT COUNT(p) FROM LessonProgress p JOIN p.lesson l" +
            " WHERE p.userId = :uid AND p.courseId = :cid AND l.supplementary = false",
            Long.class)
            .setParameter("uid", userId).setParameter("cid", courseId).getSingleResult();

        double pct = totalLessons > 0 ? (completed * 100.0 / totalLessons) : 0.0;

        try {
            Enrollment enrollment = em.createQuery(
                "SELECT e FROM Enrollment e WHERE e.userId = :uid AND e.course.id = :cid" +
                " AND e.deletedAt IS NULL", Enrollment.class)
                .setParameter("uid", userId).setParameter("cid", courseId).getSingleResult();
            enrollment.setProgressPct(Math.min(100.0, pct));
            if (pct >= 100.0) {
                enrollment.setStatus(EnrollmentStatus.COMPLETED);
                enrollment.setCompletedAt(Instant.now());
            }
            em.merge(enrollment);
        } catch (NoResultException ignored) {}

        return Response.ok(Map.of(
                "lessonId",       lessonId,
                "alreadyDone",    alreadyDone,
                "completedCount", completed,
                "totalLessons",   totalLessons,
                "progressPct",    Math.round(pct * 10.0) / 10.0
        )).build();
    }
}
