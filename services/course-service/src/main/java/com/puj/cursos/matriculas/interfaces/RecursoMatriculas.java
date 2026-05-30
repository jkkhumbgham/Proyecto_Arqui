package com.puj.cursos.matriculas.interfaces;

import com.puj.cursos.matriculas.aplicacion.ServicioMatriculas;
import com.puj.cursos.matriculas.dominio.EstadoMatricula;
import com.puj.cursos.matriculas.dominio.Matricula;
import com.puj.cursos.matriculas.dominio.RepositorioMatriculas;
import com.puj.cursos.matriculas.interfaces.dto.RespuestaMatricula;
import com.puj.seguridad.rbac.RequiereRol;
import com.puj.seguridad.rbac.Rol;
import com.puj.seguridad.rbac.UsuarioAutenticado;
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

/**
 * Recurso REST para la gestión de matrículas de estudiantes en cursos.
 *
 * <p>Expone endpoints bajo la ruta base {@code /api/v1/enrollments} para
 * matricularse, consultar matrículas propias, obtener estadísticas y finalizar cursos.
 * La autorización por rol se delega a la anotación {@link RequiereRol}.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Path("/enrollments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Matrículas")
public class RecursoMatriculas {

    @Inject private ServicioMatriculas    servicioMatriculas;
    @Inject private RepositorioMatriculas repositorioMatriculas;
    @Inject private UsuarioAutenticado    usuarioAutenticado;

    /**
     * Matricula al estudiante autenticado en el curso indicado.
     *
     * @param  idCurso identificador del curso en el que se desea matricular
     * @return respuesta 201 con el DTO de la matrícula creada
     */
    @POST
    @Path("/courses/{courseId}")
    @RequiereRol(Rol.STUDENT)
    @Operation(summary = "Matricularse en un curso (STUDENT)")
    public Response matricular(@PathParam("courseId") UUID idCurso) {
        UUID idUsuario = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        RespuestaMatricula matricula = servicioMatriculas.matricular(idUsuario, idCurso);
        return Response.status(Response.Status.CREATED).entity(matricula).build();
    }

    /**
     * Devuelve todas las matrículas activas del estudiante autenticado.
     *
     * @return lista de matrículas del estudiante
     */
    @GET
    @Path("/my")
    @RequiereRol(Rol.STUDENT)
    @Operation(summary = "Mis matrículas")
    public List<RespuestaMatricula> misMatriculas() {
        UUID idUsuario = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        return servicioMatriculas.buscarPorUsuario(idUsuario);
    }

    /**
     * Devuelve estadísticas de matrícula de un curso, incluyendo distribución
     * de estudiantes por módulo más reciente completado.
     *
     * @param  idCurso identificador del curso
     * @return respuesta 200 con el mapa de estadísticas
     */
    @GET
    @Path("/course/{courseId}/stats")
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN})
    @Operation(summary = "Estadísticas de matrícula por curso (INSTRUCTOR/ADMIN)")
    public Response estadisticasCurso(@PathParam("courseId") UUID idCurso) {
        long cantidadMatriculados  = repositorioMatriculas.contarPorCurso(idCurso);
        double promedioPorcentaje  = repositorioMatriculas.promedioProgresoPorCurso(idCurso);

        List<Object[]> filasCrudas = repositorioMatriculas.ultimoModuloPorUsuario(idCurso);

        Map<UUID, Object[]> ultimoPorUsuario = new LinkedHashMap<>();
        for (Object[] fila : filasCrudas) {
            UUID    idUsuario   = (UUID) fila[0];
            Instant completadoEn = (Instant) fila[4];
            Object[] actual      = ultimoPorUsuario.get(idUsuario);
            if (actual == null || completadoEn.isAfter((Instant) actual[4])) {
                ultimoPorUsuario.put(idUsuario, fila);
            }
        }

        Map<String, long[]>   conteoPorModulo = new LinkedHashMap<>();
        Map<String, Object[]> metaModulo      = new LinkedHashMap<>();
        for (Object[] fila : ultimoPorUsuario.values()) {
            String idModulo    = fila[1].toString();
            String tituloModulo = fila[2].toString();
            int    ordenIndex  = ((Number) fila[3]).intValue();
            conteoPorModulo.computeIfAbsent(idModulo, k -> new long[]{0})[0]++;
            metaModulo.put(idModulo, new Object[]{tituloModulo, ordenIndex});
        }

        List<Map<String, Object>> distribucionModulos = conteoPorModulo.entrySet().stream()
                .sorted(Comparator.comparingInt(
                        e -> (int) ((Object[]) metaModulo.get(e.getKey()))[1]))
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("moduleId",     e.getKey());
                    m.put("moduleTitle",  ((Object[]) metaModulo.get(e.getKey()))[0]);
                    m.put("studentCount", e.getValue()[0]);
                    return m;
                })
                .collect(Collectors.toList());

        long sinIniciar = repositorioMatriculas.contarSinIniciar(idCurso);
        if (sinIniciar > 0) {
            Map<String, Object> ns = new LinkedHashMap<>();
            ns.put("moduleId",     "not-started");
            ns.put("moduleTitle",  "No han comenzado el curso");
            ns.put("studentCount", sinIniciar);
            distribucionModulos.add(0, ns);
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("enrolledCount",      cantidadMatriculados);
        resultado.put("avgProgressPct",     Math.round(promedioPorcentaje * 10.0) / 10.0);
        resultado.put("moduleDistribution", distribucionModulos);

        return Response.ok(resultado).build();
    }

    /**
     * Cuenta los estudiantes únicos matriculados en el conjunto de cursos indicado.
     *
     * @param  paramIdsCurso lista de UUIDs separados por coma
     * @return respuesta 200 con la clave {@code uniqueStudents}
     */
    @GET
    @Path("/unique-students")
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN, Rol.DIRECTOR})
    @Operation(summary = "Cantidad de estudiantes únicos en un conjunto de cursos")
    public Response estudiantesUnicos(@QueryParam("courseIds") String paramIdsCurso) {
        if (paramIdsCurso == null || paramIdsCurso.isBlank()) {
            return Response.ok(Map.of("uniqueStudents", 0L)).build();
        }
        List<UUID> ids = Arrays.stream(paramIdsCurso.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(UUID::fromString)
                .collect(Collectors.toList());
        long conteo = repositorioMatriculas.contarEstudiantesUnicosEnCursos(ids);
        return Response.ok(Map.of("uniqueStudents", conteo)).build();
    }

    /**
     * Devuelve estadísticas globales de matrículas para el panel de administración.
     *
     * @param  limite número de cursos en cada ranking (entre 1 y 20, por defecto 5)
     * @return respuesta 200 con estadísticas globales
     */
    @GET
    @Path("/admin/course-stats")
    @RequiereRol({Rol.ADMIN})
    @Operation(summary = "Estadísticas globales de cursos para el panel de administración (ADMIN)")
    public Response estadisticasAdmin(
            @QueryParam("limit") @DefaultValue("5") int limite) {

        int limiteSeguro = Math.min(Math.max(limite, 1), 20);

        long totalMatriculas    = repositorioMatriculas.contarTodas();
        long totalCompletadas   = repositorioMatriculas.contarCompletadas();

        List<Map<String, Object>> populares = repositorioMatriculas.cursosMasPopulares(limiteSeguro)
                .stream()
                .map(fila -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("courseId",    fila[0].toString());
                    m.put("courseTitle", fila[1].toString());
                    m.put("enrollCount", ((Number) fila[2]).longValue());
                    return m;
                })
                .collect(Collectors.toList());

        List<Map<String, Object>> completados = repositorioMatriculas.cursosMasCompletados(limiteSeguro)
                .stream()
                .map(fila -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("courseId",       fila[0].toString());
                    m.put("courseTitle",    fila[1].toString());
                    m.put("completedCount", ((Number) fila[2]).longValue());
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("totalEnrollments",          totalMatriculas);
        resultado.put("totalCompletedEnrollments", totalCompletadas);
        resultado.put("popularCourses",            populares);
        resultado.put("completedCourses",          completados);
        return Response.ok(resultado).build();
    }

    /**
     * Marca la matrícula del estudiante como {@code COMPLETED} si tiene el 100% de progreso.
     *
     * @param  idCurso identificador del curso a finalizar
     * @return respuesta 200, 400 o 404 según el resultado
     */
    @POST
    @Path("/courses/{courseId}/finalize")
    @Transactional
    @RequiereRol(Rol.STUDENT)
    @Operation(summary = "Finalizar curso (marcar COMPLETED) — requiere 100% progreso (STUDENT)")
    public Response finalizar(@PathParam("courseId") UUID idCurso) {
        UUID idUsuario = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        Matricula matricula = repositorioMatriculas.buscarPorUsuarioYCurso(idUsuario, idCurso)
                .orElse(null);
        if (matricula == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("message", "Matrícula no encontrada"))
                    .build();
        }
        if (matricula.obtenerPorcentajeProgreso() < 100.0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("message", "El curso no está al 100% de progreso"))
                    .build();
        }
        matricula.establecerEstado(EstadoMatricula.COMPLETED);
        matricula.establecerCompletadoEn(Instant.now());
        repositorioMatriculas.fusionar(matricula);
        return Response.ok(Map.of("status", "COMPLETED")).build();
    }

    /**
     * Cancela la matrícula del estudiante autenticado en el curso indicado.
     *
     * @param  idCurso identificador del curso a cancelar
     * @return respuesta 204 sin cuerpo
     */
    @DELETE
    @Path("/courses/{courseId}")
    @RequiereRol(Rol.STUDENT)
    @Operation(summary = "Cancelar matrícula")
    public Response cancelar(@PathParam("courseId") UUID idCurso) {
        UUID idUsuario = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        servicioMatriculas.cancelar(idUsuario, idCurso);
        return Response.noContent().build();
    }
}
