package com.platform.chat.dto;

import com.platform.chat.model.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {
    private String roomId;
    private String sender;
    private String content;
    private MessageType type;
    private LocalDateTime timestamp;
}
