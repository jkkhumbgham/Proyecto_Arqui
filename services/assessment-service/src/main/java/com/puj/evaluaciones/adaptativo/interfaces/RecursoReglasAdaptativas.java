package com.puj.evaluaciones.adaptativo.interfaces;

import com.puj.evaluaciones.adaptativo.aplicacion.MotorAdaptativo;
import com.puj.evaluaciones.adaptativo.dominio.ReglaAdaptativa;
import com.puj.evaluaciones.adaptativo.dominio.RepositorioReglasAdaptativas;
import com.puj.evaluaciones.entregas.dominio.Entrega;
import com.puj.evaluaciones.entregas.dominio.EstadoEntrega;
import com.puj.evaluaciones.entregas.dominio.RepositorioEntregas;
import com.puj.seguridad.rbac.UsuarioAutenticado;
import com.puj.seguridad.rbac.RequiereRol;
import com.puj.seguridad.rbac.Rol;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Recurso REST para la gestión de reglas adaptativas.
 *
 * <p>Permite a los instructores crear, consultar y eliminar reglas adaptativas
 * que determinan si un estudiante debe recibir contenido suplementario tras
 * realizar una evaluación. También expone un endpoint para que los estudiantes
 * consulten las lecciones suplementarias que han desbloqueado.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Path("/adaptive-rules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Reglas Adaptativas")
public class RecursoReglasAdaptativas {

    @Inject private RepositorioReglasAdaptativas repoReglas;
    @Inject private RepositorioEntregas          repoEntregas;
    @Inject private MotorAdaptativo              motorAdaptativo;
    @Inject private UsuarioAutenticado            usuarioAutenticado;

    /**
     * Crea o actualiza la regla adaptativa de una evaluación.
     *
     * <p>Solo los instructores pueden gestionar reglas adaptativas. Tras persistir
     * la regla, invalida la caché Redis correspondiente.</p>
     *
     * @param regla cuerpo JSON con los datos de la regla adaptativa
     * @return respuesta 201 con la regla persistida
     */
    @POST
    @RequiereRol(Rol.INSTRUCTOR)
    @Transactional
    @Operation(summary = "Crear/actualizar regla adaptativa de una evaluación (INSTRUCTOR)")
    public Response crearOActualizar(ReglaAdaptativa regla) {
        regla.setIdInstructor(UUID.fromString(usuarioAutenticado.obtenerIdUsuario()));
        repoReglas.guardar(regla);
        motorAdaptativo.invalidarCache(regla.getIdEvaluacion());
        return Response.status(Response.Status.CREATED).entity(regla).build();
    }

    /**
     * Consulta la regla adaptativa activa de una evaluación.
     *
     * @param idEvaluacion UUID de la evaluación
     * @return respuesta 200 con la regla, o 404 si no existe
     */
    @GET
    @Path("/assessments/{assessmentId}")
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN})
    @Operation(summary = "Consultar regla adaptativa de una evaluación")
    public Response buscarPorEvaluacion(@PathParam("assessmentId") UUID idEvaluacion) {
        return repoReglas.buscarPorEvaluacion(idEvaluacion)
                .map(r -> Response.ok(r).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Retorna los identificadores de lecciones suplementarias desbloqueadas
     * para el estudiante autenticado en un curso dado.
     *
     * <p>Una lección suplementaria se desbloquea cuando el estudiante realizó
     * al menos un intento calificado en la evaluación asociada a la regla y
     * su puntaje estuvo por debajo del umbral configurado.</p>
     *
     * @param idCurso UUID del curso para el que se consultan las lecciones
     *                suplementarias desbloqueadas
     * @return respuesta 200 con la lista de UUIDs de lecciones desbloqueadas
     *         (como {@code String})
     */
    @GET
    @Path("/unlocked-supplementary")
    @RequiereRol(Rol.STUDENT)
    @Operation(
        summary = "Lecciones suplementarias desbloqueadas para el estudiante en un curso")
    public Response obtenerLeccionesDesbloqueadas(@QueryParam("courseId") UUID idCurso) {
        UUID idUsuario = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        List<ReglaAdaptativa> reglas = repoReglas.buscarPorCurso(idCurso);
        List<String> idsDesbloqueados = new ArrayList<>();

        for (ReglaAdaptativa regla : reglas) {
            if (regla.getIdLeccionSupplementaria() == null) continue;
            List<Entrega> entregas =
                    repoEntregas.buscarPorUsuarioYEvaluacion(idUsuario, regla.getIdEvaluacion());
            boolean aplica = entregas.stream()
                    .filter(e -> e.getEstado() == EstadoEntrega.GRADED
                              && e.getPuntuacionMaxima() != null
                              && e.getPuntuacionMaxima().compareTo(BigDecimal.ZERO) > 0)
                    .anyMatch(e ->
                            e.getPuntuacion().doubleValue()
                            / e.getPuntuacionMaxima().doubleValue() * 100.0
                            < regla.getUmbralPorcentaje());
            if (aplica) {
                idsDesbloqueados.add(regla.getIdLeccionSupplementaria().toString());
            }
        }
        return Response.ok(idsDesbloqueados).build();
    }

    /**
     * Elimina una regla adaptativa de forma lógica (soft delete).
     *
     * <p>Tras el borrado, invalida la caché Redis de la regla correspondiente.</p>
     *
     * @param id UUID de la regla a eliminar
     * @return respuesta 204 sin contenido
     */
    @DELETE
    @Path("/{id}")
    @RequiereRol(Rol.INSTRUCTOR)
    @Transactional
    @Operation(summary = "Eliminar regla adaptativa (soft delete)")
    public Response eliminar(@PathParam("id") UUID id) {
        repoReglas.buscarPorId(id).ifPresent(r -> {
            r.eliminarLogicamente();
            repoReglas.guardar(r);
            motorAdaptativo.invalidarCache(r.getIdEvaluacion());
        });
        return Response.noContent().build();
    }
}
