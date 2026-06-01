package com.puj.colaboracion.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.websocket.DecodeException;
import jakarta.websocket.Decoder;

/**
 * Decodificador JSON para mensajes WebSocket entrantes.
 *
 * <p>Convierte el texto JSON recibido del cliente en una instancia de
 * {@link MensajeWs}. Registra el módulo {@link JavaTimeModule} para soportar
 * el tipo {@code Instant} del campo {@code marca}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public class DecodificadorMensajeWs implements Decoder.Text<MensajeWs> {

    private static final ObjectMapper MAPEADOR = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /**
     * Deserializa un texto JSON en una instancia de {@link MensajeWs}.
     *
     * @param s texto JSON recibido del cliente WebSocket
     * @return instancia de {@link MensajeWs} deserializada
     * @throws DecodeException si el JSON no puede ser deserializado
     */
    @Override
    public MensajeWs decode(String s) throws DecodeException {
        try {
            return MAPEADOR.readValue(s, MensajeWs.class);
        } catch (Exception e) {
            throw new DecodeException(s, "Error deserializando MensajeWs", e);
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
