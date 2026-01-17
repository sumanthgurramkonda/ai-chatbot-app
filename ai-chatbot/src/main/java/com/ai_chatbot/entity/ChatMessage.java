package com.ai_chatbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.Instant;

@Entity
@Data
public class ChatMessage {

    @Id
    @GeneratedValue
    private Long id;
    private String conversationId;
    private String role;
    @Column(length = 8000)
    private String content;
    private Instant timeStamp;
}
