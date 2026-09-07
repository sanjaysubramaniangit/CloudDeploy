package com.clouddeploy.service.ai;

public interface AIProvider {
    /**
     * Generates a completion from system instructions and user prompt.
     *
     * @param systemPrompt System level instructions
     * @param userPrompt   User message containing untrusted resume text
     * @return AI completion response string (expected raw JSON)
     */
    String generateCompletion(String systemPrompt, String userPrompt);

    /**
     * Checks whether this AI provider is properly configured with credentials and endpoints.
     *
     * @return true if configured, false otherwise
     */
    boolean isConfigured();

    /**
     * Provider identifier (e.g., "openai", "mock").
     *
     * @return provider name
     */
    String getProviderName();

    /**
     * Active model name (e.g., "gpt-4o-mini").
     *
     * @return model name
     */
    String getModelName();
}
