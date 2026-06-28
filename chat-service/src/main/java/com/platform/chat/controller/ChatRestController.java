package com.platform.chat.controller;

import com.platform.chat.dto.RoomRequest;
import com.platform.chat.model.ChatMessage;
import com.platform.chat.model.ChatRoom;
import com.platform.chat.service.ChatService;
import com.platform.chat.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatRestController {

    private final ChatService chatService;
    private final PresenceService presenceService;

    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoom>> getRooms() {
        return ResponseEntity.ok(chatService.getRooms());
    }

    @PostMapping("/rooms")
    public ResponseEntity<ChatRoom> createRoom(@RequestBody RoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chatService.createRoom(request));
    }

    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<ChatRoom> getRoom(@PathVariable String roomId) {
        return ResponseEntity.ok(chatService.getRoom(roomId));
    }

    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<Page<ChatMessage>> getMessageHistory(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(chatService.getMessageHistory(roomId, page, size));
    }

    @GetMapping("/rooms/{roomId}/online")
    public ResponseEntity<Set<String>> getOnlineUsers(@PathVariable String roomId) {
        return ResponseEntity.ok(presenceService.getOnlineUsers(roomId));
    }

    @GetMapping("/rooms/{roomId}/online/count")
    public ResponseEntity<Map<String, Long>> getOnlineCount(@PathVariable String roomId) {
        return ResponseEntity.ok(Map.of("count", presenceService.getOnlineCount(roomId)));
    }
}
