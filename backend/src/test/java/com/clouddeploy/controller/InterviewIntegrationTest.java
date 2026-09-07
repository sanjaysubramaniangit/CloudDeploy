package com.clouddeploy.controller;

import com.clouddeploy.dto.InterviewGenerationRequest;
import com.clouddeploy.dto.InterviewSessionResponse;
import com.clouddeploy.dto.InterviewSessionSummaryDto;
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
public class InterviewIntegrationTest {

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
    private TroubleshootingMessageRepository troubleshootingMessageRepository;

    @Autowired
    private TroubleshootingSessionRepository troubleshootingSessionRepository;

    @Autowired
    private InterviewQuestionRepository interviewQuestionRepository;

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

    private Resume resumeA;
    private ResumeAnalysis analysisA;
    private JobDescription jobA;

    private Resume resumeB;
    private ResumeAnalysis analysisB;
    private JobDescription jobB;

    private static final String SAMPLE_VALID_10_QUESTIONS_JSON = """
    {
      "questions": [
        {
          "question": "How does Java memory management and garbage collection work in multi-threaded microservices?",
          "category": "JAVA",
          "expectedConcepts": ["JVM Heap", "Garbage Collection", "Thread Safety"]
        },
        {
          "question": "Explain how you would design an LRU cache using appropriate data structures.",
          "category": "DSA",
          "expectedConcepts": ["HashMap", "Doubly Linked List", "O(1) lookup"]
        },
        {
          "question": "How do Spring Boot auto-configurations and condition annotations operate under the hood?",
          "category": "SPRING_BOOT",
          "expectedConcepts": ["@ConditionalOnClass", "ApplicationContext", "AutoConfigurationImportSelector"]
        },
        {
          "question": "What strategies do you use for API versioning and idempotency in distributed REST APIs?",
          "category": "REST_APIS",
          "expectedConcepts": ["Idempotency Keys", "URI Versioning", "HTTP Status Codes"]
        },
        {
          "question": "How do you optimize slow queries involving multiple JOINs and analyze execution plans in SQL?",
          "category": "SQL",
          "expectedConcepts": ["EXPLAIN ANALYZE", "B-Tree Indexes", "Query Planner"]
        },
        {
          "question": "Describe the security controls you would implement when hosting a containerized app on AWS.",
          "category": "AWS",
          "expectedConcepts": ["IAM Roles", "VPC Security Groups", "Private Subnets"]
        },
        {
          "question": "How do multi-stage Docker builds reduce image size and improve deployment security?",
          "category": "DOCKER",
          "expectedConcepts": ["Build Stages", "Minimal Base Image", "Attack Surface Reduction"]
        },
        {
          "question": "How do you troubleshoot high CPU and memory pressure on a production Linux node?",
          "category": "LINUX",
          "expectedConcepts": ["top/htop", "vmstat", "dmesg", "File Descriptors"]
        },
        {
          "question": "Describe an automated CI/CD deployment pipeline with blue-green or canary release strategies.",
          "category": "DEVOPS",
          "expectedConcepts": ["Canary Deployments", "Traffic Shifting", "Automated Rollback"]
        },
        {
          "question": "How would you design a secure event-driven cloud deployment architecture in CloudDeploy AI?",
          "category": "PROJECT_SPECIFIC",
          "expectedConcepts": ["Stateless Microservices", "S3 Storage", "JWT Security"]
        }
      ]
    }
    """;

    @BeforeEach
    void setUp() {
        cleanDatabases();

        userA = userRepository.save(User.builder()
                .name("User A")
                .email("usera_interview@example.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build());

        userB = userRepository.save(User.builder()
                .name("User B")
                .email("userb_interview@example.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build());

        adminUser = userRepository.save(User.builder()
                .name("Admin User")
                .email("admin_interview@example.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.ADMIN)
                .build());

        tokenA = jwtService.generateToken(userA);
        tokenB = jwtService.generateToken(userB);
        tokenAdmin = jwtService.generateToken(adminUser);

        // Resume & Analysis for User A
        resumeA = resumeRepository.save(Resume.builder()
                .user(userA)
                .fileName("usera_resume.pdf")
                .s3Key("resumes/" + userA.getId() + "/usera_resume.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .extractedText("Senior Engineer with Java, Spring Boot, AWS, Docker, and PostgreSQL experience.")
                .build());

        analysisA = resumeAnalysisRepository.save(ResumeAnalysis.builder()
                .resume(resumeA)
                .aiProvider("mock")
                .aiModel("mock-model")
                .summary("Senior Java Cloud Engineer with 5+ years experience.")
                .technicalSkills(List.of("Java", "Spring Boot", "AWS", "Docker", "PostgreSQL"))
                .programmingLanguages(List.of("Java"))
                .frameworks(List.of("Spring Boot"))
                .cloudTechnologies(List.of("AWS"))
                .databases(List.of("PostgreSQL"))
                .devopsTools(List.of("Docker"))
                .experienceHighlights(List.of("Architected high throughput cloud services on AWS"))
                .rawJson("{}")
                .build());

        jobA = jobDescriptionRepository.save(JobDescription.builder()
                .user(userA)
                .title("Senior Cloud Architect")
                .company("Acme Cloud Corp")
                .description("Requirements: Java, Spring Boot, AWS, Docker. Preferred: Kubernetes, Redis.")
                .build());

        // Resume & Analysis for User B
        resumeB = resumeRepository.save(Resume.builder()
                .user(userB)
                .fileName("userb_resume.pdf")
                .s3Key("resumes/" + userB.getId() + "/userb_resume.pdf")
                .contentType("application/pdf")
                .fileSize(2048L)
                .extractedText("Python and GCP Developer.")
                .build());

        analysisB = resumeAnalysisRepository.save(ResumeAnalysis.builder()
                .resume(resumeB)
                .aiProvider("mock")
                .aiModel("mock-model")
                .summary("Python GCP Engineer.")
                .technicalSkills(List.of("Python", "GCP", "Django"))
                .programmingLanguages(List.of("Python"))
                .frameworks(List.of("Django"))
                .cloudTechnologies(List.of("GCP"))
                .rawJson("{}")
                .build());

        jobB = jobDescriptionRepository.save(JobDescription.builder()
                .user(userB)
                .title("Python Data Engineer")
                .company("DataCorp")
                .description("Requirements: Python, GCP, BigQuery.")
                .build());

        // Default Mock AI behavior
        when(aiProvider.isConfigured()).thenReturn(true);
        when(aiProvider.getProviderName()).thenReturn("mock");
        when(aiProvider.getModelName()).thenReturn("mock-model");
        when(aiProvider.generateCompletion(anyString(), anyString())).thenReturn(SAMPLE_VALID_10_QUESTIONS_JSON);
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
    // Scenario 1: User can create interview session
    // =========================================================================
    @Test
    @DisplayName("Scenario 1: Authenticated user creates interview session successfully")
    void test01_createInterviewSession_Success() throws Exception {
        InterviewGenerationRequest req = InterviewGenerationRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(jobA.getId())
                .difficulty("INTERMEDIATE")
                .build();

        mockMvc.perform(post("/api/ai/interview/generate")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.jobTitle").value("Senior Cloud Architect"))
                .andExpect(jsonPath("$.company").value("Acme Cloud Corp"))
                .andExpect(jsonPath("$.difficulty").value("INTERMEDIATE"))
                .andExpect(jsonPath("$.questionCount").value(10))
                .andExpect(jsonPath("$.questions", hasSize(10)))
                .andExpect(jsonPath("$.questions[0].difficulty").value("INTERMEDIATE"))
                .andExpect(jsonPath("$.questions[0].expectedConcepts", not(empty())));
    }

    // =========================================================================
    // Scenario 2: Unauthenticated generation rejected (401)
    // =========================================================================
    @Test
    @DisplayName("Scenario 2: Unauthenticated interview generation and access rejected with 401")
    void test02_unauthenticatedGeneration_RejectedWith401() throws Exception {
        InterviewGenerationRequest req = InterviewGenerationRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(jobA.getId())
                .difficulty("BEGINNER")
                .build();

        mockMvc.perform(post("/api/ai/interview/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/ai/interview"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Scenario 3: Cross-user resume rejected (403)
    // =========================================================================
    @Test
    @DisplayName("Scenario 3: User A attempting to use User B's resume rejected with 403 Forbidden")
    void test03_crossUserResume_RejectedWith403() throws Exception {
        InterviewGenerationRequest req = InterviewGenerationRequest.builder()
                .resumeId(resumeB.getId()) // Belongs to User B!
                .jobDescriptionId(jobA.getId())
                .difficulty("INTERMEDIATE")
                .build();

        mockMvc.perform(post("/api/ai/interview/generate")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Scenario 4: Cross-user job rejected (403)
    // =========================================================================
    @Test
    @DisplayName("Scenario 4: User A attempting to use User B's job description rejected with 403 Forbidden")
    void test04_crossUserJob_RejectedWith403() throws Exception {
        InterviewGenerationRequest req = InterviewGenerationRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(jobB.getId()) // Belongs to User B!
                .difficulty("INTERMEDIATE")
                .build();

        mockMvc.perform(post("/api/ai/interview/generate")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Scenario 5: User A resume + User B job rejected (403)
    // =========================================================================
    @Test
    @DisplayName("Scenario 5: User A resume + User B job description is rejected with 403 Forbidden")
    void test05_userAResumeAndUserBJob_RejectedWith403() throws Exception {
        // Even if called by Admin, mismatched resume and job owners are rejected
        InterviewGenerationRequest req = InterviewGenerationRequest.builder()
                .resumeId(resumeA.getId()) // User A
                .jobDescriptionId(jobB.getId()) // User B
                .difficulty("INTERMEDIATE")
                .build();

        mockMvc.perform(post("/api/ai/interview/generate")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("must belong to the same user")));
    }

    // =========================================================================
    // Scenario 6: Unanalyzed resume rejected (400)
    // =========================================================================
    @Test
    @DisplayName("Scenario 6: Unanalyzed resume rejected with 400 Bad Request")
    void test06_unanalyzedResume_RejectedWith400() throws Exception {
        Resume unanalyzed = resumeRepository.save(Resume.builder()
                .user(userA)
                .fileName("raw.pdf")
                .s3Key("resumes/" + userA.getId() + "/raw.pdf")
                .contentType("application/pdf")
                .fileSize(512L)
                .extractedText("Raw text without analysis")
                .build());

        InterviewGenerationRequest req = InterviewGenerationRequest.builder()
                .resumeId(unanalyzed.getId())
                .jobDescriptionId(jobA.getId())
                .difficulty("INTERMEDIATE")
                .build();

        mockMvc.perform(post("/api/ai/interview/generate")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Resume has not been analyzed yet")));
    }

    // =========================================================================
    // Scenario 7: Valid difficulty accepted (BEGINNER, INTERMEDIATE, ADVANCED)
    // =========================================================================
    @Test
    @DisplayName("Scenario 7: Valid difficulties (BEGINNER, ADVANCED) accepted and assigned")
    void test07_validDifficultyAccepted() throws Exception {
        // Test BEGINNER
        InterviewGenerationRequest reqBeginner = InterviewGenerationRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(jobA.getId())
                .difficulty("BEGINNER")
                .build();

        mockMvc.perform(post("/api/ai/interview/generate")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqBeginner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.difficulty").value("BEGINNER"))
                .andExpect(jsonPath("$.questions[0].difficulty").value("BEGINNER"));

        // Test ADVANCED
        InterviewGenerationRequest reqAdvanced = InterviewGenerationRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(jobA.getId())
                .difficulty("ADVANCED")
                .build();

        mockMvc.perform(post("/api/ai/interview/generate")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqAdvanced)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.difficulty").value("ADVANCED"))
                .andExpect(jsonPath("$.questions[0].difficulty").value("ADVANCED"));
    }

    // =========================================================================
    // Scenario 8: Invalid difficulty rejected (400)
    // =========================================================================
    @Test
    @DisplayName("Scenario 8: Invalid difficulty level rejected with 400 Bad Request")
    void test08_invalidDifficulty_RejectedWith400() throws Exception {
        InterviewGenerationRequest req = InterviewGenerationRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(jobA.getId())
                .difficulty("EXTREME")
                .build();

        mockMvc.perform(post("/api/ai/interview/generate")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Invalid difficulty level")));
    }

    // =========================================================================
    // Scenario 9: Mock AI question generation works
    // =========================================================================
    @Test
    @DisplayName("Scenario 9: Mock AI generation invoked and completes cleanly")
    void test09_mockAiQuestionGenerationWorks() throws Exception {
        InterviewGenerationRequest req = InterviewGenerationRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(jobA.getId())
                .difficulty("INTERMEDIATE")
                .build();

        mockMvc.perform(post("/api/ai/interview/generate")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(aiProvider, atLeastOnce()).generateCompletion(anyString(), anyString());
    }

    // =========================================================================
    // Scenario 10: Structured JSON parsed
    // =========================================================================
    @Test
    @DisplayName("Scenario 10: Structured JSON output parsed into question models")
    void test10_structuredJsonParsed() throws Exception {
        InterviewGenerationRequest req = InterviewGenerationRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(jobA.getId())
                .difficulty("INTERMEDIATE")
                .build();

        mockMvc.perform(post("/api/ai/interview/generate")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].question").value(containsString("Java memory management")))
                .andExpect(jsonPath("$.questions[0].category").value("JAVA"))
                .andExpect(jsonPath("$.questions[0].categoryDisplayName").value("Java"))
                .andExpect(jsonPath("$.questions[1].category").value("DSA"));
    }

    // =========================================================================
    // Scenario 11: Questions persisted
    // =========================================================================
    @Test
    @DisplayName("Scenario 11: Generated questions are persisted with server-assigned difficulty")
    void test11_questionsPersisted() throws Exception {
        InterviewGenerationRequest req = InterviewGenerationRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(jobA.getId())
                .difficulty("INTERMEDIATE")
                .build();

        mockMvc.perform(post("/api/ai/interview/generate")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        List<InterviewSession> sessions = interviewSessionRepository.findByUserOrderByCreatedAtDesc(userA);
        assertEquals(1, sessions.size());
        InterviewSession savedSession = sessions.get(0);
        assertEquals(InterviewDifficulty.INTERMEDIATE, savedSession.getDifficulty());

        List<InterviewQuestion> questions = interviewQuestionRepository.findBySessionOrderByCreatedAtAsc(savedSession);
        assertEquals(10, questions.size());
        for (InterviewQuestion q : questions) {
            assertEquals(InterviewDifficulty.INTERMEDIATE, q.getDifficulty());
            assertNotNull(q.getCategory());
            assertNotNull(q.getQuestion());
        }
    }

    // =========================================================================
    // Scenario 12: Expected concepts persisted
    // =========================================================================
    @Test
    @DisplayName("Scenario 12: Expected concepts list is persisted and retrieved")
    void test12_expectedConceptsPersisted() throws Exception {
        InterviewGenerationRequest req = InterviewGenerationRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(jobA.getId())
                .difficulty("INTERMEDIATE")
                .build();

        mockMvc.perform(post("/api/ai/interview/generate")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        List<InterviewQuestion> questions = interviewQuestionRepository.findAll();
        assertFalse(questions.isEmpty());
        InterviewQuestion firstQuestion = questions.get(0);
        assertNotNull(firstQuestion.getExpectedConcepts());
        assertFalse(firstQuestion.getExpectedConcepts().isEmpty());
        assertTrue(firstQuestion.getExpectedConcepts().contains("JVM Heap"));
    }

    // =========================================================================
    // Scenario 13: Malformed AI response handled gracefully (no 500 crash)
    // =========================================================================
    @Test
    @DisplayName("Scenario 13: Malformed AI response handled gracefully (502 Bad Gateway, no partial save)")
    void test13_malformedAiResponse_HandledGracefully() throws Exception {
        when(aiProvider.generateCompletion(anyString(), anyString())).thenReturn("{ invalid_json: true }");

        InterviewGenerationRequest req = InterviewGenerationRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(jobA.getId())
                .difficulty("INTERMEDIATE")
                .build();

        mockMvc.perform(post("/api/ai/interview/generate")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(containsString("malformed JSON")));

        // Verify transaction rolled back: no partial session or question persisted!
        assertEquals(0, interviewSessionRepository.count());
        assertEquals(0, interviewQuestionRepository.count());
    }

    // =========================================================================
    // Scenario 14: AI unavailable handled (returns 503)
    // =========================================================================
    @Test
    @DisplayName("Scenario 14: Unconfigured AI provider returns 503 Service Unavailable with exact message")
    void test14_aiUnavailable_Returns503() throws Exception {
        when(aiProvider.isConfigured()).thenReturn(false);

        InterviewGenerationRequest req = InterviewGenerationRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(jobA.getId())
                .difficulty("INTERMEDIATE")
                .build();

        mockMvc.perform(post("/api/ai/interview/generate")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("AI interview generation is unavailable because the AI provider is not configured. Configure the AI provider to generate personalized interview questions."));

        assertEquals(0, interviewSessionRepository.count());
    }

    // =========================================================================
    // Scenario 15: User can retrieve own session
    // =========================================================================
    @Test
    @DisplayName("Scenario 15: User retrieves complete session with questions by ID")
    void test15_userCanRetrieveOwnSession() throws Exception {
        InterviewGenerationRequest req = InterviewGenerationRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(jobA.getId())
                .difficulty("INTERMEDIATE")
                .build();

        String responseStr = mockMvc.perform(post("/api/ai/interview/generate")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(responseStr);
        long createdId = root.get("id").asLong();

        mockMvc.perform(get("/api/ai/interview/" + createdId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdId))
                .andExpect(jsonPath("$.questions", hasSize(10)));
    }

    // =========================================================================
    // Scenario 16: User cannot retrieve another user's session (403)
    // =========================================================================
    @Test
    @DisplayName("Scenario 16: User B cannot retrieve User A's session (403 Forbidden); Admin can")
    void test16_userCannotRetrieveAnotherUserSession_RejectedWith403() throws Exception {
        InterviewSession sessionA = interviewSessionRepository.save(InterviewSession.builder()
                .user(userA)
                .resume(resumeA)
                .jobDescription(jobA)
                .difficulty(InterviewDifficulty.INTERMEDIATE)
                .build());

        // User B attempts access -> 403
        mockMvc.perform(get("/api/ai/interview/" + sessionA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // Admin can access -> 200
        mockMvc.perform(get("/api/ai/interview/" + sessionA.getId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionA.getId()));
    }

    // =========================================================================
    // Scenario 17: User can list own sessions (lightweight summaries)
    // =========================================================================
    @Test
    @DisplayName("Scenario 17: User lists own sessions as lightweight summaries without questions array")
    void test17_userCanListOwnSessions() throws Exception {
        InterviewGenerationRequest req = InterviewGenerationRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(jobA.getId())
                .difficulty("INTERMEDIATE")
                .build();

        mockMvc.perform(post("/api/ai/interview/generate")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // User A lists sessions -> 1 session with questionCount=10, but NO questions field
        mockMvc.perform(get("/api/ai/interview")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].questionCount").value(10))
                .andExpect(jsonPath("$[0].jobTitle").value("Senior Cloud Architect"))
                .andExpect(jsonPath("$[0].questions").doesNotExist());

        // User B lists sessions -> empty
        mockMvc.perform(get("/api/ai/interview")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // =========================================================================
    // Scenario 18: User can delete own session
    // =========================================================================
    @Test
    @DisplayName("Scenario 18: User deletes own session; subsequent get returns 404")
    void test18_userCanDeleteOwnSession() throws Exception {
        InterviewGenerationRequest req = InterviewGenerationRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(jobA.getId())
                .difficulty("INTERMEDIATE")
                .build();

        String responseStr = mockMvc.perform(post("/api/ai/interview/generate")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(responseStr);
        long createdId = root.get("id").asLong();

        mockMvc.perform(delete("/api/ai/interview/" + createdId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Interview session deleted successfully"));

        mockMvc.perform(get("/api/ai/interview/" + createdId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Scenario 19: User cannot delete another user's session (403)
    // =========================================================================
    @Test
    @DisplayName("Scenario 19: User B cannot delete User A's session (403 Forbidden)")
    void test19_userCannotDeleteAnotherUserSession_RejectedWith403() throws Exception {
        InterviewSession sessionA = interviewSessionRepository.save(InterviewSession.builder()
                .user(userA)
                .resume(resumeA)
                .jobDescription(jobA)
                .difficulty(InterviewDifficulty.INTERMEDIATE)
                .build());

        mockMvc.perform(delete("/api/ai/interview/" + sessionA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        assertTrue(interviewSessionRepository.findById(sessionA.getId()).isPresent());
    }

    // =========================================================================
    // Scenario 20: AI interaction logging recorded
    // =========================================================================
    @Test
    @DisplayName("Scenario 20: AI interaction logging records INTERVIEW_GENERATION telemetry")
    void test20_aiInteractionLoggingRecorded() throws Exception {
        InterviewGenerationRequest req = InterviewGenerationRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(jobA.getId())
                .difficulty("INTERMEDIATE")
                .build();

        mockMvc.perform(post("/api/ai/interview/generate")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        List<AIInteraction> interactions = aiInteractionRepository.findByUserOrderByCreatedAtDesc(userA);
        assertFalse(interactions.isEmpty());
        AIInteraction last = interactions.get(0);
        assertEquals("INTERVIEW_GENERATION", last.getFeature());
        assertEquals("mock", last.getProvider());
        assertEquals("mock-model", last.getModel());
        assertEquals("SUCCESS", last.getStatus());
    }
}
