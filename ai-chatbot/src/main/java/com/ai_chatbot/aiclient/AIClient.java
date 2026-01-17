package com.ai_chatbot.aiclient;

import com.ai_chatbot.entity.ChatMessage;
import com.ai_chatbot.entity.Conversation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface AIClient {

    Flux<String> streamChat(List<ChatMessage> messageList);
    public Mono<String> chat(Conversation conv, String userMessage, String model, boolean useRag);
    public Flux<String> streamChat(Conversation conv, String userMessage, String model);
}
