package com.puj.courses.rest;

import com.puj.courses.dto.CourseRequest;
import com.puj.courses.dto.CourseResponse;
import com.puj.courses.entity.Course;
import com.puj.courses.entity.Lesson;
import com.puj.courses.entity.LessonProgress;
import com.puj.courses.entity.Module;
import com.puj.courses.service.CourseService;
import com.puj.security.rbac.AuthenticatedUser;
import com.puj.security.rbac.RequiresRole;
import com.puj.security.rbac.Role;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
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
 * Recurso REST para la gestión de cursos de la plataforma.
 *
 * <p>Expone los endpoints bajo la ruta base {@code /api/v1/courses} para operaciones
 * CRUD de cursos, publicación, borrado lógico y consulta de progreso del estudiante.
 * La autorización por rol se delega a la anotación {@link RequiresRole}.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Path("/courses")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Cursos")
public class CourseResource {

    @Inject private CourseService     courseService;
    @Inject private AuthenticatedUser authenticatedUser;

    @PersistenceContext(unitName = "CourseServicePU")
    private EntityManager em;

    /**
     * DTO de entrada para crear un módulo directamente desde el endpoint de cursos.
     *
     * @param title       título del módulo; no debe ser vacío
     * @param description descripción del módulo; puede ser {@code null}
     * @param orderIndex  posición del módulo; si es {@code null} se asigna el siguiente valor
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public record ModuleCreateRequest(
            @NotBlank String title,
            String  description,
            Integer orderIndex) {}

    /**
     * Lista los cursos en estado {@code PUBLISHED} con soporte de paginación.
     *
     * @param  page número de página, base 0 (por defecto 0)
     * @param  size tamaño de página, máximo 50 (por defecto 20)
     * @return respuesta 200 con la lista de cursos bajo la clave {@code data}
     */
    @GET
    @Operation(summary = "Listar cursos publicados")
    public Response listPublished(
            @QueryParam("page") @DefaultValue("0")  int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        List<CourseResponse> courses = courseService.findPublished(page, Math.min(size, 50));
        return Response.ok(Map.of("data", courses)).build();
    }

    /**
     * Lista los cursos del instructor autenticado con soporte de paginación.
     *
     * @param  page número de página, base 0 (por defecto 0)
     * @param  size tamaño de página, máximo 20 (por defecto 20)
     * @return lista de cursos del instructor
     */
    @GET
    @Path("/my")
    @RequiresRole({Role.INSTRUCTOR})
    @Operation(summary = "Mis cursos (INSTRUCTOR)")
    public List<CourseResponse> myCourses(
            @QueryParam("page") @DefaultValue("0")  int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        return courseService.findByInstructor(instructorId, page, size);
    }

    /**
     * Obtiene el detalle completo de un curso por su identificador.
     *
     * @param  id identificador del curso
     * @return DTO del curso con módulos y lecciones incluidos
     */
    @GET
    @Path("/{id}")
    @Operation(summary = "Obtener curso por ID")
    public CourseResponse findById(@PathParam("id") UUID id) {
        return courseService.findById(id);
    }

    /**
     * Crea un nuevo curso asignado al instructor autenticado.
     *
     * @param  req datos del curso a crear; debe superar la validación de Bean Validation
     * @return respuesta 201 con el DTO del curso creado
     */
    @POST
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Crear curso (INSTRUCTOR/ADMIN)")
    public Response create(@Valid CourseRequest req) {
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        CourseResponse course = courseService.create(req, instructorId);
        return Response.status(Response.Status.CREATED).entity(course).build();
    }

    /**
     * Actualiza los datos de un curso existente.
     *
     * @param  id  identificador del curso a actualizar
     * @param  req campos a actualizar; los nulos se ignoran
     * @return DTO del curso actualizado
     */
    @PUT
    @Path("/{id}")
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Actualizar curso")
    public CourseResponse update(@PathParam("id") UUID id, @Valid CourseRequest req) {
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        return courseService.update(id, req, instructorId);
    }

    /**
     * Publica un curso, haciéndolo visible y disponible para inscripciones.
     *
     * @param  id identificador del curso a publicar
     * @return DTO del curso en estado {@code PUBLISHED}
     */
    @POST
    @Path("/{id}/publish")
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Publicar curso")
    public CourseResponse publish(@PathParam("id") UUID id) {
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        return courseService.publish(id, instructorId);
    }

    /**
     * Realiza el borrado lógico de un curso que no esté publicado.
     *
     * @param  id identificador del curso a eliminar
     * @return respuesta 204 sin cuerpo
     */
    @DELETE
    @Path("/{id}")
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Eliminar curso (soft delete)")
    public Response delete(@PathParam("id") UUID id) {
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        courseService.delete(id, instructorId);
        return Response.noContent().build();
    }

    /**
     * Devuelve el progreso del usuario autenticado en el curso indicado.
     *
     * <p>Calcula el porcentaje de lecciones no suplementarias completadas sobre
     * el total de lecciones activas del curso.
     *
     * @param  courseId identificador del curso
     * @return respuesta 200 con {@code completedLessonIds}, {@code completedCount},
     *         {@code totalLessons} y {@code progressPct}
     */
    @GET
    @Path("/{id}/progress")
    @Transactional
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Progreso del usuario en un curso")
    public Response getCourseProgress(@PathParam("id") UUID courseId) {
        UUID userId = UUID.fromString(authenticatedUser.getUserId());

        List<LessonProgress> progressList = em.createQuery(
                "SELECT p FROM LessonProgress p"
                + " WHERE p.userId = :uid AND p.courseId = :cid",
                LessonProgress.class)
                .setParameter("uid", userId)
                .setParameter("cid", courseId)
                .getResultList();

        long totalLessons = em.createQuery(
                "SELECT COUNT(l) FROM Lesson l"
                + " WHERE l.module.course.id = :cid"
                + "   AND l.deletedAt IS NULL AND l.supplementary = false",
                Long.class)
                .setParameter("cid", courseId)
                .getSingleResult();

        List<String> completedIds = progressList.stream()
                .map(p -> p.getLesson().getId().toString())
                .toList();
        long completed = progressList.stream()
                .filter(p -> !p.getLesson().isSupplementary())
                .count();
        double pct = totalLessons > 0 ? (completed * 100.0 / totalLessons) : 0.0;

        return Response.ok(Map.of(
                "completedLessonIds", completedIds,
                "completedCount",     completed,
                "totalLessons",       totalLessons,
                "progressPct",        Math.round(pct * 10.0) / 10.0
        )).build();
    }

    /**
     * Crea un nuevo módulo dentro del curso indicado.
     *
     * <p>Si no se especifica {@code orderIndex}, se asigna el siguiente valor
     * tras el módulo con índice más alto existente en el curso.
     *
     * @param  courseId identificador del curso padre
     * @param  req      datos del módulo a crear
     * @return respuesta 201 con el identificador y los datos básicos del módulo creado
     */
    @POST
    @Path("/{id}/modules")
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Transactional
    @Operation(summary = "Crear módulo en un curso")
    public Response createModule(@PathParam("id") UUID courseId, ModuleCreateRequest req) {
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        Course course = courseService.findRaw(courseId, instructorId);
        int nextOrder = course.getModules().stream()
                .filter(m -> !m.isDeleted())
                .mapToInt(Module::getOrderIndex)
                .max()
                .orElse(0) + 1;
        Module module = new Module();
        module.setCourse(course);
        module.setTitle(req.title());
        module.setDescription(req.description());
        module.setOrderIndex(req.orderIndex() != null ? req.orderIndex() : nextOrder);
        em.persist(module);
        return Response.status(Response.Status.CREATED).entity(Map.of(
                "id",          module.getId(),
                "title",       module.getTitle(),
                "description", module.getDescription() != null ? module.getDescription() : "",
                "orderIndex",  module.getOrderIndex(),
                "courseId",    courseId
        )).build();
    }
}
