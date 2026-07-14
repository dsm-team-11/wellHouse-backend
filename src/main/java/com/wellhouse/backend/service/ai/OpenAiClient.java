package com.wellhouse.backend.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions(GPT) 호출 클라이언트.
 *
 * <p>API 키({@code wellhouse.ai.openai-api-key}, env {@code OPENAI_API_KEY})가 없으면 호출하지 않고
 * {@code null} 을 돌려준다 → 상위 {@link AiService} 가 규칙 기반 기본 문구로 폴백한다.
 */
@Slf4j
@Component
public class OpenAiClient {

    private final RestClient rest;
    private final String apiKey;
    private final String model;

    public OpenAiClient(
            @Value("${wellhouse.ai.openai-api-key:}") String apiKey,
            @Value("${wellhouse.ai.model:gpt-4o-mini}") String model,
            @Value("${wellhouse.ai.base-url:https://api.openai.com/v1}") String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.rest = RestClient.builder().baseUrl(baseUrl).build();
    }

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** 기본 파라미터(temperature 0.7, max_tokens 300)로 1회 호출. */
    public String chat(String system, String user) {
        return chat(system, user, 0.7, 300);
    }

    /**
     * system + user 프롬프트로 GPT 를 1회 호출하고 응답 텍스트를 돌려준다.
     * 미연동/오류/빈 응답이면 {@code null}.
     */
    public String chat(String system, String user, double temperature, int maxTokens) {
        if (!enabled()) return null;
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "temperature", temperature,
                    "max_tokens", maxTokens,
                    "messages", List.of(
                            Map.of("role", "system", "content", system),
                            Map.of("role", "user", "content", user)
                    )
            );
            JsonNode resp = rest.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (resp == null) return null;
            String content = resp.path("choices").path(0).path("message").path("content").asText("");
            return content.isBlank() ? null : content.trim();
        } catch (Exception e) {
            log.warn("OpenAI 호출 실패: {}", e.toString());
            return null;
        }
    }
}
