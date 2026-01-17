package com.ai_chatbot.controller;

import com.ai_chatbot.aiclient.implementation.OllamaClient;
import com.ai_chatbot.entity.ChatRequest;
import com.ai_chatbot.entity.Conversation;
import com.ai_chatbot.entity.Message;
import com.ai_chatbot.repository.ConversationRepository;
import com.ai_chatbot.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ChatController {

    private final ConversationRepository convRepo;
    private final OllamaClient aiClient;
    private final RagService ragService;

    @PostMapping("/chat")
    public Mono<ResponseEntity<Map<String, String>>> chat(@RequestBody ChatRequest req) {
        return Mono.fromCallable(() -> getOrCreateConversation(req))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(conv -> {
                    if (req.isUseRag()) {
                        return ragService.answerWithRag(conv.getId(), req.getMessage(), req.getK(), req.getModel())
                                .flatMap(answer -> persistAndBuildResponse(answer, conv))
                                .defaultIfEmpty(buildError(conv.getId(), "Empty RAG response"))
                                .onErrorResume(ex -> Mono.just(buildError(conv.getId(), ex.getMessage())));
                    } else {
                        return aiClient.chat(conv, req.getMessage(), req.getModel(), false)
                                .flatMap(answer -> persistAndBuildResponse(answer, conv))
                                .defaultIfEmpty(buildError(conv.getId(), "Empty AI response"))
                                .onErrorResume(ex -> Mono.just(buildError(conv.getId(), ex.getMessage())));
                    }
                })
                .onErrorResume(ex -> Mono.just(buildError(null, ex.getMessage())));
    }

    private Conversation getOrCreateConversation(ChatRequest req) {
        if (req.getConversationId() == null || !convRepo.existsById(req.getConversationId())) {
            Conversation conv = new Conversation();
            if (req.getModel() != null) conv.setModel(req.getModel());
            return convRepo.save(conv);
        }
        Conversation conv = convRepo.findById(req.getConversationId()).get();
        if (req.getModel() != null) conv.setModel(req.getModel());
        return conv;
    }

    private ResponseEntity<Map<String, String>> buildError(String convId, String msg) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("conversationId", convId != null ? convId : "", "message", msg));
    }

    private Mono<ResponseEntity<Map<String, String>>> persistAndBuildResponse(String answer, Conversation conv) {
        if (answer == null || answer.isBlank()) return Mono.just(buildError(conv.getId(), "Empty answer"));
        Message aiMsg = new Message();
        aiMsg.setRole("assistant");
        aiMsg.setContent(answer);
        return Mono.fromCallable(() -> {
                    conv.addMessage(aiMsg);
                    convRepo.save(conv);
                    return ResponseEntity.ok(Map.of("conversationId", conv.getId(), "message", answer));
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> Mono.just(buildError(conv.getId(), ex.getMessage())));
    }

    @GetMapping(value = "/stream/{conversationId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@PathVariable String conversationId,
                                                @RequestParam String message,
                                                @RequestParam(required = false) String model,
                                                @RequestParam(defaultValue = "false") boolean useRag) {
        Conversation conv = convRepo.findById(conversationId).orElseGet(() -> convRepo.save(new Conversation()));
        Flux<String> flux = useRag ?
                ragService.answerWithRagStream(conversationId, message, 3, model) :
                aiClient.streamChat(conv, message, model);
        return flux.map(chunk -> ServerSentEvent.builder(chunk)
                .event("message")
                .id(String.valueOf(System.currentTimeMillis()))
                .build());
    }

    @GetMapping("/conversations")
    public List<Conversation> listConversations() {
        return convRepo.findAll();
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<Conversation> get(@PathVariable String id) {
        return convRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
