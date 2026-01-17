// java
package com.ai_chatbot.aiclient.implementation;

import com.ai_chatbot.aiclient.AIClient;
import com.ai_chatbot.entity.ChatMessage;
import com.ai_chatbot.entity.Conversation;
import com.ai_chatbot.entity.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OllamaClient implements AIClient {

    private final WebClient webClient;
    @Value("${ai.provider.chat-model}")
    private String defaultModel;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Flux<String> streamChat(List<ChatMessage> messageList) {
        Map<String, Object> body = Map.of(
                "model", defaultModel,
                "stream", true,
                "messages", messageList.stream()
                        .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                        .collect(Collectors.toList())
        );
        return webClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(this::parseStreamLine);
    }

    // sync chat
    @Override
    public Mono<String> chat(Conversation conv, String userMessage, String model, boolean useRag) {

        List<Map<String, String>> messages = new ArrayList<>();

        for (var m : conv.getMessages()) {
            messages.add(Map.of(
                    "role", m.getRole(),
                    "content", m.getContent()
            ));
        }
        messages.add(Map.of(
                "role", "user",
                "content", userMessage
        ));

        Map<String, Object> body = new HashMap<>();
        body.put("model", model != null ? model : defaultModel);
        body.put("messages", messages);
        body.put("stream", false);

        return webClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(),
                        resp -> resp.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(b -> new RuntimeException("Ollama returned " + resp.statusCode() + " : " + b)))
                .bodyToMono(String.class)
                .map(this::extractContent);
    }

    private String extractContent(String rawJson) {
        if (rawJson == null) return "";
        try {
            JsonNode json = mapper.readTree(rawJson);
            JsonNode choices = json.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).path("message");
                if (message.has("content")) {
                    String content = message.path("content").asText(null);
                    if (content != null && !content.isBlank()) return content;
                }
            }
            JsonNode msg = json.path("message").path("content");
            if (!msg.isMissingNode() && msg.isTextual()) return msg.asText();
            JsonNode text = json.path("text");
            if (text.isTextual()) return text.asText();
            return rawJson;
        } catch (Exception e) {
            return rawJson;
        }
    }

    // streaming chat
    @Override
    public Flux<String> streamChat(Conversation conv, String userMessage, String model) {
        List<Map<String, String>> messages = conv.getMessages().stream()
                .map(m -> Map.of(
                        "role", m.getRole(),
                        "content", m.getContent()
                ))
                .toList();
        List<Map<String, String>> mutableList = new ArrayList<>(messages);
        mutableList.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> body = Map.of(
                "model", model != null ? model : defaultModel,
                "messages", mutableList,
                "stream", true
        );

        return webClient.post()
                .uri("/api/chat")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(JsonNode.class)
                .map(json -> json.path("message").path("content").asText())
                .filter(text -> !text.isBlank());
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
