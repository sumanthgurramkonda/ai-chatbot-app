package com.ai_chatbot.service;

import com.ai_chatbot.aiclient.AIClient;
import com.ai_chatbot.aiclient.implementation.OllamaClient;
import com.ai_chatbot.aiclient.implementation.OpenAIClient;
import com.ai_chatbot.entity.Conversation;
import com.ai_chatbot.entity.Message;
import com.ai_chatbot.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RagService {

    private final VectorStoreService vectorStore;
    private final EmbeddingService embeddingService;
    private final OllamaClient aiClient;
    private final ConversationRepository conversationRepository;

    /**
     * Build a Message object from role and content.
     */
    public Message buildMessage(String role, String content) {
        Message msg = new Message();
        msg.setRole(role);
        msg.setContent(content);
        return msg;
    }

    /**
     * Answer with RAG: embed the user prompt, query vector store for top-k matches,
     * save conversation/messages, then call OpenAIClient.chat to synthesize final answer.
     */
    public Mono<String> answerWithRag(String conversationId, String userPrompt, int k, String model) {
        return embeddingService.embedText(userPrompt)
                // run blocking vector DB query on boundedElastic
                .flatMap(queryVector -> Mono.fromCallable(() -> {

                    var hits = vectorStore.queryNearest(queryVector, k);

                    vectorStore.upsertDocument(conversationId, "Last User Prompt", userPrompt, queryVector, "{}");
                    // build context
                    StringBuilder context = new StringBuilder();
                    for (var h : hits) {
                        context.append("Source: ").append(h.title()).append("\n")
                                .append(h.content()).append("\n\n");
                    }
                    String systemPrompt = "You are a helpful assistant. Use the following context to answer the user. " +
                            "Indicate the source for factual claims from the context.\n\n" + context.toString();

                    Conversation conv = conversationRepository.findById(conversationId).orElseGet(() -> {
                        Conversation c = new Conversation();
                        conversationRepository.save(c);
                        return c;
                    });

                    // ephemeral system message (we add to conv for this request; controller can choose not to persist system messages separately)
                    conv.addMessage(buildMessage("system", systemPrompt));
                    conv.addMessage(buildMessage("user", userPrompt));
                    conversationRepository.save(conv);

                    // return conv for subsequent async processing
                    return conv;
                }).subscribeOn(Schedulers.boundedElastic()))
                .flatMap(conv -> aiClient.chat(conv, userPrompt, model, true));
    }

    /**
     * Streaming version of answerWithRag: returns Flux<String> chunks from the LLM stream
     */
    public Flux<String> answerWithRagStream(String conversationId, String userPrompt, int k, String model) {
        return embeddingService.embedText(userPrompt)
                .flatMapMany(queryVector -> Mono.fromCallable(() -> {
                    var hits = vectorStore.queryNearest(queryVector, k);

                    StringBuilder context = new StringBuilder();
                    for (var h : hits) {
                        context.append("Source: ").append(h.title()).append("\n")
                                .append(h.content()).append("\n\n");
                    }

                    String systemPrompt = "You are a helpful assistant. Use the following context to answer the user. " +
                            "Indicate the source for factual claims from the context.\n\n" + context.toString();

                    Conversation conv = conversationRepository.findById(conversationId).orElseGet(() -> {
                        Conversation c = new Conversation();
                        conversationRepository.save(c);
                        return c;
                    });

                    conv.addMessage(buildMessage("system", systemPrompt));
                    conv.addMessage(buildMessage("user", userPrompt));
                    conversationRepository.save(conv);

                    return conv;
                }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(conv -> aiClient.streamChat(conv, userPrompt, model)));
    }
}
