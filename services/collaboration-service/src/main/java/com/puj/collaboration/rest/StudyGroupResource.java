package com.puj.collaboration.rest;

import com.puj.collaboration.entity.ChatMessage;
import com.puj.collaboration.entity.GroupMember;
import com.puj.collaboration.entity.StudyGroup;
import com.puj.collaboration.repository.StudyGroupRepository;
import com.puj.collaboration.service.StudyGroupService;
import com.puj.security.rbac.AuthenticatedUser;
import com.puj.security.rbac.RequiresRole;
import com.puj.security.rbac.Role;
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
public class StudyGroupResource {

    @Inject private StudyGroupService    service;
    @Inject private StudyGroupRepository repo;
    @Inject private AuthenticatedUser    currentUser;

    /**
     * DTO para la creación de un grupo de estudio.
     */
    public record CreateGroupRequest(
            /** Nombre del grupo. No puede estar en blanco. */
            @NotBlank String name,

            /** Identificador del curso al que pertenece el grupo. */
            UUID courseId,

            /** Número máximo de miembros. Se usa 10 si es 0 o negativo. */
            int maxMembers
    ) {}

    /**
     * DTO para enviar un mensaje de chat al grupo.
     */
    public record SendMessageRequest(
            /** Contenido del mensaje. No puede estar en blanco. */
            @NotBlank String content
    ) {}

    /**
     * Lista los grupos de estudio activos de un curso con información de membresía
     * del usuario autenticado.
     *
     * @param courseId UUID del curso
     * @return respuesta 200 con la lista de grupos y datos de membresía del usuario
     */
    @GET
    @Path("/courses/{courseId}")
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR, Role.ADMIN})
    public Response listByCourse(@PathParam("courseId") UUID courseId) {
        List<StudyGroup> groups = service.findByCourse(courseId);
        return Response.ok(groups.stream().map(g -> {
            List<GroupMember> members = repo.findActiveMembers(g.getId());
            boolean isMember = members.stream()
                    .anyMatch(m -> m.getUserId().toString()
                            .equals(currentUser.getUserId()));
            boolean isTutor = members.stream()
                    .anyMatch(m -> m.isTutor()
                            && m.getUserId().toString()
                               .equals(currentUser.getUserId()));
            Map<String, Object> m = new HashMap<>();
            m.put("id",          g.getId().toString());
            m.put("name",        g.getName());
            m.put("courseId",    g.getCourseId().toString());
            m.put("maxMembers",  g.getMaxMembers());
            m.put("memberCount", members.size());
            m.put("isMember",    isMember);
            m.put("isTutor",     isTutor);
            return m;
        }).toList()).build();
    }

    /**
     * Obtiene los detalles de un grupo de estudio, incluyendo la lista de miembros.
     *
     * @param groupId UUID del grupo
     * @return respuesta 200 con los datos del grupo y la lista de miembros
     * @throws NotFoundException si el grupo no existe o está eliminado
     */
    @GET
    @Path("/{id}")
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR, Role.ADMIN})
    public Response getGroup(@PathParam("id") UUID groupId) {
        StudyGroup g = repo.findById(groupId)
                .orElseThrow(() -> new NotFoundException("Grupo no encontrado."));
        List<GroupMember> members = repo.findActiveMembers(groupId);
        Map<String, Object> result = new HashMap<>();
        result.put("id",         g.getId().toString());
        result.put("name",       g.getName());
        result.put("courseId",   g.getCourseId().toString());
        result.put("maxMembers", g.getMaxMembers());
        result.put("members", members.stream().map(m -> {
            Map<String, Object> rm = new HashMap<>();
            rm.put("userId",   m.getUserId().toString());
            rm.put("isTutor",  m.isTutor());
            rm.put("joinedAt", m.getJoinedAt().toString());
            return rm;
        }).toList());
        return Response.ok(result).build();
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
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR})
    public Response create(CreateGroupRequest req) {
        StudyGroup g = service.create(
                req.name(), req.courseId(),
                req.maxMembers() > 0 ? req.maxMembers() : 10);
        return Response.status(Response.Status.CREATED)
                .entity(Map.of(
                        "id",   g.getId().toString(),
                        "name", g.getName()))
                .build();
    }

    /**
     * Elimina un grupo de estudio de forma lógica.
     *
     * @param groupId UUID del grupo a eliminar
     * @return respuesta 204 sin contenido
     * @throws NotFoundException si el grupo no existe o está eliminado
     */
    @DELETE
    @Path("/{id}")
    @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
    @Transactional
    public Response delete(@PathParam("id") UUID groupId) {
        StudyGroup g = repo.findById(groupId)
                .orElseThrow(() -> new NotFoundException("Grupo no encontrado."));
        g.softDelete();
        repo.save(g);
        return Response.noContent().build();
    }

    /**
     * Une al estudiante autenticado al grupo de estudio.
     *
     * <p>Si el estudiante tuvo membresía previa, la restaura en lugar de crear una
     * nueva (evita violar la restricción única).</p>
     *
     * @param groupId UUID del grupo al que unirse
     * @return respuesta 200 con mensaje de confirmación
     */
    @POST
    @Path("/{id}/join")
    @RequiresRole({Role.STUDENT})
    public Response join(@PathParam("id") UUID groupId) {
        service.join(groupId);
        return Response.ok(Map.of("message", "Te has unido al grupo.")).build();
    }

    /**
     * Retira al estudiante autenticado del grupo de estudio.
     *
     * @param groupId UUID del grupo a abandonar
     * @return respuesta 200 con mensaje de confirmación
     */
    @POST
    @Path("/{id}/leave")
    @RequiresRole({Role.STUDENT})
    public Response leave(@PathParam("id") UUID groupId) {
        service.leave(groupId);
        return Response.ok(Map.of("message", "Has salido del grupo.")).build();
    }

    /**
     * Retorna el historial de mensajes de chat del grupo.
     *
     * <p>Solo los miembros activos del grupo pueden consultar los mensajes.</p>
     *
     * @param groupId UUID del grupo
     * @param limit   número máximo de mensajes a retornar (por defecto 50, máx. 100)
     * @return respuesta 200 con la lista de mensajes ordenados cronológicamente
     * @throws ForbiddenException si el usuario no es miembro del grupo
     */
    @GET
    @Path("/{id}/messages")
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR, Role.ADMIN})
    public Response getMessages(
            @PathParam("id") UUID groupId,
            @QueryParam("limit") @DefaultValue("50") int limit) {

        if (!repo.isMember(groupId, UUID.fromString(currentUser.getUserId()))) {
            throw new ForbiddenException(
                    "Solo los miembros del grupo pueden ver los mensajes.");
        }
        List<ChatMessage> messages =
                repo.findRecentMessages(groupId, Math.min(limit, 100));
        return Response.ok(messages.stream().map(m -> Map.of(
                "id",          m.getId().toString(),
                "authorId",    m.getAuthorId().toString(),
                "authorEmail", m.getAuthorEmail(),
                "content",     m.getContent(),
                "sentAt",      m.getSentAt().toString()
        )).toList()).build();
    }

    /**
     * Envía un mensaje de chat al grupo y lo persiste en base de datos.
     *
     * <p>Solo los miembros activos del grupo pueden enviar mensajes.
     * El mensaje también se propaga en tiempo real a través de Redis Pub/Sub
     * desde el endpoint WebSocket.</p>
     *
     * @param groupId UUID del grupo al que se envía el mensaje
     * @param req     contenido del mensaje
     * @return respuesta 201 con los datos del mensaje persistido
     * @throws ForbiddenException si el usuario no es miembro del grupo
     */
    @POST
    @Path("/{id}/messages")
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR, Role.ADMIN})
    @Transactional
    public Response sendMessage(
            @PathParam("id") UUID groupId,
            SendMessageRequest req) {

        if (!repo.isMember(groupId, UUID.fromString(currentUser.getUserId()))) {
            throw new ForbiddenException(
                    "Solo los miembros del grupo pueden enviar mensajes.");
        }
        ChatMessage msg = new ChatMessage();
        msg.setGroupId(groupId);
        msg.setAuthorId(UUID.fromString(currentUser.getUserId()));
        msg.setAuthorEmail(
                currentUser.getEmail() != null ? currentUser.getEmail() : "unknown");
        msg.setContent(req.content());
        repo.saveMessage(msg);
        return Response.status(Response.Status.CREATED).entity(Map.of(
                "id",          msg.getId().toString(),
                "authorEmail", msg.getAuthorEmail(),
                "content",     msg.getContent(),
                "sentAt",      msg.getSentAt().toString()
        )).build();
    }
}
