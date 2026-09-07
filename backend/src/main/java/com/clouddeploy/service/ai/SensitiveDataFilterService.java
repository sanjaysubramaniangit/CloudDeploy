package com.clouddeploy.service.ai;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Service to sanitize and scrub sensitive credentials, secrets, and private PII
 * from resume text before transmitting to external AI providers.
 * 
 * Preserves normal candidate data (contact details, phone numbers, emails,
 * education, companies, skills, project links) while redacting API keys,
 * passwords, private keys, SSNs, and credit card numbers.
 */
@Service
public class SensitiveDataFilterService {

    // Common API Key and Token Patterns
    private static final Pattern OPENAI_KEY_PATTERN = Pattern.compile("\\b(sk-[a-zA-Z0-9_-]{20,})\\b");
    private static final Pattern GITHUB_TOKEN_PATTERN = Pattern.compile("\\b(gh[pousr][_-][a-zA-Z0-9]{20,})\\b");
    private static final Pattern AWS_KEY_PATTERN = Pattern.compile("\\b(AKIA[0-9A-Z]{16})\\b");
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile("(?i)\\b(Bearer\\s+)([A-Za-z0-9\\-_=]+\\.[A-Za-z0-9\\-_=]+\\.?[A-Za-z0-9\\-_.+/=]*)");
    private static final Pattern ASSIGNED_API_KEY_PATTERN = Pattern.compile("(?i)\\b((?:api[_-]?key|access[_-]?token|secret[_-]?key|client[_-]?secret)\\s*[:=]\\s*)['\"]?([a-zA-Z0-9_\\-\\.]{10,})['\"]?");
    
    // Passwords and Secrets
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(?i)\\b((?:password|passwd|pwd|auth_secret)\\s*[:=]\\s*)['\"]?(\\S{4,})['\"]?");
    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----");

    // High-Risk PII (SSN & Credit Cards)
    private static final Pattern SSN_PATTERN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|6(?:011|5[0-9]{2})[0-9]{12}|(?:\\d{4}[ -]){3}\\d{4})\\b");

    /**
     * Filters sensitive tokens and credentials from the provided resume text.
     *
     * @param text Raw extracted text
     * @return Sanitized text with credentials and sensitive PII redacted
     */
    public String filterSensitiveData(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;

        // Redact Private Keys
        result = PRIVATE_KEY_PATTERN.matcher(result).replaceAll("[REDACTED_PRIVATE_KEY]");

        // Redact Known API Keys and Tokens
        result = OPENAI_KEY_PATTERN.matcher(result).replaceAll("[REDACTED_API_KEY]");
        result = GITHUB_TOKEN_PATTERN.matcher(result).replaceAll("[REDACTED_GITHUB_TOKEN]");
        result = AWS_KEY_PATTERN.matcher(result).replaceAll("[REDACTED_AWS_KEY]");
        result = BEARER_TOKEN_PATTERN.matcher(result).replaceAll("$1[REDACTED_BEARER_TOKEN]");
        result = ASSIGNED_API_KEY_PATTERN.matcher(result).replaceAll("$1[REDACTED_SECRET]");

        // Redact Passwords
        result = PASSWORD_PATTERN.matcher(result).replaceAll("$1[REDACTED_PASSWORD]");

        // Redact SSN & Credit Cards
        result = SSN_PATTERN.matcher(result).replaceAll("[REDACTED_SSN]");
        result = CREDIT_CARD_PATTERN.matcher(result).replaceAll("[REDACTED_PAYMENT_CARD]");

        return result;
    }
}
