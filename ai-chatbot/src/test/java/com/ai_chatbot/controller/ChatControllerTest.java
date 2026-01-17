package com.ai_chatbot.controller;

import com.ai_chatbot.aiclient.implementation.OllamaClient;
import com.ai_chatbot.entity.ChatRequest;
import com.ai_chatbot.entity.Conversation;
import com.ai_chatbot.entity.Message;
import com.ai_chatbot.repository.ConversationRepository;
import com.ai_chatbot.service.RagService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@WebFluxTest(controllers = ChatControllerTest.class)
class ChatControllerTest {

    @Autowired
    private WebTestClient webClient;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private OllamaClient ollamaClient;

    @Mock
    private RagService ragService;

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private Conversation stubConversation(String id) {
        Conversation conv = new Conversation();
        conv.setId(id);
        conv.setModel("test-model");
        conv.setMessages(Collections.emptyList());
        return conv;
    }

    private ChatRequest stubChatRequest(String message, boolean useRag) {
        ChatRequest req = new ChatRequest();
        req.setMessage(message);
        req.setUseRag(useRag);
        req.setModel("test-model");
        // conversationId left null so the controller creates a new one
        return req;
    }

    // -------------------------------------------------------------------------
    // Tests for the POST /chat endpoint
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/chat")
    class PostChat {

        @Test
        @DisplayName("should respond with AI answer when RAG is disabled")
        void chatWithoutRagSuccess() {
            // arrange
            ChatRequest req = stubChatRequest("Hello", false);
            Conversation conv = stubConversation("conv-123");
            when(conversationRepository.existsById(any())).thenReturn(false);
            when(conversationRepository.save(any(Conversation.class))).thenReturn(conv);
            when(ollamaClient.chat(eq(conv), eq(req.getMessage()), eq(req.getModel()), eq(false)))
                    .thenReturn(Mono.just("AI response"));

            // act & assert
            webClient.post()
                    .uri("/api/v1/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody(Map.class)
                    .value(body -> {
                        assertThat(body).containsEntry("conversationId", conv.getId());
                        assertThat(body).containsEntry("message", "AI response");
                    });

            verify(ollamaClient, times(1)).chat(any(), anyString(), anyString(), eq(false));
            verifyNoInteractions(ragService);
        }

        @Test
        @DisplayName("should respond with RAG answer when RAG is enabled")
        void chatWithRagSuccess() {
            // arrange
            ChatRequest req = stubChatRequest("Explain RAG", true);
            Conversation conv = stubConversation("conv-456");
            when(conversationRepository.existsById(any())).thenReturn(false);
            when(conversationRepository.save(any(Conversation.class))).thenReturn(conv);
            when(ragService.answerWithRag(eq(conv.getId()), eq(req.getMessage()), eq(req.getK()), eq(req.getModel())))
                    .thenReturn(Mono.just("RAG answer"));

            // act & assert
            webClient.post()
                    .uri("/api/v1/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Map.class)
                    .value(body -> {
                        assertThat(body).containsEntry("conversationId", conv.getId());
                        assertThat(body).containsEntry("message", "RAG answer");
                    });

            verify(ragService, times(1))
                    .answerWithRag(eq(conv.getId()), eq(req.getMessage()), eq(req.getK()), eq(req.getModel()));
            verifyNoInteractions(ollamaClient);
        }

        @Test
        @DisplayName("should return 500 and error payload when service throws")
        void chatServiceError() {
            // arrange
            ChatRequest req = stubChatRequest("boom", true);
            Conversation conv = stubConversation("conv-789");
            when(conversationRepository.existsById(any())).thenReturn(false);
            when(conversationRepository.save(any(Conversation.class))).thenReturn(conv);
            when(ragService.answerWithRag(anyString(), anyString(), anyInt(), anyString()))
                    .thenReturn(Mono.error(new RuntimeException("RAG failure")));

            // act & assert
            webClient.post()
                    .uri("/api/v1/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .exchange()
                    .expectStatus().is5xxServerError()
                    .expectBody(Map.class)
                    .value(body -> {
                        assertThat(body).containsEntry("conversationId", conv.getId());
                        assertThat(body).containsEntry("message", "RAG failure");
                    });

            verify(ragService, times(1)).answerWithRag(anyString(), anyString(), anyInt(), anyString());
        }
    }

    // -------------------------------------------------------------------------
    // Tests for the streaming endpoint
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/stream/{conversationId}")
    class StreamEndpoint {

        @Test
        @DisplayName("should stream AI chunks when RAG is disabled")
        void streamWithoutRag() {
            // arrange
            String convId = "conv-stream-1";
            when(conversationRepository.findById(convId))
                    .thenReturn(Optional.of(stubConversation(convId)));
            when(ollamaClient.streamChat(any(Conversation.class), eq("msg"), anyString()))
                    .thenReturn(Flux.just("part‑1", "part‑2"));

            // act & assert
            webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/stream/{conversationId}")
                            .queryParam("message", "msg")
                            .queryParam("useRag", false)
                            .build(convId))
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                    .returnResult(String.class)
                    .getResponseBody()
                    .collectList()
                    .as(StepVerifier::create)
                    .expectNext(List.of("part‑1", "part‑2"))
                    .verifyComplete();

            verify(ollamaClient, times(1)).streamChat(any(Conversation.class), eq("msg"), anyString());
            verifyNoInteractions(ragService);
        }

        @Test
        @DisplayName("should stream RAG chunks when RAG is enabled")
        void streamWithRag() {
            // arrange
            String convId = "conv-stream-2";
            when(ragService.answerWithRagStream(eq(convId), eq("msg"), anyInt(), anyString()))
                    .thenReturn(Flux.just("rag‑chunk‑a", "rag‑chunk‑b"));

            // act & assert
            webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/stream/{conversationId}")
                            .queryParam("message", "msg")
                            .queryParam("useRag", true)
                            .build(convId))
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .exchange()
                    .expectStatus().isOk()
                    .returnResult(String.class)
                    .getResponseBody()
                    .collectList()
                    .as(StepVerifier::create)
                    .expectNext(List.of("rag‑chunk‑a", "rag‑chunk‑b"))
                    .verifyComplete();

            verify(ragService, times(1)).answerWithRagStream(eq(convId), eq("msg"), anyInt(), anyString());
            verifyNoInteractions(ollamaClient);
        }
    }

    // -------------------------------------------------------------------------
    // Tests for the conversation‑list / get endpoints
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Conversation retrieval")
    class ConversationEndpoints {

        @Test
        @DisplayName("should return list of all conversations")
        void listConversations() {
            // arrange
            Conversation c1 = stubConversation("c1");
            Conversation c2 = stubConversation("c2");
            when(conversationRepository.findAll()).thenReturn(List.of(c1, c2));

            // act & assert
            webClient.get()
                    .uri("/api/v1/conversations")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(Conversation.class)
                    .value(list -> {
                        assertThat(list).hasSize(2)
                                .extracting(Conversation::getId)
                                .containsExactlyInAnyOrder("c1", "c2");
                    });

            verify(conversationRepository, times(1)).findAll();
        }

        @Test
        @DisplayName("should return conversation when it exists")
        void getConversationFound() {
            // arrange
            Conversation conv = stubConversation("found-id");
            when(conversationRepository.findById("found-id")).thenReturn(Optional.of(conv));

            // act & assert
            webClient.get()
                    .uri("/api/v1/conversations/{id}", "found-id")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Conversation.class)
                    .isEqualTo(conv);
        }

        @Test
        @DisplayName("should return 404 when conversation does not exist")
        void getConversationNotFound() {
            // arrange
            when(conversationRepository.findById("missing-id")).thenReturn(Optional.empty());

            // act & assert
            webClient.get()
                    .uri("/api/v1/conversations/{id}", "missing-id")
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }
}