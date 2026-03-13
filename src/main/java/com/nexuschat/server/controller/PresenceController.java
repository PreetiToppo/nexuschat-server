package com.nexuschat.server.controller;

import com.nexuschat.server.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;

    // Client sends heartbeat every 20s
    @MessageMapping("/presence.heartbeat")
    public void heartbeat(@Payload Map<String, String> payload) {
        String userId = payload.get("userId");
        if (userId != null) {
            presenceService.heartbeat(userId);
        }
    }

    // Client joins a channel
    @MessageMapping("/presence.join")
    public void join(@Payload Map<String, String> payload) {
        String userId   = payload.get("userId");
        String username = payload.get("username");

        // ── Guard: only store if BOTH are valid strings ────────────────
        if (userId == null || userId.isBlank()) return;
        if (username == null || username.isBlank()) return;

        // ── Guard: username should never be a status word ──────────────
        if (username.equalsIgnoreCase("ONLINE") ||
                username.equalsIgnoreCase("OFFLINE") ||
                username.equalsIgnoreCase("online") ||
                username.equalsIgnoreCase("offline")) {
            log.warn("⚠️ Rejected suspicious username: {}", username);
            return;
        }

        presenceService.userOnline(userId, username);
        log.info("✅ User joined: {} ({})", username, userId);
    }

    // Client leaves a channel
    @MessageMapping("/presence.leave")
    public void leave(@Payload Map<String, String> payload) {
        String userId   = payload.get("userId");
        String username = payload.get("username");

        if (userId != null && username != null) {
            presenceService.userOffline(userId, username);
        }
    }

    // REST — get ALL online users globally ← main endpoint now
    @GetMapping("/api/presence/online")
    public Map<String, String> getAllOnlineUsers() {
        return presenceService.getAllOnlineUsers();
    }

    // REST — get online users in a specific channel (kept for future use)
    @GetMapping("/api/channels/{channelId}/presence")
    public Map<String, String> getChannelPresence(
            @PathVariable String channelId) {
        return presenceService.getOnlineUsersInChannel(channelId);
    }

    // REST — check if specific user is online
    @GetMapping("/api/presence/{userId}")
    public Map<String, Object> checkPresence(
            @PathVariable String userId) {
        boolean online = presenceService.isOnline(userId);
        return Map.of(
                "userId", userId,
                "online", online,
                "status", online ? "ONLINE" : "OFFLINE"
        );
    }
}