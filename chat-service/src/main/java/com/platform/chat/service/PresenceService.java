package com.platform.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceService {

    private final StringRedisTemplate redisTemplate;

    private String presenceKey(String roomId) {
        return "presence:" + roomId;
    }

    public void userJoined(String roomId, String username) {
        redisTemplate.opsForSet().add(presenceKey(roomId), username);
        log.info("User {} joined room {}", username, roomId);
    }

    public void userLeft(String roomId, String username) {
        redisTemplate.opsForSet().remove(presenceKey(roomId), username);
        log.info("User {} left room {}", username, roomId);
    }

    public Set<String> getOnlineUsers(String roomId) {
        return redisTemplate.opsForSet().members(presenceKey(roomId));
    }

    public long getOnlineCount(String roomId) {
        Long count = redisTemplate.opsForSet().size(presenceKey(roomId));
        return count != null ? count : 0;
    }
}
