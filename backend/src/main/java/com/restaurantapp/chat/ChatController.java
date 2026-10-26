package com.restaurantapp.chat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.restaurantapp.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat/messages")
public class ChatController {
    private final ChatService chatService;
    private final ChatRateLimiter rateLimiter;

    public ChatController(ChatService chatService, ChatRateLimiter rateLimiter) {
        this.chatService = chatService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    public ChatMessageResponse send(@Valid @RequestBody ChatMessageRequest request, HttpServletRequest httpRequest) {
        String client = httpRequest.getRemoteAddr();
        if (!rateLimiter.allow(client)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CHAT_RATE_LIMITED",
                    "Please wait a moment before asking again.");
        }
        return chatService.answer(request.restaurantId(), request.message().trim());
    }
}
