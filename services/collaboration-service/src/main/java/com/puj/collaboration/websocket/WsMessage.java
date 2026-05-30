package com.puj.collaboration.websocket;

import java.time.Instant;

/**
 * Mensaje inmutable intercambiado a través del WebSocket de colaboración.
 *
 * <p>Se serializa/deserializa mediante {@link WsMessageEncoder} y
 * {@link WsMessageDecoder} respectivamente. El campo {@code id} es un UUID
 * generado por el servidor en el momento de retransmitir el mensaje.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public record WsMessage(
        /** UUID del mensaje generado por el servidor al retransmitir. */
        String  id,

        /** Tipo de evento del mensaje. */
        Type    type,

        /** Identificador del grupo de estudio al que pertenece el mensaje. */
        String  groupId,

        /** Identificador del usuario que envió el mensaje. */
        String  senderId,

        /** Contenido textual del mensaje. {@code null} para tipos {@code PING}. */
        String  content,

        /** Instante en que el servidor procesó y retransmitió el mensaje. */
        Instant timestamp
) {

    /**
     * Tipos de evento soportados por el protocolo WebSocket de colaboración.
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public enum Type {
        /** Mensaje de chat con contenido textual. */
        CHAT,

        /** Notificación de un nuevo miembro que se unió al grupo. */
        JOIN,

        /** Notificación de un miembro que abandonó el grupo. */
        LEAVE,

        /** Keep-alive: verifica que la conexión WebSocket sigue activa. */
        PING
    }
}
