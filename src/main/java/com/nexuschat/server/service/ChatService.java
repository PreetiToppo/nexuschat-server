package com.nexuschat.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexuschat.server.dto.ChatMessage;
import com.nexuschat.server.model.Message;
import com.nexuschat.server.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    private static final int MAX_MESSAGE_LENGTH = 2000;          // ← added

    public ChatMessage processAndBroadcast(
            String channelId, ChatMessage incomingMessage) {

        log.info("📨 processAndBroadcast called for channel: {}", channelId);
        log.info("📨 Message content: {}", incomingMessage.getContent());

        // ── 1. Sanitize content ────────────────────────────────────────
        String safeContent = sanitize(incomingMessage.getContent());

        // ── 2. Validate content ────────────────────────────────────────
        if (safeContent.isEmpty()) {
            log.warn("⚠️ Empty message rejected for channel: {}", channelId);
            throw new IllegalArgumentException("Message cannot be empty");
        }
        if (safeContent.length() > MAX_MESSAGE_LENGTH) {
            log.warn("⚠️ Message too long ({} chars) rejected for channel: {}",
                    safeContent.length(), channelId);
            throw new IllegalArgumentException(
                    "Message exceeds maximum length of " + MAX_MESSAGE_LENGTH + " characters");
        }

        // ── 3. Save to MongoDB ─────────────────────────────────────────
        Message saved = messageRepository.save(
                Message.builder()
                        .channelId(channelId)
                        .senderId(incomingMessage.getSenderId())
                        .senderUsername(incomingMessage.getSenderUsername())
                        .content(safeContent)                    // ← use safeContent
                        .type(Message.MessageType.TEXT)
                        .build()
        );
        log.info("✅ Saved to MongoDB with id: {}", saved.getId());

        // ── 4. Build response ──────────────────────────────────────────
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

        // ── 5. Publish to Redis ────────────────────────────────────────
        try {
            String json = objectMapper.writeValueAsString(response);
            log.info("📤 About to publish to Redis channel [chat:{}]: {}", channelId, json);

            Long receivers = redisTemplate.convertAndSend("chat:" + channelId, response);
            log.info("📤 Redis publish result (num receivers): {}", receivers);

        } catch (Exception e) {
            log.error("❌ Redis publish failed: {}", e.getMessage(), e);
        }

        return response;
    }

    // ── Sanitizer ──────────────────────────────────────────────────────────
    private String sanitize(String input) {
        if (input == null) return "";
        return input
                .replaceAll("<[^>]*>", "")   // strip HTML tags e.g. <script>
                .replaceAll("&[^;]+;", "")   // strip HTML entities e.g. &lt; &#x27;
                .trim();
    }
}