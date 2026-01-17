package com.ai_chatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class EmbeddingService {

    @Autowired
    private WebClient webClient;

    @Value("${ai.provider.api-key}")
    private String apiKey;
    @Value("${ai.provider.embedding-model}")
    private String embeddingModel;

    public Mono<float[]> embedText(String text){
        Map<String, Object> body = Map.of("prompt", text, "model", embeddingModel);
        return webClient.post()
                .uri("/api/embeddings")
//                .uri("/embeddings")
//                .headers(h -> h.setBearerAuth(apiKey))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> {
                    // Assumes response.choices[0].embedding is an array of numbers
//                    JsonNode embeddingNode = json.get("data").get(0).get("embedding");
                    JsonNode embeddingNode = json.get("embedding");
                    float[] emb = new float[embeddingNode.size()];
                    for (int i = 0; i < embeddingNode.size(); i++){
                        emb[i] = (float) embeddingNode.get(i).asDouble();
//                        System.err.println(emb[i]);
                    }
                    return emb;
                });
    }

}
