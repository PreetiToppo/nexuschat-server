package com.nexuschat.server.controller;

import com.nexuschat.server.dto.ChatMessage;
import com.nexuschat.server.model.Message;
import com.nexuschat.server.repository.MessageRepository;
import com.nexuschat.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.Instant;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private Message.MessageType type;

    // ─── WebSocket endpoint ───────────────────────────────────────
    // Client sends to: /app/chat.send/{channelId}
    // Server broadcasts to: /topic/channel/{channelId}
    @MessageMapping("/chat.send/{channelId}")
    public void sendMessage(
            @DestinationVariable String channelId,
            @Payload ChatMessage chatMessage) {

        // Save to MongoDB
        Message saved = messageRepository.save(
                Message.builder()
                        .channelId(channelId)
                        .senderId(chatMessage.getSenderId())
                        .senderUsername(chatMessage.getSenderUsername())
                        .content(chatMessage.getContent())
                        .type(Message.MessageType.TEXT)
                        .build()
        );

        // Build response
        ChatMessage response = ChatMessage.builder()
                .id(saved.getId())
                .channelId(channelId)
                .senderId(saved.getSenderId())
                .senderUsername(saved.getSenderUsername())
                .content(saved.getContent())
                .type(Message.MessageType.TEXT)
                .createdAt(saved.getCreatedAt())
                .eventType(ChatMessage.EventType.MESSAGE)
                .build();

        // Broadcast to ALL users subscribed to this channel
        messagingTemplate.convertAndSend(
                "/topic/channel/" + channelId, response
        );
    }

    // ─── Typing indicator ─────────────────────────────────────────
    // Client sends to: /app/chat.typing/{channelId}
    @MessageMapping("/chat.typing/{channelId}")
    public void typing(
            @DestinationVariable String channelId,
            @Payload ChatMessage chatMessage) {

        chatMessage.setEventType(ChatMessage.EventType.TYPING);
        messagingTemplate.convertAndSend(
                "/topic/channel/" + channelId, chatMessage
        );
    }

    // ─── REST endpoint — load chat history ────────────────────────
    // GET /api/channels/{channelId}/messages
    @GetMapping("/api/channels/{channelId}/messages")
    public List<Message> getMessages(
            @PathVariable String channelId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String before) {

        PageRequest page = PageRequest.of(0, limit);

        if (before != null) {
            return messageRepository
                    .findByChannelIdAndIdLessThanOrderByCreatedAtDesc(
                            channelId, before, page
                    );
        }
        return messageRepository
                .findByChannelIdOrderByCreatedAtDesc(channelId, page);
    }
}