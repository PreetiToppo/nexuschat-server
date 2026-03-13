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

    private static final int    PRESENCE_TTL_SECONDS = 30;
    private static final String PRESENCE_KEY         = "presence:";

    // Called when user connects via WebSocket
    public void userOnline(String userId, String username) {
        redisTemplate.opsForValue().set(
                PRESENCE_KEY + userId,
                username,
                PRESENCE_TTL_SECONDS,
                TimeUnit.SECONDS
        );
        log.info("🟢 {} ({}) is ONLINE", username, userId);
        broadcastPresence(userId, username, "ONLINE");
    }

    // Called every 20s by client heartbeat
    public void heartbeat(String userId) {
        Boolean exists = redisTemplate.hasKey(PRESENCE_KEY + userId);
        if (Boolean.TRUE.equals(exists)) {
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

    // ── Get ALL online users globally ──────────────────────────────────────
    public Map<String, String> getAllOnlineUsers() {
        Set<String> keys = redisTemplate.keys(PRESENCE_KEY + "*");
        Map<String, String> onlineUsers = new HashMap<>();

        if (keys == null || keys.isEmpty()) return onlineUsers;

        for (String key : keys) {
            String userId   = key.replace(PRESENCE_KEY, "");
            Object username = redisTemplate.opsForValue().get(key);

            if (username == null) continue;

            String uname = username.toString();

            // ── Skip corrupted entries ─────────────────────────────────
            if (uname.isBlank()) continue;
            if (uname.equalsIgnoreCase("ONLINE"))  continue;
            if (uname.equalsIgnoreCase("OFFLINE")) continue;
            if (userId.equalsIgnoreCase("online"))  continue;
            if (userId.equalsIgnoreCase("offline")) continue;

            onlineUsers.put(userId, uname);
        }
        return onlineUsers;
    }

    // ── Get online users in a specific channel (kept for future use) ───────
    public Map<String, String> getOnlineUsersInChannel(String channelId) {
        Set<Object> userIds = redisTemplate.opsForSet()
                .members("channel:users:" + channelId);

        Map<String, String> onlineUsers = new HashMap<>();
        if (userIds == null) return onlineUsers;

        for (Object userIdObj : userIds) {
            String userId   = userIdObj.toString();
            Object username = redisTemplate.opsForValue()
                    .get(PRESENCE_KEY + userId);
            if (username != null) {
                onlineUsers.put(userId, username.toString());
            }
        }
        return onlineUsers;
    }

    // Broadcast presence change to all subscribers
    private void broadcastPresence(
            String userId, String username, String status) {
        Map<String, String> event = new HashMap<>();
        event.put("userId",   userId);
        event.put("username", username);
        event.put("status",   status);
        messagingTemplate.convertAndSend("/topic/presence", event);
    }
}