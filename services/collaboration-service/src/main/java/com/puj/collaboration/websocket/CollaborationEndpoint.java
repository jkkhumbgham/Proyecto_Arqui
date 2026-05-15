package com.puj.collaboration.websocket;

import com.puj.collaboration.entity.ChatMessage;
import com.puj.collaboration.repository.StudyGroupRepository;
import com.puj.security.jwt.JwtClaims;
import com.puj.security.jwt.JwtProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * WebSocket endpoint para mensajería en tiempo real dentro de grupos de estudio.
 * Ruta: wss://host/ws/groups/{groupId}?token=<JWT>
 *
 * Escalado horizontal: los mensajes se publican en Redis Pub/Sub (WsPubSubService)
 * para que todos los pods entreguen el mensaje a sus sesiones locales,
 * independientemente de a qué pod esté conectado el cliente emisor.
 */
@ServerEndpoint(
        value = "/ws/groups/{groupId}",
        encoders = { WsMessageEncoder.class },
        decoders = { WsMessageDecoder.class }
)
@ApplicationScoped
public class CollaborationEndpoint {

    private static final Logger LOG = Logger.getLogger(CollaborationEndpoint.class.getName());

    @Inject private JwtProvider          jwtProvider;
    @Inject private WsPubSubService      pubSubService;
    @Inject private StudyGroupRepository groupRepo;

    @OnOpen
    public void onOpen(Session session, @PathParam("groupId") String groupId) {
        String token = session.getRequestParameterMap()
                .getOrDefault("token", java.util.List.of(""))
                .get(0);

        try {
            JwtClaims claims = jwtProvider.validateToken(token);
            session.getUserProperties().put("userId", claims.userId());
            session.getUserProperties().put("email",  claims.email());
            session.getUserProperties().put("role",   claims.role());
        } catch (Exception e) {
            LOG.warning("WebSocket: token inválido — cerrando sesión " + session.getId());
            closeWithError(session, 1008, "Token inválido");
            return;
        }

        pubSubService.registerSession(groupId, session);
        LOG.fine("WS conectado: session=" + session.getId() + " group=" + groupId);
    }

    @OnMessage
    @Transactional
    public void onMessage(Session session, WsMessage message,
                          @PathParam("groupId") String groupId) {
        String userId = (String) session.getUserProperties().get("userId");
        String email  = (String) session.getUserProperties().get("email");
        if (userId == null) {
            closeWithError(session, 1008, "No autenticado");
            return;
        }

        // Persist to DB for chat history (GET /groups/{id}/messages)
        if (message.type() == WsMessage.Type.CHAT && message.content() != null) {
            ChatMessage record = new ChatMessage();
            record.setGroupId(UUID.fromString(groupId));
            record.setAuthorId(UUID.fromString(userId));
            record.setAuthorEmail(email != null ? email : userId);
            record.setContent(message.content());
            groupRepo.saveMessage(record);
        }

        WsMessage outgoing = new WsMessage(
                UUID.randomUUID().toString(),
                message.type() == WsMessage.Type.PING ? WsMessage.Type.PING : WsMessage.Type.CHAT,
                groupId,
                userId,
                message.content(),
                Instant.now()
        );

        pubSubService.publish(groupId, outgoing);
    }

    @OnClose
    public void onClose(Session session, @PathParam("groupId") String groupId) {
        pubSubService.removeSession(groupId, session);
    }

    @OnError
    public void onError(Session session, Throwable t) {
        LOG.log(Level.WARNING, "WebSocket error en sesión: " + session.getId(), t);
    }

    private void closeWithError(Session session, int code, String reason) {
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.getCloseCode(code), reason));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Error cerrando WebSocket", e);
        }
    }
}
