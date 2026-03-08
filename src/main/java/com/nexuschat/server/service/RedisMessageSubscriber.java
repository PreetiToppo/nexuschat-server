package com.nexuschat.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexuschat.server.dto.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RedisMessageSubscriber {

    @Lazy
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    public void onMessage(String message, String channel) {
        try {
            log.info("🔴 Redis message on [{}]: {}", channel, message);

            ChatMessage chatMessage = objectMapper.readValue(
                    message, ChatMessage.class
            );

            messagingTemplate.convertAndSend(
                    "/topic/channel/" + chatMessage.getChannelId(),
                    chatMessage
            );

            log.info("✅ Forwarded to WebSocket: /topic/channel/{}",
                    chatMessage.getChannelId());

        } catch (Exception e) {
            log.error("❌ Redis error: {}", e.getMessage(), e);
        }
    }
}