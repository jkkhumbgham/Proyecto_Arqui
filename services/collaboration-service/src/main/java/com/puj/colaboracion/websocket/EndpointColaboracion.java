package com.puj.colaboracion.websocket;

import com.puj.colaboracion.gruposestudio.dominio.MensajeChat;
import com.puj.colaboracion.gruposestudio.dominio.RepositorioGruposEstudio;
import com.puj.seguridad.jwt.ReclamosJwt;
import com.puj.seguridad.jwt.ProveedorJwt;
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
 * <p>Ruta de conexión: {@code wss://host/ws/groups/{idGrupo}?token=<JWT>}</p>
 *
 * <p>El JWT se valida en la conexión ({@link #alConectar}); si el token es inválido
 * la sesión se cierra con código 1008 (Policy Violation).</p>
 *
 * <p>Escalado horizontal: los mensajes CHAT se publican en Redis Pub/Sub
 * ({@link ServicioPubSubWs}) para que todos los pods de la aplicación
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
        encoders = { CodificadorMensajeWs.class },
        decoders = { DecodificadorMensajeWs.class }
)
@ApplicationScoped
public class EndpointColaboracion {

    private static final Logger LOG =
            Logger.getLogger(EndpointColaboracion.class.getName());

    @Inject private ProveedorJwt             proveedorJwt;
    @Inject private ServicioPubSubWs        servicioPubSub;
    @Inject private RepositorioGruposEstudio repoGrupos;

    /**
     * Maneja la apertura de una nueva conexión WebSocket.
     *
     * <p>Valida el JWT proporcionado como query param {@code token}.
     * Si la validación falla, cierra la sesión con código 1008.</p>
     *
     * @param sesion  sesión WebSocket recién abierta
     * @param idGrupo identificador del grupo de estudio extraído de la URL
     */
    @OnOpen
    public void alConectar(Session sesion, @PathParam("groupId") String idGrupo) {
        String token = sesion.getRequestParameterMap()
                .getOrDefault("token", java.util.List.of(""))
                .get(0);

        try {
            ReclamosJwt claims = proveedorJwt.validarToken(token);
            sesion.getUserProperties().put("idUsuario", claims.idUsuario());
            sesion.getUserProperties().put("email",     claims.correo());
            sesion.getUserProperties().put("rol",       claims.rol());
        } catch (Exception e) {
            LOG.warning("WebSocket: token inválido — cerrando sesión "
                    + sesion.getId());
            cerrarConError(sesion, 1008, "Token inválido");
            return;
        }

        servicioPubSub.suscribir(idGrupo, sesion);
        LOG.fine("WS conectado: sesion=" + sesion.getId()
                + " grupo=" + idGrupo);
    }

    /**
     * Procesa un mensaje WebSocket entrante del cliente.
     *
     * <p>Para mensajes de tipo {@code CHAT}, persiste el contenido en base de datos
     * antes de publicarlo en Redis Pub/Sub. Los mensajes {@code PING} se reenvían
     * sin persistencia.</p>
     *
     * @param sesion  sesión WebSocket del remitente
     * @param mensaje mensaje recibido y decodificado por {@link DecodificadorMensajeWs}
     * @param idGrupo identificador del grupo de estudio extraído de la URL
     */
    @OnMessage
    @Transactional
    public void alRecibir(Session sesion, MensajeWs mensaje,
                          @PathParam("groupId") String idGrupo) {
        String idUsuario = (String) sesion.getUserProperties().get("idUsuario");
        String email     = (String) sesion.getUserProperties().get("email");
        if (idUsuario == null) {
            cerrarConError(sesion, 1008, "No autenticado");
            return;
        }

        if (mensaje.tipo() == MensajeWs.Tipo.CHAT && mensaje.contenido() != null) {
            persistirMensajeChat(idGrupo, idUsuario, email, mensaje.contenido());
        }

        MensajeWs.Tipo tipoSalida = mensaje.tipo() == MensajeWs.Tipo.PING
                ? MensajeWs.Tipo.PING : MensajeWs.Tipo.CHAT;
        MensajeWs saliente = new MensajeWs(
                UUID.randomUUID().toString(),
                tipoSalida,
                idGrupo,
                idUsuario,
                mensaje.contenido(),
                Instant.now()
        );

        servicioPubSub.publicar(idGrupo, saliente);
    }

    /**
     * Maneja el cierre de una sesión WebSocket.
     *
     * @param sesion  sesión WebSocket que se cerró
     * @param idGrupo identificador del grupo de estudio extraído de la URL
     */
    @OnClose
    public void alDesconectar(Session sesion, @PathParam("groupId") String idGrupo) {
        servicioPubSub.desuscribir(idGrupo, sesion);
    }

    /**
     * Maneja errores producidos en una sesión WebSocket activa.
     *
     * @param sesion sesión en la que ocurrió el error
     * @param t      excepción capturada
     */
    @OnError
    public void alError(Session sesion, Throwable t) {
        LOG.log(Level.WARNING,
                "WebSocket error en sesión: " + sesion.getId(), t);
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private void persistirMensajeChat(
            String idGrupo, String idUsuario, String email, String contenido) {

        MensajeChat registro = new MensajeChat();
        registro.setIdGrupo(UUID.fromString(idGrupo));
        registro.setIdAutor(UUID.fromString(idUsuario));
        registro.setEmailAutor(email != null ? email : idUsuario);
        registro.setContenido(contenido);
        repoGrupos.guardarMensaje(registro);
    }

    private void cerrarConError(Session sesion, int codigo, String motivo) {
        try {
            sesion.close(new CloseReason(
                    CloseReason.CloseCodes.getCloseCode(codigo), motivo));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Error cerrando WebSocket", e);
        }
    }
}
