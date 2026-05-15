package com.puj.collaboration.websocket;

import java.time.Instant;

public record WsMessage(
        String  id,
        Type    type,
        String  groupId,
        String  senderId,
        String  content,
        Instant timestamp
) {
    public enum Type { CHAT, JOIN, LEAVE, PING }
}
