package com.platform.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.chat.dto.ChatMessageDto;
import com.platform.chat.dto.RoomRequest;
import com.platform.chat.model.ChatMessage;
import com.platform.chat.model.ChatRoom;
import com.platform.chat.model.MessageType;
import com.platform.chat.repository.ChatMessageRepository;
import com.platform.chat.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatMessageRepository messageRepository;
    private final ChatRoomRepository roomRepository;
    private final RedisPublisher redisPublisher;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public ChatMessageDto sendMessage(ChatMessageDto dto) {
        if (dto.getTimestamp() == null) {
            dto.setTimestamp(LocalDateTime.now());
        }
        if (dto.getType() == null) {
            dto.setType(MessageType.CHAT);
        }

        // Persist to PostgreSQL
        ChatMessage entity = ChatMessage.builder()
                .roomId(dto.getRoomId())
                .sender(dto.getSender())
                .content(dto.getContent())
                .type(dto.getType())
                .timestamp(dto.getTimestamp())
                .build();
        messageRepository.save(entity);

        // Publish to Redis for cross-instance broadcast
        redisPublisher.publish("chat:" + dto.getRoomId(), dto);

        // Publish to Kafka for persistence/analytics
        try {
            String json = objectMapper.writeValueAsString(dto);
            kafkaTemplate.send("chat-messages", dto.getRoomId(), json);
        } catch (Exception e) {
            log.error("Failed to publish message to Kafka", e);
        }

        log.info("Message sent in room {} by {}", dto.getRoomId(), dto.getSender());
        return dto;
    }

    public Page<ChatMessage> getMessageHistory(String roomId, int page, int size) {
        return messageRepository.findByRoomIdOrderByTimestampDesc(roomId, PageRequest.of(page, size));
    }

    public ChatRoom createRoom(RoomRequest request) {
        ChatRoom room = ChatRoom.builder()
                .id(request.getId())
                .description(request.getDescription())
                .createdBy("system")
                .build();
        return roomRepository.save(room);
    }

    public List<ChatRoom> getRooms() {
        return roomRepository.findAll();
    }

    public ChatRoom getRoom(String roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found: " + roomId));
    }
}
