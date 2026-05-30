package com.puj.collaboration.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.websocket.EncodeException;
import jakarta.websocket.Encoder;

/**
 * Codificador JSON para mensajes WebSocket salientes.
 *
 * <p>Convierte una instancia de {@link WsMessage} en texto JSON para enviarlo
 * al cliente conectado. Registra el módulo {@link JavaTimeModule} para soportar
 * la serialización del tipo {@code Instant} del campo {@code timestamp}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public class WsMessageEncoder implements Encoder.Text<WsMessage> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /**
     * Serializa un {@link WsMessage} a texto JSON.
     *
     * @param message instancia de {@link WsMessage} a serializar
     * @return representación JSON del mensaje
     * @throws EncodeException si el mensaje no puede ser serializado
     */
    @Override
    public String encode(WsMessage message) throws EncodeException {
        try {
            return MAPPER.writeValueAsString(message);
        } catch (Exception e) {
            throw new EncodeException(message, "Error serializando WsMessage", e);
        }
    }
}
