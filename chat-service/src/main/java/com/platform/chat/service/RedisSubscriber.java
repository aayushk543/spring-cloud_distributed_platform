package com.platform.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.chat.dto.ChatMessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody());
            ChatMessageDto chatMessage = objectMapper.readValue(body, ChatMessageDto.class);

            // Forward to WebSocket subscribers of this room
            String destination = "/topic/room/" + chatMessage.getRoomId();
            messagingTemplate.convertAndSend(destination, chatMessage);

            log.debug("Forwarded Redis message to WebSocket: {}", destination);
        } catch (Exception e) {
            log.error("Failed to process Redis message", e);
        }
    }
}
