package com.clouddeploy.service.ai;

import com.clouddeploy.exception.AIConfigurationException;
import com.clouddeploy.exception.AIServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component("openAICompatibleAIProvider")
public class OpenAICompatibleAIProvider implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger( OpenAICompatibleAIProvider.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.provider:}")
    private String provider;

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.model:gpt-4o-mini}")
    private String model;

    @Value("${ai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${ai.timeout-seconds:30}")
    private int timeoutSeconds;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String generateCompletion(String systemPrompt, String userPrompt) {
        if (!isConfigured()) {
            throw new AIConfigurationException("AI provider is not configured. Please configure AI_PROVIDER and AI_API_KEY.");
        }

        try {
            String endpoint = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.2);

            Map<String, String> responseFormat = new HashMap<>();
            responseFormat.put("type", "json_object");
            requestBody.put("response_format", responseFormat);

            List<Map<String, String>> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
                messages.add(Map.of("role", "system", "content", systemPrompt));
            }
            messages.add(Map.of("role", "user", "content", userPrompt));
            requestBody.put("messages", messages);

            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            log.info("Dispatching completion request to AI provider [{}] at endpoint [{}]", provider, endpoint);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode choices = root.path("choices");
                if (choices.isArray() && !choices.isEmpty()) {
                    return choices.get(0).path("message").path("content").asText();
                } else {
                    throw new AIServiceException("AI provider response missing choices array: " + response.body());
                }
            } else {
                log.error("AI provider returned error status {}: {}", response.statusCode(), response.body());
                throw new AIServiceException("AI provider returned error code " + response.statusCode() + ": " + response.body());
            }
        } catch (AIConfigurationException | AIServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to communicate with AI provider: {}", e.getMessage(), e);
            throw new AIServiceException("Communication failure with AI provider: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty() &&
               provider != null && !provider.trim().isEmpty() &&
               !provider.equalsIgnoreCase("mock");
    }

    @Override
    public String getProviderName() {
        return (provider != null && !provider.trim().isEmpty()) ? provider : "unconfigured";
    }

    @Override
    public String getModelName() {
        return model != null ? model : "unknown";
    }
}
