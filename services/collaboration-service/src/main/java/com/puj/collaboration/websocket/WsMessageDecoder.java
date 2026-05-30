package com.puj.collaboration.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.websocket.DecodeException;
import jakarta.websocket.Decoder;

/**
 * Decodificador JSON para mensajes WebSocket entrantes.
 *
 * <p>Convierte el texto JSON recibido del cliente en una instancia de
 * {@link WsMessage}. Registra el módulo {@link JavaTimeModule} para soportar
 * el tipo {@code Instant} del campo {@code timestamp}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public class WsMessageDecoder implements Decoder.Text<WsMessage> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /**
     * Deserializa un texto JSON en una instancia de {@link WsMessage}.
     *
     * @param s texto JSON recibido del cliente WebSocket
     * @return instancia de {@link WsMessage} deserializada
     * @throws DecodeException si el JSON no puede ser deserializado
     */
    @Override
    public WsMessage decode(String s) throws DecodeException {
        try {
            return MAPPER.readValue(s, WsMessage.class);
        } catch (Exception e) {
            throw new DecodeException(s, "Error deserializando WsMessage", e);
        }
    }

    /**
     * Determina si el texto puede ser decodificado.
     *
     * @param s texto a evaluar
     * @return {@code true} si el texto no es nulo ni está en blanco
     */
    @Override
    public boolean willDecode(String s) {
        return s != null && !s.isBlank();
    }
}
