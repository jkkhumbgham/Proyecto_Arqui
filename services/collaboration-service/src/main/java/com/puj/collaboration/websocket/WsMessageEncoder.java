package com.puj.collaboration.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.websocket.EncodeException;
import jakarta.websocket.Encoder;

public class WsMessageEncoder implements Encoder.Text<WsMessage> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Override
    public String encode(WsMessage message) throws EncodeException {
        try {
            return MAPPER.writeValueAsString(message);
        } catch (Exception e) {
            throw new EncodeException(message, "Error serializando WsMessage", e);
        }
    }
}
