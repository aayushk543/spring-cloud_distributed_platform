package com.platform.chat.controller;

import com.platform.chat.dto.ChatMessageDto;
import com.platform.chat.model.MessageType;
import com.platform.chat.service.ChatService;
import com.platform.chat.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    private final ChatService chatService;
    private final PresenceService presenceService;

    @MessageMapping("/chat.send/{roomId}")
    @SendTo("/topic/room/{roomId}")
    public ChatMessageDto sendMessage(@DestinationVariable String roomId, ChatMessageDto message) {
        message.setRoomId(roomId);
        message.setTimestamp(LocalDateTime.now());
        message.setType(MessageType.CHAT);
        chatService.sendMessage(message);
        return message;
    }

    @MessageMapping("/chat.join/{roomId}")
    @SendTo("/topic/room/{roomId}")
    public ChatMessageDto joinRoom(@DestinationVariable String roomId, ChatMessageDto message) {
        message.setRoomId(roomId);
        message.setTimestamp(LocalDateTime.now());
        message.setType(MessageType.JOIN);
        message.setContent(message.getSender() + " joined the room");
        presenceService.userJoined(roomId, message.getSender());
        chatService.sendMessage(message);
        return message;
    }

    @MessageMapping("/chat.leave/{roomId}")
    @SendTo("/topic/room/{roomId}")
    public ChatMessageDto leaveRoom(@DestinationVariable String roomId, ChatMessageDto message) {
        message.setRoomId(roomId);
        message.setTimestamp(LocalDateTime.now());
        message.setType(MessageType.LEAVE);
        message.setContent(message.getSender() + " left the room");
        presenceService.userLeft(roomId, message.getSender());
        chatService.sendMessage(message);
        return message;
    }
}
