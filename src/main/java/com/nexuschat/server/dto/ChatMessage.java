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

    private String id;              // message ID from MongoDB
    private String channelId;       // which channel
    private String senderId;        // who sent it
    private String senderUsername;  // display name
    private String content;         // message text
    private Message.MessageType type;
    private Instant createdAt;      // timestamp

    // For typing indicators
    private EventType eventType;

    public enum EventType {
        MESSAGE,   // normal chat message
        TYPING,    // user is typing...
        JOIN,      // user joined channel
        LEAVE      // user left channel
    }
}