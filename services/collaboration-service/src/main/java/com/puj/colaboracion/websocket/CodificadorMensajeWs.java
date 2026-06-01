package com.puj.colaboracion.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.websocket.EncodeException;
import jakarta.websocket.Encoder;

/**
 * Codificador JSON para mensajes WebSocket salientes.
 *
 * <p>Convierte una instancia de {@link MensajeWs} en texto JSON para enviarlo
 * al cliente conectado. Registra el módulo {@link JavaTimeModule} para soportar
 * la serialización del tipo {@code Instant} del campo {@code marca}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public class CodificadorMensajeWs implements Encoder.Text<MensajeWs> {

    private static final ObjectMapper MAPEADOR = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /**
     * Serializa un {@link MensajeWs} a texto JSON.
     *
     * @param mensaje instancia de {@link MensajeWs} a serializar
     * @return representación JSON del mensaje
     * @throws EncodeException si el mensaje no puede ser serializado
     */
    @Override
    public String encode(MensajeWs mensaje) throws EncodeException {
        try {
            return MAPEADOR.writeValueAsString(mensaje);
        } catch (Exception e) {
            throw new EncodeException(mensaje, "Error serializando MensajeWs", e);
        }
    }
}
