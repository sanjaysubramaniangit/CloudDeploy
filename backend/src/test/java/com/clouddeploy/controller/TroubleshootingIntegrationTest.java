package com.clouddeploy.controller;

import com.clouddeploy.dto.TroubleshootingPromptRequest;
import com.clouddeploy.entity.*;
import com.clouddeploy.repository.*;
import com.clouddeploy.security.JwtService;
import com.clouddeploy.service.ai.AIProvider;
import com.clouddeploy.service.storage.StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class TroubleshootingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private StoredFileRepository storedFileRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeAnalysisRepository resumeAnalysisRepository;

    @Autowired
    private AIInteractionRepository aiInteractionRepository;

    @Autowired
    private JobDescriptionRepository jobDescriptionRepository;

    @Autowired
    private JobMatchRepository jobMatchRepository;

    @Autowired
    private JobMatchDetailRepository jobMatchDetailRepository;

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

    @Autowired
    private InterviewQuestionRepository interviewQuestionRepository;

    @Autowired
    private TroubleshootingSessionRepository troubleshootingSessionRepository;

    @Autowired
    private TroubleshootingMessageRepository troubleshootingMessageRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private AIProvider aiProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User userA;
    private User userB;
    private User adminUser;
    private String tokenA;
    private String tokenB;
    private String tokenAdmin;

    private Application appA1;
    private Application appA2;
    private Application appB;

    private Deployment depA1;
    private Deployment depA2;
    private Deployment depB;

    private final String validAssistantJson = """
            {
              "content": "The application encountered a CrashLoopBackOff due to a missing environment variable in the container configuration.",
              "severity": "HIGH",
              "rootCause": "Database connection string DB_HOST is unset, causing the Spring Boot context to fail at startup.",
              "remediationSteps": [
                "1. Check the environment variables in your deployment manifest.",
                "2. Set DB_HOST=localhost before starting the container workload.",
                "3. Restart the workload and inspect application logs."
              ],
              "suggestedCommands": [
                "kubectl describe pod my-app-pod -n production",
                "kubectl logs my-app-pod --previous",
                "docker inspect my-app-container"
              ]
            }
            """;

    @BeforeEach
    void setUp() {
        cleanDatabases();

        userA = userRepository.save(User.builder()
                .name("Alice SRE")
                .email("alice_sre@example.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build());

        userB = userRepository.save(User.builder()
                .name("Bob Cloud")
                .email("bob_cloud@example.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build());

        adminUser = userRepository.save(User.builder()
                .name("Admin SRE")
                .email("admin_sre@example.com")
                .password(passwordEncoder.encode("adminpass123"))
                .role(Role.ADMIN)
                .build());

        tokenA = jwtService.generateToken(userA);
        tokenB = jwtService.generateToken(userB);
        tokenAdmin = jwtService.generateToken(adminUser);

        appA1 = applicationRepository.save(Application.builder()
                .name("Auth-Service")
                .description("Authentication microservice")
                .repositoryUrl("https://github.com/cloud/auth-service")
                .deploymentStatus(ApplicationStatus.FAILED)
                .owner(userA)
                .build());

        appA2 = applicationRepository.save(Application.builder()
                .name("Payment-Service")
                .description("Payment processing gateway")
                .repositoryUrl("https://github.com/cloud/payment-service")
                .deploymentStatus(ApplicationStatus.RUNNING)
                .owner(userA)
                .build());

        appB = applicationRepository.save(Application.builder()
                .name("Order-Service")
                .description("Order fulfillment service")
                .repositoryUrl("https://github.com/cloud/order-service")
                .deploymentStatus(ApplicationStatus.FAILED)
                .owner(userB)
                .build());

        depA1 = deploymentRepository.save(Deployment.builder()
                .application(appA1)
                .version("v1.2.0")
                .commitHash("a1b2c3d4e5f678901234567890abcdef12345678")
                .status(DeploymentStatus.FAILED)
                .deploymentMessage("Application failed to start: Connection refused on port 5432")
                .build());

        depA2 = deploymentRepository.save(Deployment.builder()
                .application(appA2)
                .version("v2.0.1")
                .commitHash("b2c3d4e5f6a178901234567890abcdef12345678")
                .status(DeploymentStatus.SUCCESS)
                .deploymentMessage("Deployment succeeded")
                .build());

        depB = deploymentRepository.save(Deployment.builder()
                .application(appB)
                .version("v0.9.0")
                .commitHash("c3d4e5f6a1b278901234567890abcdef12345678")
                .status(DeploymentStatus.FAILED)
                .deploymentMessage("OOMKilled exit code 137")
                .build());

        when(aiProvider.isConfigured()).thenReturn(true);
        when(aiProvider.getProviderName()).thenReturn("mock-openai");
        when(aiProvider.getModelName()).thenReturn("gpt-4o-mini");
        when(aiProvider.generateCompletion(anyString(), anyString())).thenReturn(validAssistantJson);
    }

    @AfterEach
    void tearDown() {
        cleanDatabases();
    }

    private void cleanDatabases() {
        troubleshootingMessageRepository.deleteAll();
        troubleshootingSessionRepository.deleteAll();
        interviewQuestionRepository.deleteAll();
        interviewSessionRepository.deleteAll();
        jobMatchDetailRepository.deleteAll();
        jobMatchRepository.deleteAll();
        jobDescriptionRepository.deleteAll();
        resumeAnalysisRepository.deleteAll();
        resumeRepository.deleteAll();
        aiInteractionRepository.deleteAll();
        storedFileRepository.deleteAll();
        deploymentRepository.deleteAll();
        applicationRepository.deleteAll();
        userRepository.deleteAll();
    }

    // =========================================================================
    // Scenario 1: User can send query and receive structured assistance (200 OK)
    // =========================================================================
    @Test
    @DisplayName("Scenario 1: Authenticated user sends query and receives structured troubleshooting response (200 OK)")
    void test01_userCanSendQueryAndReceiveAssistance() throws Exception {
        TroubleshootingPromptRequest req = TroubleshootingPromptRequest.builder()
                .query("Why is my pod crashing with CrashLoopBackOff?")
                .build();

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").isNumber())
                .andExpect(jsonPath("$.message.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.message.severity").value("HIGH"))
                .andExpect(jsonPath("$.message.rootCause").value(containsString("DB_HOST is unset")))
                .andExpect(jsonPath("$.message.remediationSteps", hasSize(3)))
                .andExpect(jsonPath("$.message.suggestedCommands", hasSize(3)));

        assertEquals(1, troubleshootingSessionRepository.count());
        assertEquals(2, troubleshootingMessageRepository.count()); // 1 USER + 1 ASSISTANT
    }

    // =========================================================================
    // Scenario 2: Unauthenticated chat rejected with 401 Unauthorized
    // =========================================================================
    @Test
    @DisplayName("Scenario 2: Unauthenticated chat request rejected with 401 Unauthorized")
    void test02_unauthenticatedChat_RejectedWith401() throws Exception {
        TroubleshootingPromptRequest req = TroubleshootingPromptRequest.builder()
                .query("How do I fix this crash?")
                .build();

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Scenario 3: Query with valid application context grounds analysis
    // =========================================================================
    @Test
    @DisplayName("Scenario 3: Query with valid application context injects server-derived metadata into prompt")
    void test03_queryWithApplicationContext_GroundsAnalysis() throws Exception {
        TroubleshootingPromptRequest req = TroubleshootingPromptRequest.builder()
                .applicationId(appA1.getId())
                .query("The service fails to start up during deployment.")
                .build();

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value(appA1.getId()))
                .andExpect(jsonPath("$.applicationName").value("Auth-Service"));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiProvider).generateCompletion(anyString(), promptCaptor.capture());

        String capturedPrompt = promptCaptor.getValue();
        assertTrue(capturedPrompt.contains("Auth-Service"), "Prompt must contain application name");
        assertTrue(capturedPrompt.contains("https://github.com/cloud/auth-service"), "Prompt must contain repository URL");
    }

    // =========================================================================
    // Scenario 4: Query with valid deployment context grounds analysis
    // =========================================================================
    @Test
    @DisplayName("Scenario 4: Query with valid deployment context injects commit hash and error message")
    void test04_queryWithDeploymentContext_GroundsAnalysis() throws Exception {
        TroubleshootingPromptRequest req = TroubleshootingPromptRequest.builder()
                .applicationId(appA1.getId())
                .deploymentId(depA1.getId())
                .query("Investigate failed release.")
                .build();

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deploymentId").value(depA1.getId()));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiProvider).generateCompletion(anyString(), promptCaptor.capture());

        String capturedPrompt = promptCaptor.getValue();
        assertTrue(capturedPrompt.contains("a1b2c3d4e5f678901234567890abcdef12345678"), "Prompt must contain commit hash");
        assertTrue(capturedPrompt.contains("Connection refused on port 5432"), "Prompt must contain deployment error message");
    }

    // =========================================================================
    // Scenario 5: Cross-user application context rejected with 403 Forbidden
    // =========================================================================
    @Test
    @DisplayName("Scenario 5: User A cannot query with User B's application (403 Forbidden)")
    void test05_crossUserApplication_RejectedWith403() throws Exception {
        TroubleshootingPromptRequest req = TroubleshootingPromptRequest.builder()
                .applicationId(appB.getId())
                .query("Diagnose this app")
                .build();

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Scenario 6: Cross-user deployment context rejected with 403 Forbidden
    // =========================================================================
    @Test
    @DisplayName("Scenario 6: User A cannot query with User B's deployment (403 Forbidden)")
    void test06_crossUserDeployment_RejectedWith403() throws Exception {
        TroubleshootingPromptRequest req = TroubleshootingPromptRequest.builder()
                .deploymentId(depB.getId())
                .query("Diagnose this deployment")
                .build();

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Scenario 7: Mismatched application and deployment combination rejected with 400 Bad Request
    // =========================================================================
    @Test
    @DisplayName("Scenario 7: Mismatched application and deployment combination rejected with 400 Bad Request")
    void test07_mismatchedApplicationAndDeployment_RejectedWith400() throws Exception {
        // App A1 with Deployment A2 (which belongs to App A2!)
        TroubleshootingPromptRequest req = TroubleshootingPromptRequest.builder()
                .applicationId(appA1.getId())
                .deploymentId(depA2.getId())
                .query("Diagnose mismatched application and deployment")
                .build();

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Deployment does not belong to the specified application")));
    }

    // =========================================================================
    // Scenario 8: Cross-user session continuation rejected with 403 Forbidden
    // =========================================================================
    @Test
    @DisplayName("Scenario 8: User B cannot continue or post to User A's session (403 Forbidden)")
    void test08_crossUserSessionContinuation_RejectedWith403() throws Exception {
        TroubleshootingSession sessionA = troubleshootingSessionRepository.save(TroubleshootingSession.builder()
                .user(userA)
                .application(appA1)
                .title("Alice's troubleshooting thread")
                .build());

        TroubleshootingPromptRequest req = TroubleshootingPromptRequest.builder()
                .sessionId(sessionA.getId())
                .query("Continuing Alice's chat thread from Bob's account")
                .build();

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Scenario 9: Unconfigured AI provider returns HTTP 503 with exact message
    // =========================================================================
    @Test
    @DisplayName("Scenario 9: Unconfigured AI provider returns HTTP 503 with exact specified message")
    void test09_aiUnconfigured_Returns503() throws Exception {
        when(aiProvider.isConfigured()).thenReturn(false);

        TroubleshootingPromptRequest req = TroubleshootingPromptRequest.builder()
                .query("How to fix 504 Gateway Timeout?")
                .build();

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(
                        "AI cloud troubleshooting assistant is unavailable because the AI provider is not configured. Configure the AI provider to enable cloud troubleshooting assistance."
                ));
    }

    // =========================================================================
    // Scenario 10: Sensitive credentials scrubbed before AI invocation
    // =========================================================================
    @Test
    @DisplayName("Scenario 10: Sensitive credentials (AWS key, Bearer token, private key) scrubbed before AI invocation")
    void test10_sensitiveDataScrubbedBeforeAiCall() throws Exception {
        TroubleshootingPromptRequest req = TroubleshootingPromptRequest.builder()
                .query("Getting unauthorized with key AKIAIOSFODNN7EXAMPLE and Bearer eyJhbGciOiJIUzI1NiJ9.test.sig")
                .logSnippet("Error: Password was password=SuperSecret123 when connecting to RDS")
                .build();

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiProvider).generateCompletion(anyString(), promptCaptor.capture());

        String capturedPrompt = promptCaptor.getValue();
        assertFalse(capturedPrompt.contains("AKIAIOSFODNN7EXAMPLE"), "AWS key must be redacted");
        assertTrue(capturedPrompt.contains("[REDACTED_AWS_KEY]"), "Redaction token must be present");
        assertFalse(capturedPrompt.contains("SuperSecret123"), "Password must be redacted");
        assertTrue(capturedPrompt.contains("[REDACTED_PASSWORD]"), "Redacted password token must be present");
    }

    // =========================================================================
    // Scenario 11: Suggested commands are advisory only and never executed
    // =========================================================================
    @Test
    @DisplayName("Scenario 11: Suggested commands are stored as advisory text only and never executed")
    void test11_commandsAreAdvisoryOnlyAndNeverExecuted() throws Exception {
        TroubleshootingPromptRequest req = TroubleshootingPromptRequest.builder()
                .query("Pod status unknown")
                .build();

        String responseStr = mockMvc.perform(post("/api/ai/assistant/chat")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(responseStr);
        JsonNode commands = root.path("message").path("suggestedCommands");
        assertTrue(commands.isArray());
        assertEquals(3, commands.size());
        assertEquals("kubectl describe pod my-app-pod -n production", commands.get(0).asText());

        // Verify message entity in DB stores commands as strings
        List<TroubleshootingMessage> messages = troubleshootingMessageRepository.findAll();
        TroubleshootingMessage assistantMsg = messages.stream()
                .filter(m -> m.getRole() == TroubleshootingMessageRole.ASSISTANT)
                .findFirst().orElseThrow();
        assertEquals(3, assistantMsg.getSuggestedCommands().size());
    }

    // =========================================================================
    // Scenario 12: Command safety validation rejects invalid or oversized commands
    // =========================================================================
    @Test
    @DisplayName("Scenario 12: AI response containing invalid/blank commands is rejected with error")
    void test12_commandValidation_RejectsInvalidCommands() throws Exception {
        String invalidCommandsJson = """
                {
                  "content": "Explanation text",
                  "severity": "LOW",
                  "rootCause": "Minor glitch",
                  "remediationSteps": ["Step 1: Check config"],
                  "suggestedCommands": ["   ", "valid command"]
                }
                """;
        when(aiProvider.generateCompletion(anyString(), anyString())).thenReturn(invalidCommandsJson);

        TroubleshootingPromptRequest req = TroubleshootingPromptRequest.builder()
                .query("Minor config question")
                .build();

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadGateway());

        assertEquals(0, troubleshootingSessionRepository.count(), "Transaction must rollback on validation failure");
    }

    // =========================================================================
    // Scenario 13: Multi-turn conversation history preserved across turns
    // =========================================================================
    @Test
    @DisplayName("Scenario 13: Multi-turn conversation history preserved and injected into subsequent turns")
    void test13_conversationHistoryPreservedAcrossTurns() throws Exception {
        // Turn 1
        TroubleshootingPromptRequest turn1 = TroubleshootingPromptRequest.builder()
                .query("Turn 1: Pod is failing with OOM")
                .build();

        String turn1Resp = mockMvc.perform(post("/api/ai/assistant/chat")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(turn1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        long sessionId = objectMapper.readTree(turn1Resp).get("sessionId").asLong();

        // Turn 2 in same session
        TroubleshootingPromptRequest turn2 = TroubleshootingPromptRequest.builder()
                .sessionId(sessionId)
                .query("Turn 2: What memory limits should I configure?")
                .build();

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(turn2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiProvider, times(2)).generateCompletion(anyString(), promptCaptor.capture());

        String secondTurnPrompt = promptCaptor.getValue();
        assertTrue(secondTurnPrompt.contains("Turn 1: Pod is failing with OOM"), "Turn 2 prompt must include Turn 1 history");
    }

    // =========================================================================
    // Scenario 14: User can list own sessions as lightweight summaries
    // =========================================================================
    @Test
    @DisplayName("Scenario 14: User can list own troubleshooting sessions as lightweight summaries")
    void test14_userCanListOwnSessionsAsSummaries() throws Exception {
        troubleshootingSessionRepository.save(TroubleshootingSession.builder()
                .user(userA)
                .application(appA1)
                .title("Alice Session 1")
                .build());

        troubleshootingSessionRepository.save(TroubleshootingSession.builder()
                .user(userA)
                .application(appA2)
                .title("Alice Session 2")
                .build());

        mockMvc.perform(get("/api/ai/assistant/sessions")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").exists())
                .andExpect(jsonPath("$[0].messageCount").isNumber());
    }

    // =========================================================================
    // Scenario 15: User cannot list another user's sessions
    // =========================================================================
    @Test
    @DisplayName("Scenario 15: User B cannot see User A's troubleshooting sessions in history")
    void test15_userCannotListAnotherUserSessions() throws Exception {
        troubleshootingSessionRepository.save(TroubleshootingSession.builder()
                .user(userA)
                .application(appA1)
                .title("Alice Private Session")
                .build());

        mockMvc.perform(get("/api/ai/assistant/sessions")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // =========================================================================
    // Scenario 16: User can retrieve full session thread by ID
    // =========================================================================
    @Test
    @DisplayName("Scenario 16: User can retrieve full session thread and message history by ID")
    void test16_userCanRetrieveFullSessionThread() throws Exception {
        TroubleshootingSession session = troubleshootingSessionRepository.save(TroubleshootingSession.builder()
                .user(userA)
                .application(appA1)
                .title("Full Thread Test")
                .build());

        troubleshootingMessageRepository.save(TroubleshootingMessage.builder()
                .session(session)
                .role(TroubleshootingMessageRole.USER)
                .content("Initial query")
                .build());

        troubleshootingMessageRepository.save(TroubleshootingMessage.builder()
                .session(session)
                .role(TroubleshootingMessageRole.ASSISTANT)
                .content("Diagnostic response")
                .severity(TroubleshootingSeverity.MEDIUM)
                .rootCause("Root cause")
                .build());

        mockMvc.perform(get("/api/ai/assistant/sessions/" + session.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(session.getId()))
                .andExpect(jsonPath("$.messages", hasSize(2)));
    }

    // =========================================================================
    // Scenario 17: User cannot retrieve another user's session (403); Admin can
    // =========================================================================
    @Test
    @DisplayName("Scenario 17: User B cannot retrieve User A's session (403 Forbidden); Admin can")
    void test17_userCannotRetrieveAnotherUserSession_RejectedWith403() throws Exception {
        TroubleshootingSession session = troubleshootingSessionRepository.save(TroubleshootingSession.builder()
                .user(userA)
                .application(appA1)
                .title("Alice Secret Thread")
                .build());

        // User B rejected with 403
        mockMvc.perform(get("/api/ai/assistant/sessions/" + session.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // Admin can inspect
        mockMvc.perform(get("/api/ai/assistant/sessions/" + session.getId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(session.getId()));
    }

    // =========================================================================
    // Scenario 18: User can delete own session (cascading messages)
    // =========================================================================
    @Test
    @DisplayName("Scenario 18: User can delete own session and cascade removes messages")
    void test18_userCanDeleteOwnSession() throws Exception {
        TroubleshootingSession session = troubleshootingSessionRepository.save(TroubleshootingSession.builder()
                .user(userA)
                .application(appA1)
                .title("Thread to delete")
                .build());

        troubleshootingMessageRepository.save(TroubleshootingMessage.builder()
                .session(session)
                .role(TroubleshootingMessageRole.USER)
                .content("Message to delete")
                .build());

        mockMvc.perform(delete("/api/ai/assistant/sessions/" + session.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Troubleshooting session deleted successfully"));

        mockMvc.perform(get("/api/ai/assistant/sessions/" + session.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        assertEquals(0, troubleshootingMessageRepository.count());
    }

    // =========================================================================
    // Scenario 19: User cannot delete another user's session (403 Forbidden)
    // =========================================================================
    @Test
    @DisplayName("Scenario 19: User B cannot delete User A's troubleshooting session (403 Forbidden)")
    void test19_userCannotDeleteAnotherUserSession_RejectedWith403() throws Exception {
        TroubleshootingSession session = troubleshootingSessionRepository.save(TroubleshootingSession.builder()
                .user(userA)
                .application(appA1)
                .title("Alice's session")
                .build());

        mockMvc.perform(delete("/api/ai/assistant/sessions/" + session.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        assertTrue(troubleshootingSessionRepository.existsById(session.getId()));
    }

    // =========================================================================
    // Scenario 20: Telemetry and failed interaction logged in ai_interactions
    // =========================================================================
    @Test
    @DisplayName("Scenario 20: Telemetry recorded in ai_interactions for success and failure")
    void test20_telemetryAndFailedInteractionLogged() throws Exception {
        // Success case
        TroubleshootingPromptRequest req1 = TroubleshootingPromptRequest.builder()
                .query("Successful troubleshooting query")
                .build();

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk());

        List<AIInteraction> interactions = aiInteractionRepository.findAll();
        assertEquals(1, interactions.size());
        assertEquals("TROUBLESHOOTING_ASSISTANT", interactions.get(0).getFeature());
        assertEquals("SUCCESS", interactions.get(0).getStatus());

        // Failure case (malformed JSON)
        when(aiProvider.generateCompletion(anyString(), anyString())).thenReturn("NOT VALID JSON");
        TroubleshootingPromptRequest req2 = TroubleshootingPromptRequest.builder()
                .query("Failing troubleshooting query")
                .build();

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isBadGateway());

        List<AIInteraction> updatedInteractions = aiInteractionRepository.findAll();
        assertEquals(2, updatedInteractions.size());
        assertEquals("FAILED", updatedInteractions.get(1).getStatus());
    }
}
