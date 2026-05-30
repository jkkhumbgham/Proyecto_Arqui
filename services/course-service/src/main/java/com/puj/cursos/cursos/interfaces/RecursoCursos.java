package com.puj.cursos.cursos.interfaces;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.puj.cursos.cursos.aplicacion.ServicioCursos;
import com.puj.cursos.cursos.dominio.Curso;
import com.puj.cursos.cursos.interfaces.dto.RespuestaCurso;
import com.puj.cursos.cursos.interfaces.dto.SolicitudCurso;
import com.puj.cursos.lecciones.dominio.ProgresoLeccion;
import com.puj.cursos.modulos.dominio.Modulo;
import com.puj.seguridad.rbac.RequiereRol;
import com.puj.seguridad.rbac.Rol;
import com.puj.seguridad.rbac.UsuarioAutenticado;
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
 * La autorización por rol se delega a la anotación {@link RequiereRol}.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Path("/courses")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Cursos")
public class RecursoCursos {

    @Inject private ServicioCursos    servicioCursos;
    @Inject private UsuarioAutenticado usuarioAutenticado;

    @PersistenceContext(unitName = "CourseServicePU")
    private EntityManager em;

    /**
     * DTO de entrada para crear un módulo directamente desde el endpoint de cursos.
     *
     * @param titulo      título del módulo; no debe ser vacío
     * @param descripcion descripción del módulo; puede ser {@code null}
     * @param ordenIndex  posición del módulo; si es {@code null} se asigna el siguiente valor
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public record SolicitudCrearModulo(
            @JsonProperty("title")       @NotBlank String  titulo,
            @JsonProperty("description")           String  descripcion,
            @JsonProperty("orderIndex")            Integer ordenIndex) {}

    /**
     * Lista los cursos en estado {@code PUBLISHED} con soporte de paginación.
     *
     * @param  pagina número de página, base 0 (por defecto 0)
     * @param  tamano tamaño de página, máximo 50 (por defecto 20)
     * @return respuesta 200 con la lista de cursos bajo la clave {@code data}
     */
    @GET
    @Operation(summary = "Listar cursos publicados")
    public Response listar(
            @QueryParam("page") @DefaultValue("0")  int pagina,
            @QueryParam("size") @DefaultValue("20") int tamano) {
        List<RespuestaCurso> cursos = servicioCursos.buscarPublicados(pagina, Math.min(tamano, 50));
        return Response.ok(Map.of("data", cursos)).build();
    }

    /**
     * Lista los cursos del instructor autenticado con soporte de paginación.
     *
     * @param  pagina número de página, base 0 (por defecto 0)
     * @param  tamano tamaño de página, máximo 20 (por defecto 20)
     * @return lista de cursos del instructor
     */
    @GET
    @Path("/my")
    @RequiereRol({Rol.INSTRUCTOR})
    @Operation(summary = "Mis cursos (INSTRUCTOR)")
    public List<RespuestaCurso> misCursos(
            @QueryParam("page") @DefaultValue("0")  int pagina,
            @QueryParam("size") @DefaultValue("20") int tamano) {
        UUID idInstructor = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        return servicioCursos.buscarPorInstructor(idInstructor, pagina, tamano);
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
    public RespuestaCurso buscarPorId(@PathParam("id") UUID id) {
        return servicioCursos.buscarPorId(id);
    }

    /**
     * Crea un nuevo curso asignado al instructor autenticado.
     *
     * @param  solicitud datos del curso a crear; debe superar la validación de Bean Validation
     * @return respuesta 201 con el DTO del curso creado
     */
    @POST
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN})
    @Operation(summary = "Crear curso (INSTRUCTOR/ADMIN)")
    public Response crear(@Valid SolicitudCurso solicitud) {
        UUID idInstructor = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        RespuestaCurso curso = servicioCursos.crear(solicitud, idInstructor);
        return Response.status(Response.Status.CREATED).entity(curso).build();
    }

    /**
     * Actualiza los datos de un curso existente.
     *
     * @param  id       identificador del curso a actualizar
     * @param  solicitud campos a actualizar; los nulos se ignoran
     * @return DTO del curso actualizado
     */
    @PUT
    @Path("/{id}")
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN})
    @Operation(summary = "Actualizar curso")
    public RespuestaCurso actualizar(@PathParam("id") UUID id, @Valid SolicitudCurso solicitud) {
        UUID idInstructor = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        return servicioCursos.actualizar(id, solicitud, idInstructor);
    }

    /**
     * Publica un curso, haciéndolo visible y disponible para matriculaciones.
     *
     * @param  id identificador del curso a publicar
     * @return DTO del curso en estado {@code PUBLISHED}
     */
    @POST
    @Path("/{id}/publish")
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN})
    @Operation(summary = "Publicar curso")
    public RespuestaCurso publicar(@PathParam("id") UUID id) {
        UUID idInstructor = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        return servicioCursos.publicar(id, idInstructor);
    }

    /**
     * Realiza el borrado lógico de un curso que no esté publicado.
     *
     * @param  id identificador del curso a eliminar
     * @return respuesta 204 sin cuerpo
     */
    @DELETE
    @Path("/{id}")
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN})
    @Operation(summary = "Eliminar curso (soft delete)")
    public Response eliminar(@PathParam("id") UUID id) {
        UUID idInstructor = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        servicioCursos.eliminar(id, idInstructor);
        return Response.noContent().build();
    }

    /**
     * Devuelve el progreso del usuario autenticado en el curso indicado.
     *
     * @param  idCurso identificador del curso
     * @return respuesta 200 con {@code completedLessonIds}, {@code completedCount},
     *         {@code totalLessons} y {@code progressPct}
     */
    @GET
    @Path("/{id}/progress")
    @Transactional
    @RequiereRol({Rol.STUDENT, Rol.INSTRUCTOR, Rol.ADMIN})
    @Operation(summary = "Progreso del usuario en un curso")
    public Response obtenerProgreso(@PathParam("id") UUID idCurso) {
        UUID idUsuario = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());

        List<ProgresoLeccion> listaProgreso = em.createQuery(
                "SELECT p FROM ProgresoLeccion p"
                + " WHERE p.idUsuario = :uid AND p.idCurso = :cid",
                ProgresoLeccion.class)
                .setParameter("uid", idUsuario)
                .setParameter("cid", idCurso)
                .getResultList();

        long totalLecciones = em.createQuery(
                "SELECT COUNT(l) FROM Leccion l"
                + " WHERE l.modulo.curso.id = :cid"
                + "   AND l.eliminadoEn IS NULL AND l.esSuplementaria = false",
                Long.class)
                .setParameter("cid", idCurso)
                .getSingleResult();

        List<String> idsCompletadas = listaProgreso.stream()
                .map(p -> p.obtenerLeccion().getId().toString())
                .toList();
        long completadas = listaProgreso.stream()
                .filter(p -> !p.obtenerLeccion().esSuplementaria())
                .count();
        double pct = totalLecciones > 0 ? (completadas * 100.0 / totalLecciones) : 0.0;

        return Response.ok(Map.of(
                "completedLessonIds", idsCompletadas,
                "completedCount",     completadas,
                "totalLessons",       totalLecciones,
                "progressPct",        Math.round(pct * 10.0) / 10.0
        )).build();
    }

    /**
     * Crea un nuevo módulo dentro del curso indicado.
     *
     * @param  idCurso  identificador del curso padre
     * @param  solicitud datos del módulo a crear
     * @return respuesta 201 con el identificador y los datos básicos del módulo creado
     */
    @POST
    @Path("/{id}/modules")
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN})
    @Transactional
    @Operation(summary = "Crear módulo en un curso")
    public Response crearModulo(@PathParam("id") UUID idCurso, SolicitudCrearModulo solicitud) {
        UUID idInstructor = UUID.fromString(usuarioAutenticado.obtenerIdUsuario());
        Curso curso = servicioCursos.buscarCrudo(idCurso, idInstructor);
        int siguienteOrden = curso.obtenerModulos().stream()
                .filter(m -> !m.estaEliminado())
                .mapToInt(Modulo::obtenerOrdenIndex)
                .max()
                .orElse(0) + 1;
        Modulo modulo = new Modulo();
        modulo.establecerCurso(curso);
        modulo.establecerTitulo(solicitud.titulo());
        modulo.establecerDescripcion(solicitud.descripcion());
        modulo.establecerOrdenIndex(solicitud.ordenIndex() != null ? solicitud.ordenIndex() : siguienteOrden);
        em.persist(modulo);
        return Response.status(Response.Status.CREATED).entity(Map.of(
                "id",          modulo.getId(),
                "title",       modulo.obtenerTitulo(),
                "description", modulo.obtenerDescripcion() != null ? modulo.obtenerDescripcion() : "",
                "orderIndex",  modulo.obtenerOrdenIndex(),
                "courseId",    idCurso
        )).build();
    }
}
