package com.nexuschat.server.config;

import com.nexuschat.server.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                        message, StompHeaderAccessor.class);

                if (accessor != null &&
                        StompCommand.CONNECT.equals(accessor.getCommand())) {

                    String userId   = accessor.getFirstNativeHeader("userId");
                    String username = accessor.getFirstNativeHeader("username");
                    String token    = accessor.getFirstNativeHeader("Authorization");

                    // ── 1. Token must be present ───────────────────────
                    if (token == null || !token.startsWith("Bearer ")) {
                        throw new MessageDeliveryException(
                                "Missing or malformed Authorization header");
                    }

                    String jwt = token.substring(7); // strip "Bearer "

                    // ── 2. Token must be valid (not expired, not tampered) ─
                    if (!jwtService.isTokenValid(jwt)) {
                        throw new MessageDeliveryException(
                                "Invalid or expired JWT token");
                    }

                    // ── 3. userId in header must match token subject ───
                    String tokenUserId = jwtService.extractUserId(jwt);
                    if (userId == null || !tokenUserId.equals(userId)) {
                        throw new MessageDeliveryException(
                                "UserId mismatch — possible spoofing attempt");
                    }

                    // ── 4. Store in session (same as before) ──────────
                    Map<String, Object> attrs = accessor.getSessionAttributes();
                    if (attrs != null && username != null) {
                        attrs.put("userId", userId);
                        attrs.put("username", username);
                    }
                }

                return message;
            }
        });
    }
}