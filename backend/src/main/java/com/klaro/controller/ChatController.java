package com.klaro.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.klaro.dto.ChatReply;
import com.klaro.dto.ChatRequest;
import com.klaro.service.ChatService;

import jakarta.validation.Valid;

/**
 * POST /api/chat  {"question": "...", "documentId": "receipt-12", "sessionId": "...", "language": "en"}
 *              -> {"reply": "...", "sessionId": "...", "degraded": false}
 * Can take up to about 25 seconds while the AI answers.
 * On failure: {"error": "readable message", "code": "AI_CHAT_REPLY_TIMEOUT", "retryable": true}
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @PostMapping
    public ChatReply ask(@Valid @RequestBody ChatRequest request) {
        return chat.ask(request);
    }
}
