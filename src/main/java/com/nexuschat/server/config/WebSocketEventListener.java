package com.nexuschat.server.config;

import com.nexuschat.server.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final PresenceService presenceService;

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        log.info("🔌 WebSocket connected: session={}", sessionId);
        // Presence is set when client sends /app/presence.join
        // after connecting — we just log here
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        // Get userId from session attributes
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs != null) {
            String userId   = (String) attrs.get("userId");
            String username = (String) attrs.get("username");
            if (userId != null && username != null) {
                presenceService.userOffline(userId, username);
                log.info("🔌 WebSocket disconnected: {} went offline",
                        username);
            }
        }
        log.info("🔌 WebSocket disconnected: session={}", sessionId);
    }
}