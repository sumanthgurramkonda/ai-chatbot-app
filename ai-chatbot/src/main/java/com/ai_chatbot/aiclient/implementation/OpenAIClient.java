package com.ai_chatbot.aiclient.implementation;

import com.ai_chatbot.entity.ChatMessage;
import com.ai_chatbot.entity.Conversation;
import com.ai_chatbot.aiclient.AIClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class OpenAIClient implements AIClient {

    private final WebClient webClient;
    private final String apiKey;

    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAIClient(WebClient webClient, @Value("${ai.provider.api-key}") String apiKey) {
        this.webClient = webClient;
        this.apiKey = apiKey;
    }

    // Implement AIClient's existing streamChat for List<ChatMessage>
    @Override
    public Flux<String> streamChat(List<ChatMessage> messageList) {
        Map<String, Object> body = Map.of(
                "model", "gpt-4o-mini",
                "stream", true,
                "messages", messageList.stream()
                        .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                        .collect(Collectors.toList())
        );
        return webClient.post()
                .uri("/chat/completions")
                .headers(h -> h.setBearerAuth(apiKey))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(this::parseStreamLine);
    }

    // sync chat
    @Override
    public Mono<String> chat(Conversation conv, String userMessage, String model, boolean useRag) {
        // reference useRag to avoid unused-parameter warnings; actual behavior can be expanded later
        if (!useRag) {
            // no-op; conversation already built by caller when RAG was used or not
        }

        List<Map<String, String>> messages = conv.getMessages().stream()
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .collect(Collectors.toList());
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0.2);

        return webClient.post()
                .uri("/chat/completions")
                .headers(h -> h.setBearerAuth(apiKey))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> {
                    try {
                        JsonNode choices = json.get("choices");
                        if (choices != null && choices.isArray() && !choices.isEmpty()) {
                            JsonNode message = choices.get(0).get("message");
                            if (message != null && message.get("content") != null) {
                                return message.get("content").asText();
                            }
                            if (choices.get(0).get("text") != null) {
                                return choices.get(0).get("text").asText();
                            }
                        }
                    } catch (Exception e) {
                        // fall through
                    }
                    return "";
                });
    }

    // streaming chat
    @Override
    public Flux<String> streamChat(Conversation conv, String userMessage, String model) {
        List<Map<String, String>> messages = conv.getMessages().stream()
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .collect(Collectors.toList());
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", true);

        return webClient.post()
                .uri("/chat/completions")
                .headers(h -> h.setBearerAuth(apiKey))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(this::parseStreamLine);
    }

    // crude parser for provider stream format
    private Flux<String> parseStreamLine(String line) {
        if (line == null || line.isBlank()) return Flux.empty();
        return Flux.fromArray(line.split("\\r?\\n"))
                .map(String::trim)
                .filter(l -> l.startsWith("data:"))
                .map(l -> l.substring("data:".length()).trim())
                .takeUntil(s -> s.equals("[DONE]"))
                .flatMap(s -> {
                    if (s.equals("[DONE]")) return Flux.empty();
                    try {
                        JsonNode json = mapper.readTree(s);
                        JsonNode choices = json.get("choices");
                        StringBuilder out = new StringBuilder();
                        if (choices != null && choices.isArray()) {
                            for (JsonNode choice : choices) {
                                JsonNode delta = choice.get("delta");
                                if (delta != null && delta.has("content")) {
                                    out.append(delta.get("content").asText());
                                }
                            }
                        }
                        if (!out.isEmpty()) return Flux.just(out.toString());
                    } catch (Exception e) {
                        // ignore parse issues
                    }
                    return Flux.empty();
                });
    }
}
