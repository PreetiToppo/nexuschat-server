package com.nexuschat.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexuschat.server.model.Message;
import com.nexuschat.server.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiSuggestionService {

    private final MessageRepository messageRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final int  CONTEXT_MESSAGE_LIMIT = 15;
    private static final long CACHE_TTL_SECONDS      = 30;
    private static final int  MAX_CONTEXT_CHARS      = 3000;

    // Groq config — set AI_GROQ_API_KEY in your environment
    @Value("${ai.groq.api-key:}")
    private String groqApiKey;

    // llama-3.1-8b-instant is free, fast, and great for short tasks
    @Value("${ai.groq.model:llama-3.1-8b-instant}")
    private String model;

    /**
     * Returns an SseEmitter that streams 3 reply suggestions to the client.
     * Cache key: suggest:{channelId}:{lastMessageId}
     * Identical channel state -> Redis hit -> no LLM call needed.
     */
    public SseEmitter streamSuggestions(String channelId) {
        SseEmitter emitter = new SseEmitter(20_000L);

        List<Message> recentMessages = messageRepository
                .findByChannelIdOrderByCreatedAtDesc(
                        channelId, PageRequest.of(0, CONTEXT_MESSAGE_LIMIT));

        if (recentMessages.isEmpty()) {
            sendAndClose(emitter,
                    "[\"Hello everyone!\",\"What's up?\",\"Anyone here?\"]");
            return emitter;
        }

        // Cache key = last message ID (fingerprint of channel state)
        String cacheKey = "suggest:" + channelId + ":" + recentMessages.get(0).getId();

        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.info("Cache hit for {}", cacheKey);
            sendAndClose(emitter, cached.toString());
            return emitter;
        }

        String context = buildContext(recentMessages.reversed());
        String prompt  = buildPrompt(context);

        Thread.startVirtualThread(() ->
                callGroqAndStream(prompt, cacheKey, emitter));

        return emitter;
    }

    private String buildContext(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message msg : messages) {
            String line = msg.getSenderUsername() + ": " + msg.getContent() + "\n";
            if (sb.length() + line.length() > MAX_CONTEXT_CHARS) break;
            sb.append(line);
        }
        return sb.toString();
    }

    private String buildPrompt(String context) {
        return "You are a chat assistant helping a user reply in a group chat.\n"
                + "Based on the conversation below, generate exactly 3 short, natural reply suggestions.\n\n"
                + "Rules:\n"
                + "- Each suggestion must be under 12 words\n"
                + "- Vary the tone: one casual, one informative, one question\n"
                + "- Return ONLY a raw JSON array of 3 strings, no markdown, no explanation\n"
                + "- Example: [\"Sure, sounds good!\", \"I'll check that out.\", \"What time works for you?\"]\n\n"
                + "Conversation:\n"
                + context
                + "\nReply suggestions (JSON array only):";
    }

    /**
     * Calls the Groq API (OpenAI-compatible format) with streaming enabled.
     * Groq response format: choices[0].delta.content per SSE event.
     */
    private void callGroqAndStream(String prompt, String cacheKey, SseEmitter emitter) {
        try {
            if (groqApiKey == null || groqApiKey.isBlank()) {
                log.warn("No Groq API key set — returning fallback suggestions");
                sendAndClose(emitter,
                        "[\"Sounds good!\",\"Can you share more details?\",\"When do we need this?\"]");
                return;
            }

            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", 150,
                    "stream", true,
                    "messages", List.of(
                            Map.of("role", "system",
                                    "content", "You generate short JSON reply suggestions for a chat app. Return only raw JSON arrays."),
                            Map.of("role", "user", "content", prompt)
                    )
            );

            String requestBody = objectMapper.writeValueAsString(body);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + groqApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            StringBuilder fullText = new StringBuilder();

            HttpResponse<java.io.InputStream> response = client.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body()))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data: ")) continue;

                    String data = line.substring(6).trim();
                    if (data.equals("[DONE]")) break;

                    try {
                        Map<?, ?> event = objectMapper.readValue(data, Map.class);

                        // OpenAI/Groq format: choices[0].delta.content
                        List<?> choices = (List<?>) event.get("choices");
                        if (choices == null || choices.isEmpty()) continue;

                        Map<?, ?> delta = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("delta");
                        if (delta == null) continue;

                        String token = (String) delta.get("content");
                        if (token == null) continue;

                        fullText.append(token);

                        emitter.send(SseEmitter.event()
                                .name("token")
                                .data(objectMapper.writeValueAsString(
                                        Map.of("token", token))));

                    } catch (Exception ignored) { }
                }
            }

            String suggestions = fullText.toString().trim();
            log.info("Groq suggestions for {}: {}", cacheKey, suggestions);

            redisTemplate.opsForValue().set(
                    cacheKey, suggestions, CACHE_TTL_SECONDS, TimeUnit.SECONDS);

            emitter.send(SseEmitter.event().name("suggestions").data(suggestions));
            emitter.complete();

        } catch (Exception e) {
            log.error("Groq streaming error: {}", e.getMessage(), e);
            try {
                sendAndClose(emitter, "[\"Got it!\",\"Interesting point.\",\"Tell me more?\"]");
            } catch (Exception ignored) {
                emitter.completeWithError(e);
            }
        }
    }

    private void sendAndClose(SseEmitter emitter, String suggestions) {
        try {
            emitter.send(SseEmitter.event().name("suggestions").data(suggestions));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }
}