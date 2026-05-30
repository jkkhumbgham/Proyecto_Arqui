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

/**
 * Recurso REST para la gestión de módulos y la creación de lecciones dentro de ellos.
 *
 * <p>Expone endpoints bajo la ruta base {@code /api/v1/modules} para consultar,
 * actualizar, borrar lógicamente módulos y crear lecciones en su interior.
 * Las operaciones de escritura validan que el instructor autenticado sea propietario
 * del curso al que pertenece el módulo.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Path("/modules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Módulos")
public class ModuleResource {

    @PersistenceContext(unitName = "CourseServicePU")
    private EntityManager em;

    @Inject private AuthenticatedUser authenticatedUser;

    /**
     * DTO de entrada para actualizar los datos de un módulo.
     *
     * @param title       nuevo título; no debe ser vacío
     * @param description nueva descripción; puede ser {@code null}
     * @param orderIndex  nueva posición dentro del curso; puede ser {@code null}
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public record ModuleUpdateRequest(
            @NotBlank String title,
            String  description,
            Integer orderIndex) {}

    /**
     * DTO de entrada para crear una lección dentro de un módulo.
     *
     * @param title           título de la lección; no debe ser vacío
     * @param content         contenido textual de la lección; puede ser {@code null}
     * @param orderIndex      posición dentro del módulo; si es {@code null} se asigna
     *                        el siguiente valor disponible
     * @param durationMinutes duración estimada en minutos; puede ser {@code null}
     * @param contentType     tipo de contenido (TEXT, VIDEO, PDF, DOCUMENT);
     *                        puede ser {@code null}
     * @param contentUrl      URL del recurso externo; puede ser {@code null}
     * @param supplementary   {@code true} si la lección es material suplementario;
     *                        puede ser {@code null} (equivalente a {@code false})
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public record LessonCreateRequest(
            @NotBlank String title,
            String  content,
            Integer orderIndex,
            Integer durationMinutes,
            String  contentType,
            String  contentUrl,
            Boolean supplementary
    ) {}

    /**
     * Obtiene el detalle básico de un módulo por su identificador.
     *
     * @param  id identificador del módulo
     * @return respuesta 200 con el mapa de datos del módulo,
     *         o 404 si el módulo no existe o fue borrado lógicamente
     */
    @GET
    @Path("/{id}")
    @Transactional
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN, Role.STUDENT, Role.DIRECTOR})
    @Operation(summary = "Obtener módulo por ID")
    public Response findById(@PathParam("id") UUID id) {
        Module m = em.find(Module.class, id);
        if (m == null || m.isDeleted()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"message\":\"Módulo no encontrado\"}")
                    .build();
        }
        return Response.ok(Map.of(
                "id",          m.getId(),
                "title",       m.getTitle(),
                "description", m.getDescription() != null ? m.getDescription() : "",
                "orderIndex",  m.getOrderIndex(),
                "courseId",    m.getCourse().getId()
        )).build();
    }

    /**
     * Actualiza los datos de un módulo existente.
     *
     * <p>Los campos nulos del request se ignoran. El instructor autenticado
     * debe ser propietario del curso al que pertenece el módulo.
     *
     * @param  id  identificador del módulo a actualizar
     * @param  req campos a actualizar
     * @return respuesta 200 con el identificador y título actualizados,
     *         404 si el módulo no existe, 403 si no es el instructor propietario
     */
    @PUT
    @Path("/{id}")
    @Transactional
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Actualizar módulo")
    public Response update(@PathParam("id") UUID id, ModuleUpdateRequest req) {
        Module m = em.find(Module.class, id);
        if (m == null || m.isDeleted()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"message\":\"Módulo no encontrado\"}")
                    .build();
        }
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        if (!m.getCourse().getInstructorId().equals(instructorId)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"message\":\"No eres el instructor de este curso\"}")
                    .build();
        }
        m.setTitle(req.title());
        if (req.description() != null) m.setDescription(req.description());
        if (req.orderIndex() != null)  m.setOrderIndex(req.orderIndex());
        em.merge(m);
        return Response.ok(Map.of("id", m.getId(), "title", m.getTitle())).build();
    }

    /**
     * Realiza el borrado lógico de un módulo.
     *
     * <p>El instructor autenticado debe ser propietario del curso al que pertenece
     * el módulo.
     *
     * @param  id identificador del módulo a eliminar
     * @return respuesta 204 sin cuerpo,
     *         404 si el módulo no existe, 403 si no es el instructor propietario
     */
    @DELETE
    @Path("/{id}")
    @Transactional
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Eliminar módulo (soft delete)")
    public Response delete(@PathParam("id") UUID id) {
        Module m = em.find(Module.class, id);
        if (m == null || m.isDeleted()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"message\":\"Módulo no encontrado\"}")
                    .build();
        }
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        if (!m.getCourse().getInstructorId().equals(instructorId)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"message\":\"No eres el instructor de este curso\"}")
                    .build();
        }
        m.softDelete();
        em.merge(m);
        return Response.noContent().build();
    }

    /**
     * Crea una nueva lección dentro del módulo indicado.
     *
     * <p>Si no se especifica {@code orderIndex}, se asigna el siguiente valor tras
     * la lección con índice más alto existente en el módulo. El instructor autenticado
     * debe ser propietario del curso.
     *
     * @param  moduleId identificador del módulo padre
     * @param  req      datos de la lección a crear
     * @return respuesta 201 con el identificador y los datos básicos de la lección creada,
     *         404 si el módulo no existe, 403 si no es el instructor propietario
     */
    @POST
    @Path("/{id}/lessons")
    @Transactional
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Crear lección en un módulo")
    public Response createLesson(@PathParam("id") UUID moduleId, LessonCreateRequest req) {
        Module m = em.find(Module.class, moduleId);
        if (m == null || m.isDeleted()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"message\":\"Módulo no encontrado\"}")
                    .build();
        }
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        if (!m.getCourse().getInstructorId().equals(instructorId)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"message\":\"No eres el instructor de este curso\"}")
                    .build();
        }
        int nextOrder = m.getLessons().stream()
                .filter(l -> !l.isDeleted())
                .mapToInt(Lesson::getOrderIndex)
                .max()
                .orElse(0) + 1;

        Lesson lesson = new Lesson();
        lesson.setModule(m);
        lesson.setTitle(req.title());
        lesson.setContent(req.content());
        lesson.setOrderIndex(req.orderIndex() != null ? req.orderIndex() : nextOrder);
        lesson.setDurationMinutes(req.durationMinutes());
        if (req.contentType() != null && !req.contentType().isBlank()) {
            lesson.setContentType(req.contentType());
        }
        if (req.contentUrl() != null && !req.contentUrl().isBlank()) {
            lesson.setContentUrl(req.contentUrl());
        }
        if (Boolean.TRUE.equals(req.supplementary())) {
            lesson.setSupplementary(true);
        }
        em.persist(lesson);

        return Response.status(Response.Status.CREATED).entity(Map.of(
                "id",         lesson.getId(),
                "title",      lesson.getTitle(),
                "orderIndex", lesson.getOrderIndex(),
                "moduleId",   moduleId,
                "courseId",   m.getCourse().getId()
        )).build();
    }
}
