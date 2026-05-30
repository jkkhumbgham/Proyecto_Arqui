package com.puj.colaboracion.gruposestudio.interfaces;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.puj.colaboracion.gruposestudio.aplicacion.ServicioGruposEstudio;
import com.puj.colaboracion.gruposestudio.dominio.GrupoEstudio;
import com.puj.colaboracion.gruposestudio.dominio.MensajeChat;
import com.puj.colaboracion.gruposestudio.dominio.MiembroGrupo;
import com.puj.colaboracion.gruposestudio.dominio.RepositorioGruposEstudio;
import com.puj.seguridad.rbac.UsuarioAutenticado;
import com.puj.seguridad.rbac.RequiereRol;
import com.puj.seguridad.rbac.Rol;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recurso REST para la gestión de grupos de estudio colaborativos.
 *
 * <p>Permite crear grupos, unirse y abandonarlos, listar miembros,
 * consultar y enviar mensajes de chat. El rol de tutor se gestiona
 * automáticamente por el servicio.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Path("/groups")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RecursoGruposEstudio {

    @Inject private ServicioGruposEstudio    servicio;
    @Inject private RepositorioGruposEstudio repo;
    @Inject private UsuarioAutenticado        usuarioActual;

    /**
     * DTO para la creación de un grupo de estudio.
     */
    public record SolicitudCrearGrupo(
            /** Nombre del grupo. No puede estar en blanco. */
            @JsonProperty("name")       @NotBlank String nombre,

            /** Identificador del curso al que pertenece el grupo. */
            @JsonProperty("courseId")             UUID   idCurso,

            /** Número máximo de miembros. Se usa 10 si es 0 o negativo. */
            @JsonProperty("maxMembers")           int    maxMiembros
    ) {}

    /**
     * DTO para enviar un mensaje de chat al grupo.
     */
    public record SolicitudEnviarMensaje(
            /** Contenido del mensaje. No puede estar en blanco. */
            @JsonProperty("content") @NotBlank String contenido
    ) {}

    /**
     * Lista los grupos de estudio activos de un curso con información de membresía
     * del usuario autenticado.
     *
     * @param idCurso UUID del curso
     * @return respuesta 200 con la lista de grupos y datos de membresía del usuario
     */
    @GET
    @Path("/courses/{courseId}")
    @RequiereRol({Rol.STUDENT, Rol.INSTRUCTOR, Rol.ADMIN})
    public Response listarPorCurso(@PathParam("courseId") UUID idCurso) {
        List<GrupoEstudio> grupos = servicio.buscarPorCurso(idCurso);
        return Response.ok(grupos.stream().map(g -> {
            List<MiembroGrupo> miembros = repo.buscarMiembrosActivos(g.getId());
            boolean esMiembro = miembros.stream()
                    .anyMatch(m -> m.getIdUsuario().toString()
                            .equals(usuarioActual.obtenerIdUsuario()));
            boolean esTutor = miembros.stream()
                    .anyMatch(m -> m.esTutor()
                            && m.getIdUsuario().toString()
                               .equals(usuarioActual.obtenerIdUsuario()));
            Map<String, Object> m = new HashMap<>();
            m.put("id",          g.getId().toString());
            m.put("name",        g.getNombre());
            m.put("courseId",    g.getIdCurso().toString());
            m.put("maxMembers",  g.getMaxMiembros());
            m.put("memberCount", miembros.size());
            m.put("isMember",    esMiembro);
            m.put("isTutor",     esTutor);
            return m;
        }).toList()).build();
    }

    /**
     * Obtiene los detalles de un grupo de estudio, incluyendo la lista de miembros.
     *
     * @param idGrupo UUID del grupo
     * @return respuesta 200 con los datos del grupo y la lista de miembros
     * @throws NotFoundException si el grupo no existe o está eliminado
     */
    @GET
    @Path("/{id}")
    @RequiereRol({Rol.STUDENT, Rol.INSTRUCTOR, Rol.ADMIN})
    public Response obtenerGrupo(@PathParam("id") UUID idGrupo) {
        GrupoEstudio grupo = repo.buscarPorId(idGrupo)
                .orElseThrow(() -> new NotFoundException("Grupo no encontrado."));
        List<MiembroGrupo> miembros = repo.buscarMiembrosActivos(idGrupo);
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("id",         grupo.getId().toString());
        resultado.put("name",       grupo.getNombre());
        resultado.put("courseId",   grupo.getIdCurso().toString());
        resultado.put("maxMembers", grupo.getMaxMiembros());
        resultado.put("members", miembros.stream().map(m -> {
            Map<String, Object> rm = new HashMap<>();
            rm.put("userId",   m.getIdUsuario().toString());
            rm.put("isTutor",  m.esTutor());
            rm.put("joinedAt", m.getUnidoEn().toString());
            return rm;
        }).toList());
        return Response.ok(resultado).build();
    }

    /**
     * Crea un nuevo grupo de estudio en un curso.
     *
     * <p>El creador se añade automáticamente como primer miembro y tutor.</p>
     *
     * @param req nombre del grupo, curso y capacidad máxima
     * @return respuesta 201 con el UUID y nombre del grupo creado
     */
    @POST
    @RequiereRol({Rol.STUDENT, Rol.INSTRUCTOR})
    public Response crear(SolicitudCrearGrupo req) {
        GrupoEstudio grupo = servicio.crear(
                req.nombre(), req.idCurso(),
                req.maxMiembros() > 0 ? req.maxMiembros() : 10);
        return Response.status(Response.Status.CREATED)
                .entity(Map.of(
                        "id",   grupo.getId().toString(),
                        "name", grupo.getNombre()))
                .build();
    }

    /**
     * Elimina un grupo de estudio de forma lógica.
     *
     * @param idGrupo UUID del grupo a eliminar
     * @return respuesta 204 sin contenido
     * @throws NotFoundException si el grupo no existe o está eliminado
     */
    @DELETE
    @Path("/{id}")
    @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN})
    @Transactional
    public Response eliminar(@PathParam("id") UUID idGrupo) {
        GrupoEstudio grupo = repo.buscarPorId(idGrupo)
                .orElseThrow(() -> new NotFoundException("Grupo no encontrado."));
        grupo.eliminarLogicamente();
        repo.guardar(grupo);
        return Response.noContent().build();
    }

    /**
     * Une al estudiante autenticado al grupo de estudio.
     *
     * @param idGrupo UUID del grupo al que unirse
     * @return respuesta 200 con mensaje de confirmación
     */
    @POST
    @Path("/{id}/join")
    @RequiereRol({Rol.STUDENT})
    public Response unirse(@PathParam("id") UUID idGrupo) {
        servicio.unirse(idGrupo);
        return Response.ok(Map.of("mensaje", "Te has unido al grupo.")).build();
    }

    /**
     * Retira al estudiante autenticado del grupo de estudio.
     *
     * @param idGrupo UUID del grupo a abandonar
     * @return respuesta 200 con mensaje de confirmación
     */
    @POST
    @Path("/{id}/leave")
    @RequiereRol({Rol.STUDENT})
    public Response abandonar(@PathParam("id") UUID idGrupo) {
        servicio.abandonar(idGrupo);
        return Response.ok(Map.of("mensaje", "Has salido del grupo.")).build();
    }

    /**
     * Retorna el historial de mensajes de chat del grupo.
     *
     * <p>Solo los miembros activos del grupo pueden consultar los mensajes.</p>
     *
     * @param idGrupo UUID del grupo
     * @param limite  número máximo de mensajes a retornar (por defecto 50, máx. 100)
     * @return respuesta 200 con la lista de mensajes ordenados cronológicamente
     * @throws ForbiddenException si el usuario no es miembro del grupo
     */
    @GET
    @Path("/{id}/messages")
    @RequiereRol({Rol.STUDENT, Rol.INSTRUCTOR, Rol.ADMIN})
    public Response obtenerMensajes(
            @PathParam("id") UUID idGrupo,
            @QueryParam("limit") @DefaultValue("50") int limite) {

        if (!repo.esMiembro(idGrupo,
                UUID.fromString(usuarioActual.obtenerIdUsuario()))) {
            throw new ForbiddenException(
                    "Solo los miembros del grupo pueden ver los mensajes.");
        }
        List<MensajeChat> mensajes =
                repo.buscarMensajesRecientes(idGrupo, Math.min(limite, 100));
        return Response.ok(mensajes.stream().map(m -> Map.of(
                "id",          m.getId().toString(),
                "authorId",    m.getIdAutor().toString(),
                "emailAuthor", m.getEmailAutor(),
                "content",     m.getContenido(),
                "sentAt",      m.getEnviadoEn().toString()
        )).toList()).build();
    }

    /**
     * Envía un mensaje de chat al grupo y lo persiste en base de datos.
     *
     * <p>Solo los miembros activos del grupo pueden enviar mensajes.</p>
     *
     * @param idGrupo UUID del grupo al que se envía el mensaje
     * @param req     contenido del mensaje
     * @return respuesta 201 con los datos del mensaje persistido
     * @throws ForbiddenException si el usuario no es miembro del grupo
     */
    @POST
    @Path("/{id}/messages")
    @RequiereRol({Rol.STUDENT, Rol.INSTRUCTOR, Rol.ADMIN})
    @Transactional
    public Response enviarMensaje(
            @PathParam("id") UUID idGrupo,
            SolicitudEnviarMensaje req) {

        if (!repo.esMiembro(idGrupo,
                UUID.fromString(usuarioActual.obtenerIdUsuario()))) {
            throw new ForbiddenException(
                    "Solo los miembros del grupo pueden enviar mensajes.");
        }
        MensajeChat mensaje = new MensajeChat();
        mensaje.setIdGrupo(idGrupo);
        mensaje.setIdAutor(UUID.fromString(usuarioActual.obtenerIdUsuario()));
        mensaje.setEmailAutor(
                usuarioActual.obtenerCorreo() != null
                        ? usuarioActual.obtenerCorreo() : "unknown");
        mensaje.setContenido(req.contenido());
        repo.guardarMensaje(mensaje);
        return Response.status(Response.Status.CREATED).entity(Map.of(
                "id",          mensaje.getId().toString(),
                "emailAuthor", mensaje.getEmailAutor(),
                "content",     mensaje.getContenido(),
                "sentAt",      mensaje.getEnviadoEn().toString()
        )).build();
    }
}
