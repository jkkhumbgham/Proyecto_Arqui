package com.puj.collaboration.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.websocket.DecodeException;
import jakarta.websocket.Decoder;

public class WsMessageDecoder implements Decoder.Text<WsMessage> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Override
    public WsMessage decode(String s) throws DecodeException {
        try {
            return MAPPER.readValue(s, WsMessage.class);
        } catch (Exception e) {
            throw new DecodeException(s, "Error deserializando WsMessage", e);
        }
    }

    @Override
    public boolean willDecode(String s) {
        return s != null && !s.isBlank();
    }
}
