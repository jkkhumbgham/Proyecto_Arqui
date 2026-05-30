package com.puj.courses.rest;

import com.puj.courses.entity.Enrollment;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recurso REST para la gestión de lecciones y el registro de progreso de los estudiantes.
 *
 * <p>Expone endpoints bajo la ruta base {@code /api/v1/lessons} para consultar,
 * actualizar y borrar lecciones, así como para marcar una lección como completada.
 * Al completar una lección, se recalcula el progreso de la inscripción y se publica
 * un evento de analítica mediante {@link EventPublisher}.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Path("/lessons")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Lecciones")
public class LessonResource {

    @PersistenceContext(unitName = "CourseServicePU")
    private EntityManager em;

    @Inject private AuthenticatedUser authenticatedUser;
    @Inject private EventPublisher    eventPublisher;

    /**
     * DTO de salida que representa el detalle de una lección.
     *
     * @param id              identificador de la lección
     * @param title           título de la lección
     * @param content         contenido textual, puede ser {@code null}
     * @param orderIndex      posición dentro del módulo
     * @param durationMinutes duración estimada en minutos, puede ser {@code null}
     * @param moduleId        identificador del módulo padre
     * @param courseId        identificador del curso
     * @param contentType     tipo de contenido (TEXT, VIDEO, PDF, DOCUMENT)
     * @param contentUrl      URL del recurso externo, puede ser {@code null}
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
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

    /**
     * DTO de entrada para actualizar los datos de una lección.
     *
     * @param title           nuevo título; no debe ser vacío
     * @param content         nuevo contenido textual; puede ser {@code null}
     * @param orderIndex      nueva posición dentro del módulo; puede ser {@code null}
     * @param durationMinutes nueva duración en minutos; puede ser {@code null}
     * @param contentType     nuevo tipo de contenido; puede ser {@code null}
     * @param contentUrl      nueva URL del recurso; puede ser {@code null}
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public record LessonUpdateRequest(
            @NotBlank String title,
            String  content,
            Integer orderIndex,
            Integer durationMinutes,
            String  contentType,
            String  contentUrl
    ) {}

    /**
     * Obtiene el detalle de una lección por su identificador (acceso público).
     *
     * @param  id identificador de la lección
     * @return respuesta 200 con el DTO de la lección,
     *         o 404 si la lección no existe o fue borrada lógicamente
     */
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

    /**
     * Actualiza los datos de una lección existente.
     *
     * <p>Los campos nulos del request se ignoran. El instructor autenticado
     * debe ser propietario del curso de la lección.
     *
     * @param  id  identificador de la lección a actualizar
     * @param  req campos a actualizar
     * @return respuesta 200 con el identificador y título actualizados,
     *         404 si la lección no existe, 403 si no es el instructor propietario
     */
    @PUT
    @Path("/{id}")
    @Transactional
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Actualizar lección")
    public Response update(@PathParam("id") UUID id, LessonUpdateRequest req) {
        Lesson lesson = em.find(Lesson.class, id);
        if (lesson == null || lesson.isDeleted()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"message\":\"Lección no encontrada\"}")
                    .build();
        }
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        if (!lesson.getModule().getCourse().getInstructorId().equals(instructorId)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"message\":\"No eres el instructor de este curso\"}")
                    .build();
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

    /**
     * Realiza el borrado lógico de una lección.
     *
     * <p>El instructor autenticado debe ser propietario del curso de la lección.
     *
     * @param  id identificador de la lección a eliminar
     * @return respuesta 204 sin cuerpo,
     *         404 si la lección no existe, 403 si no es el instructor propietario
     */
    @DELETE
    @Path("/{id}")
    @Transactional
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Eliminar lección (soft delete)")
    public Response delete(@PathParam("id") UUID id) {
        Lesson lesson = em.find(Lesson.class, id);
        if (lesson == null || lesson.isDeleted()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"message\":\"Lección no encontrada\"}")
                    .build();
        }
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        if (!lesson.getModule().getCourse().getInstructorId().equals(instructorId)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"message\":\"No eres el instructor de este curso\"}")
                    .build();
        }
        lesson.softDelete();
        em.merge(lesson);
        return Response.noContent().build();
    }

    /**
     * Marca una lección como completada por el estudiante autenticado.
     *
     * <p>Si la lección ya fue completada ({@code alreadyDone = true}), la operación
     * es idempotente y no crea un registro duplicado. Si es la primera vez, persiste
     * un {@link LessonProgress}, publica un {@link LessonCompletedEvent} y recalcula
     * el porcentaje de progreso de la inscripción.
     *
     * <p>La inscripción no se marca automáticamente como {@code COMPLETED}; el
     * estudiante debe llamar a {@code POST /enrollments/courses/{id}/finalize}
     * una vez aprobadas todas las evaluaciones.
     *
     * @param  lessonId identificador de la lección a marcar como completada
     * @return respuesta 200 con {@code lessonId}, {@code alreadyDone},
     *         {@code completedCount}, {@code totalLessons} y {@code progressPct},
     *         o 404 si la lección no existe o fue borrada lógicamente
     */
    @POST
    @Path("/{id}/complete")
    @Transactional
    @RequiresRole({Role.STUDENT})
    @Operation(summary = "Marcar lección como completada")
    public Response complete(@PathParam("id") UUID lessonId) {
        Lesson lesson = em.find(Lesson.class, lessonId);
        if (lesson == null || lesson.isDeleted()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"message\":\"Lección no encontrada\"}")
                    .build();
        }
        UUID userId   = UUID.fromString(authenticatedUser.getUserId());
        UUID courseId = lesson.getModule().getCourse().getId();

        // Upsert del registro de progreso
        boolean alreadyDone;
        try {
            em.createQuery(
                    "SELECT p FROM LessonProgress p"
                    + " WHERE p.userId = :uid AND p.lesson.id = :lid",
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
            eventPublisher.publishAnalytics(new LessonCompletedEvent(
                    userId.toString(), lessonId.toString(), courseId.toString()));
        }

        // Recalcular y actualizar el progreso de la inscripción
        long totalLessons = em.createQuery(
                "SELECT COUNT(l) FROM Lesson l"
                + " WHERE l.module.course.id = :cid"
                + "   AND l.deletedAt IS NULL AND l.supplementary = false",
                Long.class)
                .setParameter("cid", courseId)
                .getSingleResult();

        long completed = em.createQuery(
                "SELECT COUNT(p) FROM LessonProgress p JOIN p.lesson l"
                + " WHERE p.userId = :uid AND p.courseId = :cid"
                + "   AND l.supplementary = false",
                Long.class)
                .setParameter("uid", userId)
                .setParameter("cid", courseId)
                .getSingleResult();

        double pct = totalLessons > 0 ? (completed * 100.0 / totalLessons) : 0.0;

        try {
            Enrollment enrollment = em.createQuery(
                    "SELECT e FROM Enrollment e"
                    + " WHERE e.userId = :uid AND e.course.id = :cid"
                    + "   AND e.deletedAt IS NULL",
                    Enrollment.class)
                    .setParameter("uid", userId)
                    .setParameter("cid", courseId)
                    .getSingleResult();
            enrollment.setProgressPct(Math.min(100.0, pct));
            // COMPLETED no se asigna automáticamente:
            // requiere aprobación de evaluaciones (>=60%) y llamada a
            // POST /enrollments/courses/{id}/finalize
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
