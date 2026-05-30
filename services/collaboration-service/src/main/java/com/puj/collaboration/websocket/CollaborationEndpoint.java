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
 * Endpoint WebSocket para mensajería en tiempo real dentro de grupos de estudio.
 *
 * <p>Ruta de conexión: {@code wss://host/ws/groups/{groupId}?token=<JWT>}</p>
 *
 * <p>El JWT se valida en la conexión ({@link #onOpen}); si el token es inválido
 * la sesión se cierra con código 1008 (Policy Violation).</p>
 *
 * <p>Escalado horizontal: los mensajes CHAT se publican en Redis Pub/Sub
 * ({@link WsPubSubService}) para que todos los pods de la aplicación
 * entreguen el mensaje a sus sesiones locales, independientemente de a qué
 * pod esté conectado el cliente emisor.</p>
 *
 * <p>Los mensajes CHAT también se persisten en base de datos para el historial
 * accesible a través del endpoint REST {@code GET /groups/{id}/messages}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ServerEndpoint(
        value    = "/ws/groups/{groupId}",
        encoders = { WsMessageEncoder.class },
        decoders = { WsMessageDecoder.class }
)
@ApplicationScoped
public class CollaborationEndpoint {

    private static final Logger LOG =
            Logger.getLogger(CollaborationEndpoint.class.getName());

    @Inject private JwtProvider          jwtProvider;
    @Inject private WsPubSubService      pubSubService;
    @Inject private StudyGroupRepository groupRepo;

    /**
     * Maneja la apertura de una nueva conexión WebSocket.
     *
     * <p>Valida el JWT proporcionado como query param {@code token}.
     * Si la validación falla, cierra la sesión con código 1008.</p>
     *
     * @param session sesión WebSocket recién abierta
     * @param groupId identificador del grupo de estudio extraído de la URL
     */
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
            LOG.warning("WebSocket: token inválido — cerrando sesión "
                    + session.getId());
            closeWithError(session, 1008, "Token inválido");
            return;
        }

        pubSubService.registerSession(groupId, session);
        LOG.fine("WS conectado: session=" + session.getId()
                + " group=" + groupId);
    }

    /**
     * Procesa un mensaje WebSocket entrante del cliente.
     *
     * <p>Para mensajes de tipo {@code CHAT}, persiste el contenido en base de datos
     * antes de publicarlo en Redis Pub/Sub. Los mensajes {@code PING} se reenvían
     * sin persistencia.</p>
     *
     * @param session sesión WebSocket del remitente
     * @param message mensaje recibido y decodificado por {@link WsMessageDecoder}
     * @param groupId identificador del grupo de estudio extraído de la URL
     */
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

        if (message.type() == WsMessage.Type.CHAT && message.content() != null) {
            persistChatMessage(groupId, userId, email, message.content());
        }

        WsMessage.Type outType = message.type() == WsMessage.Type.PING
                ? WsMessage.Type.PING : WsMessage.Type.CHAT;
        WsMessage outgoing = new WsMessage(
                UUID.randomUUID().toString(),
                outType,
                groupId,
                userId,
                message.content(),
                Instant.now()
        );

        pubSubService.publish(groupId, outgoing);
    }

    /**
     * Maneja el cierre de una sesión WebSocket.
     *
     * <p>Elimina la sesión del mapa de sesiones locales gestionado por
     * {@link WsPubSubService}.</p>
     *
     * @param session sesión WebSocket que se cerró
     * @param groupId identificador del grupo de estudio extraído de la URL
     */
    @OnClose
    public void onClose(Session session, @PathParam("groupId") String groupId) {
        pubSubService.removeSession(groupId, session);
    }

    /**
     * Maneja errores producidos en una sesión WebSocket activa.
     *
     * @param session sesión en la que ocurrió el error
     * @param t       excepción capturada
     */
    @OnError
    public void onError(Session session, Throwable t) {
        LOG.log(Level.WARNING,
                "WebSocket error en sesión: " + session.getId(), t);
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * Persiste un mensaje de chat en la base de datos para el historial del grupo.
     *
     * @param groupId  identificador del grupo como {@code String}
     * @param userId   identificador del autor como {@code String}
     * @param email    email del autor (puede ser {@code null})
     * @param content  texto del mensaje
     */
    private void persistChatMessage(
            String groupId, String userId, String email, String content) {

        ChatMessage record = new ChatMessage();
        record.setGroupId(UUID.fromString(groupId));
        record.setAuthorId(UUID.fromString(userId));
        record.setAuthorEmail(email != null ? email : userId);
        record.setContent(content);
        groupRepo.saveMessage(record);
    }

    /**
     * Cierra la sesión WebSocket con el código y motivo indicados.
     *
     * @param session sesión a cerrar
     * @param code    código de cierre WebSocket (e.g., 1008 Policy Violation)
     * @param reason  descripción legible del motivo de cierre
     */
    private void closeWithError(Session session, int code, String reason) {
        try {
            session.close(new CloseReason(
                    CloseReason.CloseCodes.getCloseCode(code), reason));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Error cerrando WebSocket", e);
        }
    }
}
