package com.ai_chatbot.repository;

import com.ai_chatbot.entity.ChatMessage;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatRepository {
    List<ChatMessage> findByConversationIdOrderByTimestampAsc(String conversationId);
}
