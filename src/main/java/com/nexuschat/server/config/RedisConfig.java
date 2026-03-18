package com.nexuschat.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexuschat.server.service.RedisMessageSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.net.URI;

@Configuration
public class RedisConfig {

    // Reads REDIS_PUBLIC_URL first, then REDIS_URL, then falls back to localhost
    @Value("${REDIS_PUBLIC_URL:${REDIS_URL:redis://localhost:6379}}")
    private String redisUrl;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        try {
            URI uri = URI.create(redisUrl);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 6379;

            RedisStandaloneConfiguration config =
                    new RedisStandaloneConfiguration(host, port);

            // Extract password from userinfo (format: user:password or :password)
            String userInfo = uri.getUserInfo();
            if (userInfo != null && !userInfo.isBlank()) {
                String password = userInfo.contains(":")
                        ? userInfo.substring(userInfo.indexOf(':') + 1)
                        : userInfo;
                if (!password.isBlank()) {
                    config.setPassword(RedisPassword.of(password));
                }
            }

            return new LettuceConnectionFactory(config);
        } catch (Exception e) {
            // Fallback to localhost if URL parsing fails
            return new LettuceConnectionFactory(
                    new RedisStandaloneConfiguration("localhost", 6379));
        }
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory());
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(
                new GenericJackson2JsonRedisSerializer(objectMapper())
        );
        template.setHashValueSerializer(
                new GenericJackson2JsonRedisSerializer(objectMapper())
        );
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public MessageListenerAdapter messageListenerAdapter(
            @Lazy RedisMessageSubscriber subscriber) {
        MessageListenerAdapter adapter =
                new MessageListenerAdapter(subscriber, "onMessage");
        adapter.afterPropertiesSet();
        return adapter;
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            @Lazy MessageListenerAdapter messageListenerAdapter) {
        RedisMessageListenerContainer container =
                new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory());
        container.addMessageListener(
                messageListenerAdapter,
                new PatternTopic("chat:*")
        );
        return container;
    }
}