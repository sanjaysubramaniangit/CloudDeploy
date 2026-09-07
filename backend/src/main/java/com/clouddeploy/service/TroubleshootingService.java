package com.clouddeploy.service;

import com.clouddeploy.dto.*;
import com.clouddeploy.entity.*;
import com.clouddeploy.exception.AccessDeniedCustomException;
import com.clouddeploy.exception.AIConfigurationException;
import com.clouddeploy.exception.AIServiceException;
import com.clouddeploy.exception.BadRequestException;
import com.clouddeploy.exception.ResourceNotFoundException;
import com.clouddeploy.repository.*;
import com.clouddeploy.service.ai.AIProvider;
import com.clouddeploy.service.ai.SensitiveDataFilterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TroubleshootingService {

    private static final Logger log = LoggerFactory.getLogger(TroubleshootingService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final TroubleshootingSessionRepository sessionRepository;
    private final TroubleshootingMessageRepository messageRepository;
    private final ApplicationRepository applicationRepository;
    private final DeploymentRepository deploymentRepository;
    private final UserRepository userRepository;
    private final AIInteractionRepository aiInteractionRepository;
    private final AIProvider aiProvider;
    private final SensitiveDataFilterService sensitiveDataFilterService;
    private final ResourceLoader resourceLoader;

    private String systemPrompt;

    public TroubleshootingService(TroubleshootingSessionRepository sessionRepository,
                                  TroubleshootingMessageRepository messageRepository,
                                  ApplicationRepository applicationRepository,
                                  DeploymentRepository deploymentRepository,
                                  UserRepository userRepository,
                                  AIInteractionRepository aiInteractionRepository,
                                  AIProvider aiProvider,
                                  SensitiveDataFilterService sensitiveDataFilterService,
                                  ResourceLoader resourceLoader) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.applicationRepository = applicationRepository;
        this.deploymentRepository = deploymentRepository;
        this.userRepository = userRepository;
        this.aiInteractionRepository = aiInteractionRepository;
        this.aiProvider = aiProvider;
        this.sensitiveDataFilterService = sensitiveDataFilterService;
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void loadPromptTemplate() {
        try {
            Resource resource = resourceLoader.getResource("classpath:prompts/troubleshooting-assistant.txt");
            try (InputStream is = resource.getInputStream()) {
                this.systemPrompt = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                log.info("Successfully loaded troubleshooting assistant prompt template");
            }
        } catch (Exception e) {
            log.error("Failed to load troubleshooting assistant prompt template, using default fallback", e);
            this.systemPrompt = "You are a Senior Cloud SRE AI Troubleshooting Assistant. Provide structured diagnostic advice in raw JSON. Commands are advisory text only and never executed.";
        }
    }

    public TroubleshootingResponse processChatTurn(TroubleshootingPromptRequest request, String userEmail) {
        User user = getUserByEmail(userEmail);

        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            throw new BadRequestException("Query cannot be blank");
        }

        TroubleshootingSession session = null;
        Application application = null;
        Deployment deployment = null;

        // Session Continuation vs New Session
        if (request.getSessionId() != null) {
            session = sessionRepository.findById(request.getSessionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Troubleshooting session not found with id: " + request.getSessionId()));

            // Multi-tenant check
            if (user.getRole() != Role.ADMIN && !session.getUser().getId().equals(user.getId())) {
                throw new AccessDeniedCustomException("You do not have permission to access this troubleshooting session");
            }

            // Verify immutable session context
            if (request.getApplicationId() != null) {
                if (session.getApplication() != null && !session.getApplication().getId().equals(request.getApplicationId())) {
                    throw new BadRequestException("Cannot change application context for an existing session");
                }
                if (session.getApplication() == null) {
                    Application requestedApp = applicationRepository.findById(request.getApplicationId())
                            .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + request.getApplicationId()));
                    if (user.getRole() != Role.ADMIN && !requestedApp.getOwner().getId().equals(user.getId())) {
                        throw new AccessDeniedCustomException("You do not have permission to access this application");
                    }
                    throw new BadRequestException("Cannot add application context to a general session");
                }
            }

            if (request.getDeploymentId() != null) {
                if (session.getDeployment() != null && !session.getDeployment().getId().equals(request.getDeploymentId())) {
                    throw new BadRequestException("Cannot change deployment context for an existing session");
                }
                if (session.getDeployment() == null) {
                    Deployment requestedDep = deploymentRepository.findById(request.getDeploymentId())
                            .orElseThrow(() -> new ResourceNotFoundException("Deployment not found with id: " + request.getDeploymentId()));
                    if (user.getRole() != Role.ADMIN && !requestedDep.getApplication().getOwner().getId().equals(user.getId())) {
                        throw new AccessDeniedCustomException("You do not have permission to access this deployment");
                    }
                    throw new BadRequestException("Cannot add deployment context to a general session");
                }
            }

            application = session.getApplication();
            deployment = session.getDeployment();

        } else {
            // New Session: Validate Application and Deployment
            if (request.getApplicationId() != null) {
                application = applicationRepository.findById(request.getApplicationId())
                        .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + request.getApplicationId()));

                if (user.getRole() != Role.ADMIN && !application.getOwner().getId().equals(user.getId())) {
                    throw new AccessDeniedCustomException("You do not have permission to access this application");
                }
            }

            if (request.getDeploymentId() != null) {
                deployment = deploymentRepository.findById(request.getDeploymentId())
                        .orElseThrow(() -> new ResourceNotFoundException("Deployment not found with id: " + request.getDeploymentId()));

                if (user.getRole() != Role.ADMIN && !deployment.getApplication().getOwner().getId().equals(user.getId())) {
                    throw new AccessDeniedCustomException("You do not have permission to access this deployment");
                }

                // Cross-resource association validation
                if (application != null && !deployment.getApplication().getId().equals(application.getId())) {
                    throw new BadRequestException("Deployment does not belong to the specified application");
                }

                if (application == null) {
                    application = deployment.getApplication();
                }
            }
        }

        // Sensitive data scrubbing
        String scrubbedQuery = sensitiveDataFilterService.filterSensitiveData(request.getQuery().trim());
        String scrubbedLogs = (request.getLogSnippet() != null && !request.getLogSnippet().trim().isEmpty())
                ? sensitiveDataFilterService.filterSensitiveData(request.getLogSnippet().trim())
                : "";

        // AI Provider configuration check (controlled 503 fallback)
        if (!aiProvider.isConfigured()) {
            log.warn("Troubleshooting assistant invoked but AI provider is unconfigured");
            throw new AIConfigurationException(
                    "AI cloud troubleshooting assistant is unavailable because the AI provider is not configured. Configure the AI provider to enable cloud troubleshooting assistance."
            );
        }

        // Context assembly
        String appContextStr = buildApplicationContextString(application);
        String depContextStr = buildDeploymentContextString(application, deployment);
        String historyContextStr = buildConversationHistoryString(session);

        String userPrompt = """
                <<<APPLICATION CONTEXT>>>
                %s
                <<<END APPLICATION CONTEXT>>>

                <<<DEPLOYMENT CONTEXT>>>
                %s
                <<<END DEPLOYMENT CONTEXT>>>

                <<<CONVERSATION HISTORY>>>
                %s
                <<<END CONVERSATION HISTORY>>>

                <<<LOG SNIPPET>>>
                %s
                <<<END LOG SNIPPET>>>

                <<<USER QUERY>>>
                %s
                <<<END USER QUERY>>>
                """.formatted(appContextStr, depContextStr, historyContextStr, scrubbedLogs, scrubbedQuery);

        TroubleshootingAIOutputDto outputDto;
        try {
            String rawOutput = aiProvider.generateCompletion(systemPrompt, userPrompt);
            if (rawOutput == null || rawOutput.trim().isEmpty()) {
                throw new AIServiceException("AI provider returned empty response");
            }

            String cleanedJson = cleanJsonOutput(rawOutput);
            outputDto = objectMapper.readValue(cleanedJson, TroubleshootingAIOutputDto.class);

            if (outputDto == null || !outputDto.isValid()) {
                recordInteraction(user, "FAILED", "AI output failed schema or command validation");
                throw new AIServiceException("AI provider output failed schema validation: malformed structure or invalid fields");
            }

        } catch (AIConfigurationException e) {
            throw e;
        } catch (AIServiceException e) {
            recordInteraction(user, "FAILED", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("AI troubleshooting completion error: {}", e.getMessage(), e);
            recordInteraction(user, "FAILED", e.getMessage());
            throw new AIServiceException("Failed to process troubleshooting assistant response: " + e.getMessage());
        }

        return saveSessionAndMessages(user, application, deployment, session, scrubbedQuery, scrubbedLogs, outputDto);
    }

    @Transactional
    public TroubleshootingResponse saveSessionAndMessages(User user,
                                                           Application application,
                                                           Deployment deployment,
                                                           TroubleshootingSession session,
                                                           String scrubbedQuery,
                                                           String scrubbedLogs,
                                                           TroubleshootingAIOutputDto outputDto) {
        // Deterministic title for new session
        if (session == null) {
            String title;
            if (application != null) {
                String subQuery = scrubbedQuery.length() > 50 ? scrubbedQuery.substring(0, 50) + "..." : scrubbedQuery;
                title = "[" + application.getName() + "] " + subQuery;
            } else {
                title = scrubbedQuery.length() > 60 ? scrubbedQuery.substring(0, 60) + "..." : scrubbedQuery;
            }

            session = TroubleshootingSession.builder()
                    .user(user)
                    .application(application)
                    .deployment(deployment)
                    .title(title)
                    .build();
            session = sessionRepository.save(session);
        }

        // Save User Message
        String userMsgContent = scrubbedQuery + (scrubbedLogs.isEmpty() ? "" : "\n\n[Attached Diagnostic Logs]\n" + scrubbedLogs);
        TroubleshootingMessage userMsg = TroubleshootingMessage.builder()
                .session(session)
                .role(TroubleshootingMessageRole.USER)
                .content(userMsgContent)
                .build();
        messageRepository.save(userMsg);

        // Save Assistant Message
        TroubleshootingSeverity severity = TroubleshootingSeverity.fromString(outputDto.getSeverity());
        TroubleshootingMessage assistantMsg = TroubleshootingMessage.builder()
                .session(session)
                .role(TroubleshootingMessageRole.ASSISTANT)
                .content(outputDto.getContent())
                .severity(severity)
                .rootCause(outputDto.getRootCause())
                .remediationSteps(outputDto.getRemediationSteps() != null ? outputDto.getRemediationSteps() : new ArrayList<>())
                .suggestedCommands(outputDto.getSuggestedCommands() != null ? outputDto.getSuggestedCommands() : new ArrayList<>())
                .build();
        assistantMsg = messageRepository.save(assistantMsg);

        // Update session timestamp
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);

        // Audit log success
        recordInteraction(user, "SUCCESS", null);

        return TroubleshootingResponse.builder()
                .sessionId(session.getId())
                .sessionTitle(session.getTitle())
                .applicationId(session.getApplication() != null ? session.getApplication().getId() : null)
                .applicationName(session.getApplication() != null ? session.getApplication().getName() : null)
                .deploymentId(session.getDeployment() != null ? session.getDeployment().getId() : null)
                .message(mapToMessageDto(assistantMsg))
                .build();
    }

    @Transactional(readOnly = true)
    public List<TroubleshootingSessionSummaryDto> getUserSessions(String userEmail) {
        User user = getUserByEmail(userEmail);
        List<TroubleshootingSession> sessions;

        if (user.getRole() == Role.ADMIN) {
            sessions = sessionRepository.findAllByOrderByUpdatedAtDesc();
        } else {
            sessions = sessionRepository.findByUserOrderByUpdatedAtDesc(user);
        }

        return sessions.stream()
                .map(s -> TroubleshootingSessionSummaryDto.builder()
                        .id(s.getId())
                        .title(s.getTitle())
                        .applicationId(s.getApplication() != null ? s.getApplication().getId() : null)
                        .applicationName(s.getApplication() != null ? s.getApplication().getName() : null)
                        .messageCount(messageRepository.countBySession(s))
                        .createdAt(s.getCreatedAt())
                        .updatedAt(s.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TroubleshootingSessionDetailResponse getSessionById(Long sessionId, String userEmail) {
        User user = getUserByEmail(userEmail);
        TroubleshootingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Troubleshooting session not found with id: " + sessionId));

        if (user.getRole() != Role.ADMIN && !session.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedCustomException("You do not have permission to access this troubleshooting session");
        }

        List<TroubleshootingMessage> messages = messageRepository.findBySessionOrderByCreatedAtAsc(session);

        return TroubleshootingSessionDetailResponse.builder()
                .id(session.getId())
                .title(session.getTitle())
                .applicationId(session.getApplication() != null ? session.getApplication().getId() : null)
                .applicationName(session.getApplication() != null ? session.getApplication().getName() : null)
                .deploymentId(session.getDeployment() != null ? session.getDeployment().getId() : null)
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .messages(messages.stream().map(this::mapToMessageDto).collect(Collectors.toList()))
                .build();
    }

    @Transactional
    public void deleteSession(Long sessionId, String userEmail) {
        User user = getUserByEmail(userEmail);
        TroubleshootingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Troubleshooting session not found with id: " + sessionId));

        if (user.getRole() != Role.ADMIN && !session.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedCustomException("You do not have permission to delete this troubleshooting session");
        }

        sessionRepository.delete(session);
    }

    private String buildApplicationContextString(Application app) {
        if (app == null) {
            return "No specific application context selected.";
        }
        return """
                Application ID: %d
                Application Name: %s
                Repository URL: %s
                Current Deployment Status: %s
                Description: %s
                """.formatted(
                app.getId(),
                app.getName(),
                app.getRepositoryUrl() != null ? app.getRepositoryUrl() : "N/A",
                app.getDeploymentStatus(),
                app.getDescription() != null ? app.getDescription() : "N/A"
        );
    }

    private String buildDeploymentContextString(Application app, Deployment dep) {
        if (dep != null) {
            return """
                    Deployment ID: %d
                    Version: %s
                    Commit Hash: %s
                    Deployment Status: %s
                    Deployment Message / Error: %s
                    Deployed At: %s
                    """.formatted(
                    dep.getId(),
                    dep.getVersion(),
                    dep.getCommitHash() != null ? dep.getCommitHash() : "N/A",
                    dep.getStatus(),
                    dep.getDeploymentMessage() != null ? dep.getDeploymentMessage() : "N/A",
                    dep.getDeployedAt()
            );
        } else if (app != null && app.getDeployments() != null && !app.getDeployments().isEmpty()) {
            Deployment latest = app.getDeployments().get(app.getDeployments().size() - 1);
            return """
                    Latest Deployment ID: %d
                    Version: %s
                    Commit Hash: %s
                    Deployment Status: %s
                    Deployment Message / Error: %s
                    Deployed At: %s
                    """.formatted(
                    latest.getId(),
                    latest.getVersion(),
                    latest.getCommitHash() != null ? latest.getCommitHash() : "N/A",
                    latest.getStatus(),
                    latest.getDeploymentMessage() != null ? latest.getDeploymentMessage() : "N/A",
                    latest.getDeployedAt()
            );
        }
        return "No specific deployment context selected.";
    }

    private String buildConversationHistoryString(TroubleshootingSession session) {
        if (session == null || session.getMessages() == null || session.getMessages().isEmpty()) {
            return "No previous conversation history.";
        }
        List<TroubleshootingMessage> msgs = session.getMessages();
        int startIdx = Math.max(0, msgs.size() - 10);
        StringBuilder sb = new StringBuilder();
        for (int i = startIdx; i < msgs.size(); i++) {
            TroubleshootingMessage m = msgs.get(i);
            String snippet = m.getContent().length() > 500 ? m.getContent().substring(0, 500) + "..." : m.getContent();
            sb.append("[").append(m.getRole()).append("]: ").append(snippet).append("\n");
        }
        return sb.toString();
    }

    private TroubleshootingMessageDto mapToMessageDto(TroubleshootingMessage msg) {
        return TroubleshootingMessageDto.builder()
                .id(msg.getId())
                .role(msg.getRole().name())
                .content(msg.getContent())
                .severity(msg.getSeverity() != null ? msg.getSeverity().name() : null)
                .rootCause(msg.getRootCause())
                .remediationSteps(msg.getRemediationSteps())
                .suggestedCommands(msg.getSuggestedCommands())
                .createdAt(msg.getCreatedAt())
                .build();
    }

    private String cleanJsonOutput(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    private void recordInteraction(User user, String status, String errorMessage) {
        try {
            AIInteraction interaction = AIInteraction.builder()
                    .user(user)
                    .feature("TROUBLESHOOTING_ASSISTANT")
                    .provider(aiProvider.getProviderName())
                    .model(aiProvider.getModelName())
                    .status(status)
                    .errorMessage(errorMessage != null && errorMessage.length() > 500
                            ? errorMessage.substring(0, 500)
                            : errorMessage)
                    .build();
            aiInteractionRepository.save(interaction);
        } catch (Exception e) {
            log.error("Failed to record AI interaction audit log", e);
        }
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }
}
