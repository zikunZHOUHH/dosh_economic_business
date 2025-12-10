package com.example.dosh.service.chat;

import com.example.dosh.model.dto.ChatRequestDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatService {
    // 已移除 chat(ChatRequestDTO request) 方法

    SseEmitter streamChat(ChatRequestDTO request);
}
