package com.nexuschat.server.dto;

import com.nexuschat.server.model.Message;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    private String id;
    private String channelId;
    private String senderId;
    private String senderUsername;
    private String content;
    private Message.MessageType type;
    private Instant createdAt;
    private EventType eventType;

    public enum EventType {
        MESSAGE,
        TYPING,
        JOIN,
        LEAVE
    }
}