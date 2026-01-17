package com.ai_chatbot.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "conversation")
@Data
public class Conversation {
    @Id
    private String id = UUID.randomUUID().toString();

    private String model;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference
    private List<Message> messages = new ArrayList<>();

    // Convenience helper used by controller/service to add a message and keep both sides consistent
    public void addMessage(Message m) {
        if (m == null) return;
        m.setConversation(this);
        this.messages.add(m);
    }

}
