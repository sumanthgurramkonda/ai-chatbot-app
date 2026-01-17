package com.ai_chatbot.entity;

import lombok.Data;

@Data
public class ChatRequest {
    private String conversationId;
    private String message;
    private String model;
    private boolean useRag = false;
    private int k = 3; // number of retrieved docs
}
