package com.nexuschat.server.controller;

import com.nexuschat.server.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
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
    // Destination: /app/presence.heartbeat
    @MessageMapping("/presence.heartbeat")
    public void heartbeat(@Payload Map<String, String> payload) {
        String userId = payload.get("userId");
        if (userId != null) {
            presenceService.heartbeat(userId);
        }
    }

    // Client joins a channel
    // Destination: /app/presence.join
    @MessageMapping("/presence.join")
    public void join(@Payload Map<String, String> payload) {
        String userId   = payload.get("userId");
        String username = payload.get("username");
        String channelId = payload.get("channelId");

        if (userId != null && username != null) {
            presenceService.userOnline(userId, username);
        }
        if (channelId != null && userId != null) {
            presenceService.joinChannel(channelId, userId);
        }
    }

    // Client leaves a channel
    // Destination: /app/presence.leave
    @MessageMapping("/presence.leave")
    public void leave(@Payload Map<String, String> payload) {
        String userId    = payload.get("userId");
        String username  = payload.get("username");
        String channelId = payload.get("channelId");

        if (userId != null && username != null) {
            presenceService.userOffline(userId, username);
        }
        if (channelId != null && userId != null) {
            presenceService.leaveChannel(channelId, userId);
        }
    }

    // REST — get online users in a channel
    // GET /api/channels/{channelId}/presence
    @GetMapping("/api/channels/{channelId}/presence")
    public Map<String, String> getPresence(
            @PathVariable String channelId) {
        return presenceService.getOnlineUsersInChannel(channelId);
    }

    // REST — check if specific user is online
    // GET /api/presence/{userId}
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
