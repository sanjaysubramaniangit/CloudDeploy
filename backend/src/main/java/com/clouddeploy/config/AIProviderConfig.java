package com.clouddeploy.config;

import com.clouddeploy.service.ai.AIProvider;
import com.clouddeploy.service.ai.MockAIProvider;
import com.clouddeploy.service.ai.OpenAICompatibleAIProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AIProviderConfig {

    @Value("${ai.provider:}")
    private String configuredProvider;

    @Bean
    @Primary
    public AIProvider aiProvider(OpenAICompatibleAIProvider openAIProvider, MockAIProvider mockProvider) {
        if ("mock".equalsIgnoreCase(configuredProvider)) {
            return mockProvider;
        }
        return openAIProvider;
    }
}
