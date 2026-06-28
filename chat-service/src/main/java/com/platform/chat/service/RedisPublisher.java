package com.platform.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.chat.dto.ChatMessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisPublisher {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public void publish(String channel, ChatMessageDto message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(channel, json);
            log.debug("Published message to Redis channel: {}", channel);
        } catch (Exception e) {
            log.error("Failed to publish message to Redis channel: {}", channel, e);
        }
    }
}
