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

@Path("/courses")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Cursos")
public class CourseResource {

    @Inject private CourseService     courseService;
    @Inject private AuthenticatedUser authenticatedUser;

    @PersistenceContext(unitName = "CourseServicePU")
    private EntityManager em;

    @GET
    @Operation(summary = "Listar cursos publicados")
    public Response listPublished(@QueryParam("page") @DefaultValue("0") int page,
                                  @QueryParam("size") @DefaultValue("20") int size) {
        List<CourseResponse> courses = courseService.findPublished(page, Math.min(size, 50));
        return Response.ok(Map.of("data", courses)).build();
    }

    @GET
    @Path("/my")
    @RequiresRole({Role.INSTRUCTOR})
    @Operation(summary = "Mis cursos (INSTRUCTOR)")
    public List<CourseResponse> myCourses(@QueryParam("page") @DefaultValue("0") int page,
                                          @QueryParam("size") @DefaultValue("20") int size) {
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        return courseService.findByInstructor(instructorId, page, size);
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Obtener curso por ID")
    public CourseResponse findById(@PathParam("id") UUID id) {
        return courseService.findById(id);
    }

    @POST
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Crear curso (INSTRUCTOR/ADMIN)")
    public Response create(@Valid CourseRequest req) {
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        CourseResponse course = courseService.create(req, instructorId);
        return Response.status(Response.Status.CREATED).entity(course).build();
    }

    @PUT
    @Path("/{id}")
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Actualizar curso")
    public CourseResponse update(@PathParam("id") UUID id, @Valid CourseRequest req) {
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        return courseService.update(id, req, instructorId);
    }

    @POST
    @Path("/{id}/publish")
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Publicar curso")
    public CourseResponse publish(@PathParam("id") UUID id) {
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        return courseService.publish(id, instructorId);
    }

    @DELETE
    @Path("/{id}")
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Eliminar curso (soft delete)")
    public Response delete(@PathParam("id") UUID id) {
        UUID instructorId = UUID.fromString(authenticatedUser.getUserId());
        courseService.delete(id, instructorId);
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/progress")
    @Transactional
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Progreso del usuario en un curso")
    public Response getCourseProgress(@PathParam("id") UUID courseId) {
        UUID userId = UUID.fromString(authenticatedUser.getUserId());

        List<LessonProgress> progressList = em.createQuery(
            "SELECT p FROM LessonProgress p WHERE p.userId = :uid AND p.courseId = :cid",
            LessonProgress.class)
            .setParameter("uid", userId).setParameter("cid", courseId).getResultList();

        long totalLessons = em.createQuery(
            "SELECT COUNT(l) FROM Lesson l WHERE l.module.course.id = :cid" +
            " AND l.deletedAt IS NULL AND l.supplementary = false", Long.class)
            .setParameter("cid", courseId).getSingleResult();

        List<String> completedIds = progressList.stream()
                .map(p -> p.getLesson().getId().toString()).toList();
        long completed = progressList.stream()
                .filter(p -> !p.getLesson().isSupplementary()).count();
        double pct = totalLessons > 0 ? (completed * 100.0 / totalLessons) : 0.0;

        return Response.ok(Map.of(
                "completedLessonIds", completedIds,
                "completedCount",     completed,
                "totalLessons",       totalLessons,
                "progressPct",        Math.round(pct * 10.0) / 10.0
        )).build();
    }

    public record ModuleCreateRequest(@NotBlank String title, String description, Integer orderIndex) {}

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
                .mapToInt(Module::getOrderIndex).max().orElse(0) + 1;
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
