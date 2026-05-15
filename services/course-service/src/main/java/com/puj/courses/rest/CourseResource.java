package com.puj.courses.rest;

import com.puj.courses.dto.CourseRequest;
import com.puj.courses.dto.CourseResponse;
import com.puj.courses.service.CourseService;
import com.puj.security.rbac.AuthenticatedUser;
import com.puj.security.rbac.RequiresRole;
import com.puj.security.rbac.Role;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
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
}
