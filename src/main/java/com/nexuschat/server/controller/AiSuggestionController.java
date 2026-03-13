package com.nexuschat.server.controller;

import com.nexuschat.server.service.AiSuggestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai")
public class AiSuggestionController {

    private final AiSuggestionService aiSuggestionService;

    /**
     * GET /api/ai/suggest/{channelId}
     *
     * Returns a Server-Sent Events stream.
     * The client receives two event types:
     *   - "token"       : individual token streamed live (for a typing effect)
     *   - "suggestions" : the final complete JSON array of 3 suggestions
     *
     * This endpoint is intentionally unauthenticated for simplicity.
     * In production you'd validate the JWT here too.
     */
    @GetMapping(
            value = "/suggest/{channelId}",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter getSuggestions(@PathVariable String channelId) {
        log.info("🤖 AI suggestion request for channel: {}", channelId);
        return aiSuggestionService.streamSuggestions(channelId);
    }
}