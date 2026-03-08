package com.nexuschat.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    private static final int PRESENCE_TTL_SECONDS = 30;
    private static final String PRESENCE_KEY = "presence:";
    private static final String CHANNEL_USERS_KEY = "channel:users:";

    // Called when user connects via WebSocket
    public void userOnline(String userId, String username) {
        // SET presence:{userId} = username  EX 30
        redisTemplate.opsForValue().set(
                PRESENCE_KEY + userId,
                username,
                PRESENCE_TTL_SECONDS,
                TimeUnit.SECONDS
        );

        log.info("🟢 {} ({}) is ONLINE", username, userId);

        // Broadcast presence event to everyone
        broadcastPresence(userId, username, "ONLINE");
    }

    // Called every 20s by client heartbeat
    public void heartbeat(String userId) {
        Boolean exists = redisTemplate.hasKey(PRESENCE_KEY + userId);
        if (Boolean.TRUE.equals(exists)) {
            // Reset TTL to 30s — user is still active
            redisTemplate.expire(
                    PRESENCE_KEY + userId,
                    PRESENCE_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
            log.debug("💓 Heartbeat from {}", userId);
        }
    }

    // Called when user disconnects
    public void userOffline(String userId, String username) {
        redisTemplate.delete(PRESENCE_KEY + userId);
        log.info("🔴 {} ({}) is OFFLINE", username, userId);
        broadcastPresence(userId, username, "OFFLINE");
    }

    // Check if a specific user is online
    public boolean isOnline(String userId) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(PRESENCE_KEY + userId)
        );
    }

    // Add user to a channel's active user set
    public void joinChannel(String channelId, String userId) {
        redisTemplate.opsForSet().add(
                CHANNEL_USERS_KEY + channelId, userId
        );
        redisTemplate.expire(
                CHANNEL_USERS_KEY + channelId, 1, TimeUnit.HOURS
        );
    }

    // Remove user from channel
    public void leaveChannel(String channelId, String userId) {
        redisTemplate.opsForSet().remove(
                CHANNEL_USERS_KEY + channelId, userId
        );
    }

    // Get all online users in a channel
    public Map<String, String> getOnlineUsersInChannel(String channelId) {
        Set<Object> userIds = redisTemplate.opsForSet()
                .members(CHANNEL_USERS_KEY + channelId);

        Map<String, String> onlineUsers = new HashMap<>();
        if (userIds == null) return onlineUsers;

        for (Object userIdObj : userIds) {
            String userId = userIdObj.toString();
            Object username = redisTemplate.opsForValue()
                    .get(PRESENCE_KEY + userId);
            if (username != null) {
                // User has presence key = they are online
                onlineUsers.put(userId, username.toString());
            }
        }
        return onlineUsers;
    }

    // Broadcast presence change to all subscribers
    private void broadcastPresence(
            String userId, String username, String status) {
        Map<String, String> event = new HashMap<>();
        event.put("userId", userId);
        event.put("username", username);
        event.put("status", status);

        // All clients subscribed to /topic/presence get this
        messagingTemplate.convertAndSend("/topic/presence", event);
    }
}
