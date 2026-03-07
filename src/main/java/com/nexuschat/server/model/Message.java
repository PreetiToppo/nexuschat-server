package com.nexuschat.server.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "messages")
public class Message {

    @Id
    private String id;

    @Indexed
    private String channelId;

    private String senderId;
    private String senderUsername;
    private String content;

    @Builder.Default
    private MessageType type = MessageType.TEXT;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private boolean deleted = false;

    public enum MessageType {
        TEXT, IMAGE, FILE, SYSTEM
    }
}