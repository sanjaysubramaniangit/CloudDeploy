package com.clouddeploy.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Mock AI Provider for testing and local development without external API costs or credentials.
 */
@Component("mockAIProvider")
public class MockAIProvider implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger( MockAIProvider.class);

    @Value("${ai.model:mock-model}")
    private String model;

    @Value("${ai.provider:}")
    private String configuredProvider;

    private String simulatedResponse;
    private boolean simulatedFailure = false;

    @Override
    public String generateCompletion(String systemPrompt, String userPrompt) {
        log.info("MockAIProvider generating completion for prompt length: {}", userPrompt != null ? userPrompt.length() : 0);

        if (simulatedFailure) {
            throw new RuntimeException("Simulated AI Provider Failure");
        }

        if (simulatedResponse != null) {
            return simulatedResponse;
        }

        // Return a realistic default structured JSON response
        return """
        {
          "summary": "Experienced Full-Stack and Cloud Engineer with deep expertise in Java, Spring Boot, AWS, and modern DevOps pipelines. Proven track record of architecting scalable microservices.",
          "technicalSkills": ["Java", "Spring Boot", "AWS", "Docker", "Kubernetes", "MySQL", "REST APIs", "Microservices", "CI/CD"],
          "programmingLanguages": ["Java", "Python", "TypeScript", "SQL"],
          "frameworks": ["Spring Boot", "Spring Cloud", "React", "Node.js"],
          "cloudTechnologies": ["AWS S3", "AWS EC2", "AWS Lambda", "AWS RDS"],
          "databases": ["MySQL", "PostgreSQL", "Redis"],
          "devopsTools": ["Docker", "Kubernetes", "Terraform", "GitHub Actions"],
          "experienceHighlights": [
            "Architected and deployed cloud-native web platforms supporting 100k+ monthly active users",
            "Designed automated CI/CD deployment pipelines reducing release cycles by 40%",
            "Engineered secure S3-backed storage integration with pre-signed authorization"
          ],
          "strengths": [
            "Deep architectural knowledge of distributed systems and Spring ecosystem",
            "Strong security posture and least-privilege cloud IAM implementation",
            "Full lifecycle experience from system design to production deployment"
          ],
          "areasToImprove": [
            "Hands-on experience with service mesh technologies (e.g. Istio)",
            "Advanced telemetry and distributed tracing instrumentation"
          ],
          "recommendedSkills": [
            "OpenTelemetry / Distributed Tracing",
            "Apache Kafka event-driven architectures",
            "AWS Certified Solutions Architect"
          ]
        }
        """;
    }

    @Override
    public boolean isConfigured() {
        return "mock".equalsIgnoreCase(configuredProvider);
    }

    @Override
    public String getProviderName() {
        return "mock";
    }

    @Override
    public String getModelName() {
        return model != null ? model : "mock-model";
    }

    public void setSimulatedResponse(String simulatedResponse) {
        this.simulatedResponse = simulatedResponse;
    }

    public void setSimulatedFailure(boolean simulatedFailure) {
        this.simulatedFailure = simulatedFailure;
    }

    public void reset() {
        this.simulatedResponse = null;
        this.simulatedFailure = false;
    }
}
