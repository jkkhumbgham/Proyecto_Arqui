package com.puj.courses.rest;

import com.puj.courses.dto.EnrollmentResponse;
import com.puj.courses.entity.Enrollment;
import com.puj.courses.entity.EnrollmentStatus;
import com.puj.courses.repository.EnrollmentRepository;
import com.puj.courses.service.EnrollmentService;
import com.puj.security.rbac.AuthenticatedUser;
import com.puj.security.rbac.RequiresRole;
import com.puj.security.rbac.Role;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Arrays;

@Path("/enrollments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Inscripciones")
public class EnrollmentResource {

    @Inject private EnrollmentService    enrollmentService;
    @Inject private EnrollmentRepository enrollmentRepo;
    @Inject private AuthenticatedUser    authenticatedUser;

    @POST
    @Path("/courses/{courseId}")
    @RequiresRole(Role.STUDENT)
    @Operation(summary = "Inscribirse en un curso (STUDENT)")
    public Response enroll(@PathParam("courseId") UUID courseId) {
        UUID userId = UUID.fromString(authenticatedUser.getUserId());
        EnrollmentResponse enrollment = enrollmentService.enroll(userId, courseId);
        return Response.status(Response.Status.CREATED).entity(enrollment).build();
    }

    @GET
    @Path("/my")
    @RequiresRole(Role.STUDENT)
    @Operation(summary = "Mis inscripciones")
    public List<EnrollmentResponse> myEnrollments() {
        UUID userId = UUID.fromString(authenticatedUser.getUserId());
        return enrollmentService.findByUser(userId);
    }

    @GET
    @Path("/course/{courseId}/stats")
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Operation(summary = "Estadísticas de inscripción por curso (INSTRUCTOR/ADMIN)")
    public Response courseStats(@PathParam("courseId") UUID courseId) {
        long enrolledCount    = enrollmentRepo.countByCourse(courseId);
        double avgProgressPct = enrollmentRepo.avgProgressPct(courseId);

        // For each student, keep only the module of their most recent lesson completion
        List<Object[]> rawRows = enrollmentRepo.latestModulePerUser(courseId);

        // [userId] -> row with max completedAt across all modules
        Map<UUID, Object[]> latestPerUser = new LinkedHashMap<>();
        for (Object[] row : rawRows) {
            UUID    userId      = (UUID) row[0];
            Instant completedAt = (Instant) row[4];
            Object[] current    = latestPerUser.get(userId);
            if (current == null || completedAt.isAfter((Instant) current[4])) {
                latestPerUser.put(userId, row);
            }
        }

        // Aggregate count per module preserving order
        Map<String, long[]>   moduleCounts = new LinkedHashMap<>();
        Map<String, Object[]> moduleMeta   = new LinkedHashMap<>(); // moduleId -> [title, orderIndex]
        for (Object[] row : latestPerUser.values()) {
            String moduleId    = row[1].toString();
            String moduleTitle = row[2].toString();
            int    orderIndex  = ((Number) row[3]).intValue();
            moduleCounts.computeIfAbsent(moduleId, k -> new long[]{0})[0]++;
            moduleMeta.put(moduleId, new Object[]{moduleTitle, orderIndex});
        }

        List<Map<String, Object>> moduleDistribution = moduleCounts.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> (int) ((Object[]) moduleMeta.get(e.getKey()))[1]))
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("moduleId",     e.getKey());
                    m.put("moduleTitle",  ((Object[]) moduleMeta.get(e.getKey()))[0]);
                    m.put("studentCount", e.getValue()[0]);
                    return m;
                })
                .collect(Collectors.toList());

        // Prepend "no han comenzado" bucket if applicable
        long notStarted = enrollmentRepo.countNotStarted(courseId);
        if (notStarted > 0) {
            Map<String, Object> ns = new LinkedHashMap<>();
            ns.put("moduleId",    "not-started");
            ns.put("moduleTitle", "No han comenzado el curso");
            ns.put("studentCount", notStarted);
            moduleDistribution.add(0, ns);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enrolledCount",      enrolledCount);
        result.put("avgProgressPct",     Math.round(avgProgressPct * 10.0) / 10.0);
        result.put("moduleDistribution", moduleDistribution);

        return Response.ok(result).build();
    }

    @GET
    @Path("/unique-students")
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN, Role.DIRECTOR})
    @Operation(summary = "Cantidad de estudiantes únicos en un conjunto de cursos (INSTRUCTOR/ADMIN)")
    public Response uniqueStudents(@QueryParam("courseIds") String courseIdsParam) {
        if (courseIdsParam == null || courseIdsParam.isBlank())
            return Response.ok(Map.of("uniqueStudents", 0L)).build();
        List<UUID> ids = Arrays.stream(courseIdsParam.split(","))
                .map(String::trim).filter(s -> !s.isBlank())
                .map(UUID::fromString)
                .collect(Collectors.toList());
        long count = enrollmentRepo.countUniqueStudentsInCourses(ids);
        return Response.ok(Map.of("uniqueStudents", count)).build();
    }

    @GET
    @Path("/admin/course-stats")
    @RequiresRole({Role.ADMIN})
    @Operation(summary = "Estadísticas globales de cursos para el panel de administración (ADMIN)")
    public Response adminCourseStats(
            @QueryParam("limit") @DefaultValue("5") int limit) {

        int safeLimit = Math.min(Math.max(limit, 1), 20);

        long totalEnrollments = enrollmentRepo.countAll();
        long totalCompleted   = enrollmentRepo.countCompleted();

        List<Map<String, Object>> popular = enrollmentRepo.topPopularCourses(safeLimit)
                .stream().map(row -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("courseId",    row[0].toString());
                    m.put("courseTitle", row[1].toString());
                    m.put("enrollCount", ((Number) row[2]).longValue());
                    return m;
                }).collect(Collectors.toList());

        List<Map<String, Object>> completed = enrollmentRepo.topCompletedCourses(safeLimit)
                .stream().map(row -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("courseId",       row[0].toString());
                    m.put("courseTitle",    row[1].toString());
                    m.put("completedCount", ((Number) row[2]).longValue());
                    return m;
                }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalEnrollments",          totalEnrollments);
        result.put("totalCompletedEnrollments", totalCompleted);
        result.put("popularCourses",            popular);
        result.put("completedCourses",          completed);
        return Response.ok(result).build();
    }

    @POST
    @Path("/courses/{courseId}/finalize")
    @Transactional
    @RequiresRole(Role.STUDENT)
    @Operation(summary = "Finalizar curso (marcar COMPLETED) — requiere 100% progreso (STUDENT)")
    public Response finalize(@PathParam("courseId") UUID courseId) {
        UUID userId = UUID.fromString(authenticatedUser.getUserId());
        Enrollment enrollment = enrollmentRepo.findByUserAndCourse(userId, courseId)
                .orElse(null);
        if (enrollment == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("message", "Inscripción no encontrada")).build();
        }
        if (enrollment.getProgressPct() < 100.0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("message", "El curso no está al 100% de progreso")).build();
        }
        enrollment.setStatus(EnrollmentStatus.COMPLETED);
        enrollment.setCompletedAt(Instant.now());
        enrollmentRepo.merge(enrollment);
        return Response.ok(Map.of("status", "COMPLETED")).build();
    }

    @DELETE
    @Path("/courses/{courseId}")
    @RequiresRole(Role.STUDENT)
    @Operation(summary = "Cancelar inscripción")
    public Response cancel(@PathParam("courseId") UUID courseId) {
        UUID userId = UUID.fromString(authenticatedUser.getUserId());
        enrollmentService.cancel(userId, courseId);
        return Response.noContent().build();
    }
}
