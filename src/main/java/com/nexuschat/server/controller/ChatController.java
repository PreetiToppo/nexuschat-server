package com.nexuschat.server.controller;

import com.nexuschat.server.dto.ChatMessage;
import com.nexuschat.server.model.Message;
import com.nexuschat.server.repository.MessageRepository;
import com.nexuschat.server.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageRepository messageRepository;
    private final ChatService chatService;

    // Client sends to:      /app/chat.send/{channelId}
    // ChatService publishes to: Redis chat:{channelId}
    // RedisSubscriber pushes to: /topic/channel/{channelId}
    @MessageMapping("/chat.send/{channelId}")
    public void sendMessage(
            @DestinationVariable String channelId,
            @Payload ChatMessage chatMessage) {
        log.info("🎯 WebSocket received message for channel: {}", channelId);
        chatService.processAndBroadcast(channelId, chatMessage);
    }

    // Typing indicator — ephemeral, skip Redis
    @MessageMapping("/chat.typing/{channelId}")
    public void typing(
            @DestinationVariable String channelId,
            @Payload ChatMessage chatMessage) {
        chatMessage.setEventType(ChatMessage.EventType.TYPING);
        messagingTemplate.convertAndSend(
                "/topic/channel/" + channelId, chatMessage
        );
    }

    // Load chat history
    @GetMapping("/api/channels/{channelId}/messages")
    public List<Message> getMessages(
            @PathVariable String channelId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String before) {

        PageRequest page = PageRequest.of(0, limit);
        if (before != null) {
            return messageRepository
                    .findByChannelIdAndIdLessThanOrderByCreatedAtDesc(
                            channelId, before, page);
        }
        return messageRepository
                .findByChannelIdOrderByCreatedAtDesc(channelId, page);
    }
}