package com.puj.collaboration.rest;

import com.puj.collaboration.entity.Notification;
import com.puj.security.rbac.AuthenticatedUser;
import com.puj.security.rbac.RequiresRole;
import com.puj.security.rbac.Role;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Notificaciones")
public class NotificationResource {

    @PersistenceContext(unitName = "CollaborationServicePU")
    private EntityManager em;

    @Inject private AuthenticatedUser authenticatedUser;

    @GET
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR, Role.ADMIN, Role.DIRECTOR})
    @Operation(summary = "Mis notificaciones (más recientes primero)")
    public Response list(
            @QueryParam("unreadOnly") @DefaultValue("false") boolean unreadOnly,
            @QueryParam("size")       @DefaultValue("20")    int size) {
        UUID userId = UUID.fromString(authenticatedUser.getUserId());
        String jpql = "SELECT n FROM Notification n WHERE n.userId = :uid"
                + (unreadOnly ? " AND n.read = false" : "")
                + " ORDER BY n.createdAt DESC";
        List<Notification> notifs = em.createQuery(jpql, Notification.class)
                .setParameter("uid", userId)
                .setMaxResults(Math.min(size, 100))
                .getResultList();
        return Response.ok(notifs.stream().map(this::toMap).toList()).build();
    }

    @GET
    @Path("/unread-count")
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR, Role.ADMIN, Role.DIRECTOR})
    @Operation(summary = "Cantidad de notificaciones no leídas")
    public Response unreadCount() {
        UUID userId = UUID.fromString(authenticatedUser.getUserId());
        long count = em.createQuery(
                "SELECT COUNT(n) FROM Notification n WHERE n.userId = :uid AND n.read = false",
                Long.class)
                .setParameter("uid", userId)
                .getSingleResult();
        return Response.ok(Map.of("count", count)).build();
    }

    @PATCH
    @Path("/{id}/read")
    @Transactional
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR, Role.ADMIN, Role.DIRECTOR})
    @Operation(summary = "Marcar notificación como leída")
    public Response markRead(@PathParam("id") UUID notifId) {
        UUID userId = UUID.fromString(authenticatedUser.getUserId());
        Notification n = em.find(Notification.class, notifId);
        if (n == null || !n.getUserId().equals(userId))
            return Response.status(Response.Status.NOT_FOUND).build();
        n.markRead();
        em.merge(n);
        return Response.ok(toMap(n)).build();
    }

    @PATCH
    @Path("/read-all")
    @Transactional
    @RequiresRole({Role.STUDENT, Role.INSTRUCTOR, Role.ADMIN, Role.DIRECTOR})
    @Operation(summary = "Marcar todas como leídas")
    public Response markAllRead() {
        UUID userId = UUID.fromString(authenticatedUser.getUserId());
        em.createQuery("UPDATE Notification n SET n.read = true WHERE n.userId = :uid AND n.read = false")
                .setParameter("uid", userId)
                .executeUpdate();
        return Response.noContent().build();
    }

    private Map<String, Object> toMap(Notification n) {
        return Map.of(
                "id",          n.getId(),
                "type",        n.getType(),
                "title",       n.getTitle(),
                "body",        n.getBody() != null ? n.getBody() : "",
                "referenceId", n.getReferenceId() != null ? n.getReferenceId() : "",
                "read",        n.isRead(),
                "createdAt",   n.getCreatedAt().toString()
        );
    }
}
