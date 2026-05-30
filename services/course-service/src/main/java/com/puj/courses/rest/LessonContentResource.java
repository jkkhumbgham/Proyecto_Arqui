package com.puj.courses.rest;

import com.puj.courses.entity.Lesson;
import com.puj.courses.entity.LessonContent;
import com.puj.security.rbac.AuthenticatedUser;
import com.puj.security.rbac.RequiresRole;
import com.puj.security.rbac.Role;
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
 * listar, crear, actualizar y eliminar bloques de {@link LessonContent}. Las operaciones de
 * escritura validan que el usuario autenticado sea el instructor propietario del curso.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Path("/lessons/{lessonId}/contents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LessonContentResource {

    @PersistenceContext(unitName = "CourseServicePU")
    private EntityManager em;

    @Inject
    private AuthenticatedUser authenticatedUser;

    /**
     * DTO de entrada para crear o actualizar un bloque de contenido.
     *
     * @param title       título del bloque; puede ser {@code null} en actualización
     * @param description texto descriptivo del bloque; puede ser {@code null}
     * @param contentType tipo del bloque (TEXT, VIDEO, PDF, DOCUMENT)
     * @param contentUrl  URL del recurso asociado; puede ser {@code null}
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public record ContentRequest(
            String title,
            String description,
            String contentType,
            String contentUrl) {}

    /**
     * DTO de salida que representa un bloque de contenido de lección.
     *
     * @param id          identificador del bloque
     * @param title       título del bloque
     * @param description texto descriptivo del bloque, puede ser {@code null}
     * @param contentType tipo del bloque (TEXT, VIDEO, PDF, DOCUMENT)
     * @param contentUrl  URL del recurso asociado, puede ser {@code null}
     * @param orderIndex  posición del bloque dentro de la lección
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public record ContentResponse(
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
     * @param  lessonId identificador de la lección
     * @return respuesta 200 con la lista de bloques de contenido
     */
    @GET
    @Transactional
    public Response list(@PathParam("lessonId") UUID lessonId) {
        List<LessonContent> items = em.createQuery(
                "SELECT c FROM LessonContent c"
                + " WHERE c.lesson.id = :lid ORDER BY c.orderIndex ASC",
                LessonContent.class)
                .setParameter("lid", lessonId)
                .getResultList();

        List<ContentResponse> result = items.stream()
                .map(c -> new ContentResponse(
                        c.getId(),
                        c.getTitle(),
                        c.getDescription(),
                        c.getContentType(),
                        c.getContentUrl(),
                        c.getOrderIndex()))
                .toList();

        return Response.ok(result).build();
    }

    /**
     * Crea un nuevo bloque de contenido en la lección indicada.
     *
     * <p>El índice de orden se asigna automáticamente como el total actual de bloques.
     * El instructor autenticado debe ser propietario del curso de la lección.
     *
     * @param  lessonId identificador de la lección
     * @param  req      datos del bloque de contenido a crear
     * @return respuesta 201 con el DTO del bloque creado,
     *         404 si la lección no existe, 403 si no es el instructor propietario
     */
    @POST
    @Transactional
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    public Response create(@PathParam("lessonId") UUID lessonId, ContentRequest req) {
        Lesson lesson = em.find(Lesson.class, lessonId);
        if (lesson == null || lesson.isDeleted()) {
            return Response.status(404)
                    .entity("{\"message\":\"Lección no encontrada\"}")
                    .build();
        }

        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        if (!lesson.getModule().getCourse().getInstructorId().equals(instructorId)) {
            return Response.status(403)
                    .entity("{\"message\":\"Sin permiso\"}")
                    .build();
        }

        long nextOrder = em.createQuery(
                "SELECT COUNT(c) FROM LessonContent c WHERE c.lesson.id = :lid",
                Long.class)
                .setParameter("lid", lessonId)
                .getSingleResult();

        LessonContent content = new LessonContent();
        content.setLesson(lesson);
        content.setTitle(req.title() != null ? req.title() : "Sin título");
        content.setDescription(req.description());
        content.setContentType(req.contentType());
        content.setContentUrl(req.contentUrl());
        content.setOrderIndex((int) nextOrder);
        em.persist(content);

        return Response.status(201).entity(new ContentResponse(
                content.getId(),
                content.getTitle(),
                content.getDescription(),
                content.getContentType(),
                content.getContentUrl(),
                content.getOrderIndex()
        )).build();
    }

    /**
     * Actualiza los datos de un bloque de contenido existente.
     *
     * <p>Los campos nulos o vacíos se ignoran. El instructor autenticado debe ser
     * propietario del curso de la lección.
     *
     * @param  lessonId  identificador de la lección
     * @param  contentId identificador del bloque de contenido
     * @param  req       campos a actualizar
     * @return respuesta 200 con el DTO actualizado,
     *         404 si el bloque no existe, 403 si no es el instructor propietario
     */
    @PUT
    @Path("/{contentId}")
    @Transactional
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    public Response update(
            @PathParam("lessonId")  UUID lessonId,
            @PathParam("contentId") UUID contentId,
            ContentRequest req) {
        LessonContent content = em.find(LessonContent.class, contentId);
        if (content == null || !content.getLesson().getId().equals(lessonId)) {
            return Response.status(404)
                    .entity("{\"message\":\"Contenido no encontrado\"}")
                    .build();
        }
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        if (!content.getLesson().getModule().getCourse()
                .getInstructorId().equals(instructorId)) {
            return Response.status(403)
                    .entity("{\"message\":\"Sin permiso\"}")
                    .build();
        }
        if (req.title() != null && !req.title().isBlank()) content.setTitle(req.title());
        if (req.description() != null) content.setDescription(req.description());
        if (req.contentType() != null) content.setContentType(req.contentType());
        content.setContentUrl(req.contentUrl());
        return Response.ok(new ContentResponse(
                content.getId(),
                content.getTitle(),
                content.getDescription(),
                content.getContentType(),
                content.getContentUrl(),
                content.getOrderIndex()
        )).build();
    }

    /**
     * Elimina un bloque de contenido de la lección.
     *
     * <p>El instructor autenticado debe ser propietario del curso de la lección.
     *
     * @param  lessonId  identificador de la lección
     * @param  contentId identificador del bloque de contenido a eliminar
     * @return respuesta 204 sin cuerpo,
     *         404 si el bloque no existe, 403 si no es el instructor propietario
     */
    @DELETE
    @Path("/{contentId}")
    @Transactional
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    public Response delete(
            @PathParam("lessonId")  UUID lessonId,
            @PathParam("contentId") UUID contentId) {
        LessonContent content = em.find(LessonContent.class, contentId);
        if (content == null || !content.getLesson().getId().equals(lessonId)) {
            return Response.status(404)
                    .entity("{\"message\":\"Contenido no encontrado\"}")
                    .build();
        }
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        if (!content.getLesson().getModule().getCourse()
                .getInstructorId().equals(instructorId)) {
            return Response.status(403)
                    .entity("{\"message\":\"Sin permiso\"}")
                    .build();
        }
        em.remove(content);
        return Response.noContent().build();
    }
}
