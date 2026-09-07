package com.clouddeploy.controller;

import com.clouddeploy.dto.JobDescriptionRequest;
import com.clouddeploy.dto.JobMatchRequest;
import com.clouddeploy.entity.*;
import com.clouddeploy.repository.*;
import com.clouddeploy.security.JwtService;
import com.clouddeploy.service.ai.AIProvider;
import com.clouddeploy.service.storage.StorageService;
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
public class JobMatchIntegrationTest {

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
    private TroubleshootingMessageRepository troubleshootingMessageRepository;

    @Autowired
    private TroubleshootingSessionRepository troubleshootingSessionRepository;

    @Autowired
    private InterviewQuestionRepository interviewQuestionRepository;

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

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

    private static final String MOCK_EXPLANATION_JSON = """
    {
      "summary": "Candidate is an excellent match for this senior backend engineering role.",
      "strengths": [
        "Strong Java and Spring Boot experience",
        "Demonstrated AWS cloud capabilities"
      ],
      "missingSkills": [
        "Kubernetes orchestration"
      ],
      "recommendations": [
        "Emphasize cloud deployment automation",
        "Add certifications in Kubernetes"
      ],
      "preparationAreas": [
        "Distributed database partitioning",
        "Kubernetes ingress and service mesh"
      ]
    }
    """;

    @BeforeEach
    void setUp() {
        cleanDatabases();

        userA = userRepository.save(User.builder()
                .name("User A")
                .email("usera_job@example.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build());

        userB = userRepository.save(User.builder()
                .name("User B")
                .email("userb_job@example.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build());

        adminUser = userRepository.save(User.builder()
                .name("Admin User")
                .email("admin_job@example.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.ADMIN)
                .build());

        tokenA = jwtService.generateToken(userA);
        tokenB = jwtService.generateToken(userB);
        tokenAdmin = jwtService.generateToken(adminUser);

        // Standard analyzed resume for userA
        resumeA = resumeRepository.save(Resume.builder()
                .user(userA)
                .fileName("usera_resume.pdf")
                .s3Key("resumes/" + userA.getId() + "/usera_resume.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .extractedText("Senior Software Engineer with 6 years experience in Java, Spring Boot, AWS, Docker, PostgreSQL, React, and Git.")
                .build());

        analysisA = resumeAnalysisRepository.save(ResumeAnalysis.builder()
                .resume(resumeA)
                .aiProvider("mock")
                .aiModel("mock-model")
                .summary("Senior Engineer with 6 years experience in Java and AWS.")
                .technicalSkills(List.of("Java", "Spring Boot", "AWS", "Docker", "PostgreSQL", "React", "Git"))
                .programmingLanguages(List.of("Java", "JavaScript"))
                .frameworks(List.of("Spring Boot", "React"))
                .cloudTechnologies(List.of("AWS"))
                .databases(List.of("PostgreSQL"))
                .devopsTools(List.of("Docker", "Git"))
                .experienceHighlights(List.of("6 years architecting high-throughput Spring Boot services on AWS"))
                .strengths(List.of("Solid backend architecture", "Cloud migration experience"))
                .areasToImprove(List.of("No Kubernetes mentioned"))
                .recommendedSkills(List.of("Kubernetes", "Terraform"))
                .rawJson("{}")
                .build());

        // Default mock AI provider behavior
        when(aiProvider.isConfigured()).thenReturn(true);
        when(aiProvider.getProviderName()).thenReturn("mock");
        when(aiProvider.getModelName()).thenReturn("mock-model");
        when(aiProvider.generateCompletion(anyString(), anyString())).thenReturn(MOCK_EXPLANATION_JSON);
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
    // Scenario 1: Create job description
    // =========================================================================
    @Test
    @DisplayName("Scenario 1: Authenticated user creates job description successfully")
    void test01_createJobDescription_Success() throws Exception {
        JobDescriptionRequest req = JobDescriptionRequest.builder()
                .title("Senior Backend Engineer")
                .company("CloudCorp Inc.")
                .description("Requirements: Java, Spring Boot, AWS. Preferred: Kubernetes, Docker.")
                .sourceUrl("https://cloudcorp.io/careers/101")
                .build();

        mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Senior Backend Engineer"))
                .andExpect(jsonPath("$.company").value("CloudCorp Inc."))
                .andExpect(jsonPath("$.description").value(containsString("Java, Spring Boot")))
                .andExpect(jsonPath("$.sourceUrl").value("https://cloudcorp.io/careers/101"));
    }

    // =========================================================================
    // Scenario 2: List own jobs (and admin view)
    // =========================================================================
    @Test
    @DisplayName("Scenario 2: User lists only their own jobs; Admin sees all jobs")
    void test02_listOwnJobs_Success() throws Exception {
        jobDescriptionRepository.save(JobDescription.builder()
                .user(userA).title("Job A").company("Company A").description("Java, AWS").build());
        jobDescriptionRepository.save(JobDescription.builder()
                .user(userB).title("Job B").company("Company B").description("Python, GCP").build());

        // User A sees 1 job
        mockMvc.perform(get("/api/jobs")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Job A"));

        // User B sees 1 job
        mockMvc.perform(get("/api/jobs")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Job B"));

        // Admin sees all 2 jobs
        mockMvc.perform(get("/api/jobs")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // =========================================================================
    // Scenario 3: Retrieve own job
    // =========================================================================
    @Test
    @DisplayName("Scenario 3: User retrieves their own job description by ID")
    void test03_retrieveOwnJob_Success() throws Exception {
        JobDescription job = jobDescriptionRepository.save(JobDescription.builder()
                .user(userA).title("DevOps Engineer").company("ScaleOps").description("Terraform, AWS, Docker").build());

        mockMvc.perform(get("/api/jobs/" + job.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(job.getId()))
                .andExpect(jsonPath("$.title").value("DevOps Engineer"))
                .andExpect(jsonPath("$.company").value("ScaleOps"));
    }

    // =========================================================================
    // Scenario 4: Update own job
    // =========================================================================
    @Test
    @DisplayName("Scenario 4: User updates their own job description")
    void test04_updateOwnJob_Success() throws Exception {
        JobDescription job = jobDescriptionRepository.save(JobDescription.builder()
                .user(userA).title("Backend Engineer").company("Old Co").description("Java 8").build());

        JobDescriptionRequest updateReq = JobDescriptionRequest.builder()
                .title("Staff Backend Engineer")
                .company("New Co")
                .description("Java 17, Spring Boot, AWS Architecture")
                .sourceUrl("https://newco.com/jobs/99")
                .build();

        mockMvc.perform(put("/api/jobs/" + job.getId())
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Staff Backend Engineer"))
                .andExpect(jsonPath("$.company").value("New Co"))
                .andExpect(jsonPath("$.description").value("Java 17, Spring Boot, AWS Architecture"));
    }

    // =========================================================================
    // Scenario 5: Delete own job
    // =========================================================================
    @Test
    @DisplayName("Scenario 5: User deletes their job; subsequent access returns 404")
    void test05_deleteOwnJob_Success() throws Exception {
        JobDescription job = jobDescriptionRepository.save(JobDescription.builder()
                .user(userA).title("To Be Deleted").description("Some content").build());

        mockMvc.perform(delete("/api/jobs/" + job.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Job description deleted successfully"));

        mockMvc.perform(get("/api/jobs/" + job.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Scenario 6: Cross-user job access rejected (403)
    // =========================================================================
    @Test
    @DisplayName("Scenario 6: Cross-user job access, update, and delete are rejected with 403 Forbidden")
    void test06_crossUserJobAccess_RejectedWith403() throws Exception {
        JobDescription jobA = jobDescriptionRepository.save(JobDescription.builder()
                .user(userA).title("Secret Role").description("Confidential").build());

        // User B attempts to read User A's job
        mockMvc.perform(get("/api/jobs/" + jobA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // User B attempts to update User A's job
        JobDescriptionRequest updateReq = JobDescriptionRequest.builder()
                .title("Hacked").description("Overwritten").build();
        mockMvc.perform(put("/api/jobs/" + jobA.getId())
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());

        // User B attempts to delete User A's job
        mockMvc.perform(delete("/api/jobs/" + jobA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Scenario 7: Unauthenticated access rejected (401)
    // =========================================================================
    @Test
    @DisplayName("Scenario 7: Unauthenticated access to /api/jobs and /api/ai/job-match returns 401")
    void test07_unauthenticatedAccess_RejectedWith401() throws Exception {
        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/ai/job-match"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/ai/job-match")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Scenario 8: Create match with analyzed resume
    // =========================================================================
    @Test
    @DisplayName("Scenario 8: Successfully execute job match between analyzed resume and job description")
    void test08_createMatchWithAnalyzedResume_Success() throws Exception {
        JobDescription job = jobDescriptionRepository.save(JobDescription.builder()
                .user(userA)
                .title("Senior Java AWS Engineer")
                .company("CloudOps")
                .description("Requirements: Java, Spring Boot, AWS. Preferred: Docker, PostgreSQL.")
                .build());

        JobMatchRequest matchReq = JobMatchRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(job.getId())
                .build();

        mockMvc.perform(post("/api/ai/job-match")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(matchReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.jobTitle").value("Senior Java AWS Engineer"))
                .andExpect(jsonPath("$.company").value("CloudOps"))
                .andExpect(jsonPath("$.overallScore").isNumber())
                .andExpect(jsonPath("$.technicalScore").isNumber())
                .andExpect(jsonPath("$.cloudScore").isNumber())
                .andExpect(jsonPath("$.programmingScore").isNumber())
                .andExpect(jsonPath("$.details", not(empty())))
                .andExpect(jsonPath("$.strengths", not(empty())))
                .andExpect(jsonPath("$.recommendations", not(empty())));
    }

    // =========================================================================
    // Scenario 9: Cross-user resume matching rejected (403)
    // =========================================================================
    @Test
    @DisplayName("Scenario 9: User B cannot match using User A's resume (403 Forbidden)")
    void test09_crossUserResumeMatching_RejectedWith403() throws Exception {
        JobDescription jobB = jobDescriptionRepository.save(JobDescription.builder()
                .user(userB).title("DevOps Role").description("AWS, Docker").build());

        // User B attempts to run match referencing User A's resume
        JobMatchRequest matchReq = JobMatchRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(jobB.getId())
                .build();

        mockMvc.perform(post("/api/ai/job-match")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(matchReq)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Scenario 10: Cross-user match retrieval rejected (403)
    // =========================================================================
    @Test
    @DisplayName("Scenario 10: User B cannot retrieve User A's job match result (403 Forbidden)")
    void test10_crossUserMatchRetrieval_RejectedWith403() throws Exception {
        JobDescription job = jobDescriptionRepository.save(JobDescription.builder()
                .user(userA).title("Engineer").description("Java").build());

        JobMatch match = jobMatchRepository.save(JobMatch.builder()
                .user(userA)
                .resume(resumeA)
                .jobDescription(job)
                .overallScore(85)
                .technicalScore(90)
                .programmingScore(100)
                .backendScore(85)
                .cloudScore(85)
                .databaseScore(85)
                .devopsScore(85)
                .experienceScore(85)
                .summary("Great match")
                .build());

        // User B tries to view match
        mockMvc.perform(get("/api/ai/job-match/" + match.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // Admin can view User A's match
        mockMvc.perform(get("/api/ai/job-match/" + match.getId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(match.getId()));
    }

    // =========================================================================
    // Scenario 11: Match requires analyzed resume (returns 400 if unanalyzed)
    // =========================================================================
    @Test
    @DisplayName("Scenario 11: Job matching fails with 400 if resume has not been analyzed yet")
    void test11_matchRequiresAnalyzedResume_FailsWhenUnanalyzed() throws Exception {
        Resume unanalyzedResume = resumeRepository.save(Resume.builder()
                .user(userA)
                .fileName("unanalyzed.pdf")
                .s3Key("resumes/" + userA.getId() + "/unanalyzed.pdf")
                .contentType("application/pdf")
                .fileSize(500L)
                .extractedText("Raw text without analysis")
                .build());

        JobDescription job = jobDescriptionRepository.save(JobDescription.builder()
                .user(userA).title("Software Engineer").description("Java, Spring").build());

        JobMatchRequest req = JobMatchRequest.builder()
                .resumeId(unanalyzedResume.getId())
                .jobDescriptionId(job.getId())
                .build();

        mockMvc.perform(post("/api/ai/job-match")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Resume has not been analyzed yet")));
    }

    // =========================================================================
    // Scenario 12: Deterministic scoring produces mathematically accurate scores
    // =========================================================================
    @Test
    @DisplayName("Scenario 12: Scoring engine produces deterministic, mathematically accurate overall score")
    void test12_deterministicScoring_ProducesMathematicallyAccurateScores() throws Exception {
        // Job: 2 required skills (Java, AWS), 1 preferred skill (Docker).
        // Candidate resume has: Java, AWS, Docker, PostgreSQL, React, Git (6 years exp).
        // C_req = 2/2 = 1.0
        // C_pref = 1/1 = 1.0
        // S_exp: 6 yrs -> 6/5 = 1.0 (capped at 1.0)
        // S_cat: programming (Java 1.0), cloud (AWS 1.0), devops (Docker 1.0), database (0 in JD -> 1.0), backend (Java/Spring 1.0) -> ~1.0
        // Overall: 100 * (0.60*1.0 + 0.20*1.0 + 0.10*1.0 + 0.10*1.0) = 100%
        JobDescription perfectJob = jobDescriptionRepository.save(JobDescription.builder()
                .user(userA)
                .title("Java AWS Engineer")
                .description("Requirements:\n- Java\n- AWS\n\nNice to have:\n- Docker")
                .build());

        JobMatchRequest req = JobMatchRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(perfectJob.getId())
                .build();

        mockMvc.perform(post("/api/ai/job-match")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallScore").value(100))
                .andExpect(jsonPath("$.programmingScore").value(100))
                .andExpect(jsonPath("$.cloudScore").value(100))
                .andExpect(jsonPath("$.devopsScore").value(100));
    }

    // =========================================================================
    // Scenario 13: Skill normalization maps aliases (AWS, k8s, react, etc.)
    // =========================================================================
    @Test
    @DisplayName("Scenario 13: Skill normalization canonicalizes aliases (e.g., 'Amazon Web Services', 'k8s')")
    void test13_skillNormalization_MapsAliases() throws Exception {
        // JD mentions "Amazon Web Services" and "ReactJS"
        JobDescription aliasJob = jobDescriptionRepository.save(JobDescription.builder()
                .user(userA)
                .title("Fullstack Developer")
                .description("Requirements:\n- Amazon Web Services\n- ReactJS\n- Spring Boot")
                .build());

        JobMatchRequest req = JobMatchRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(aliasJob.getId())
                .build();

        // Resume has "AWS", "React", and "Spring Boot". All should match via normalization!
        mockMvc.perform(post("/api/ai/job-match")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details[?(@.skill == 'AWS')].matched").value(hasItem(true)))
                .andExpect(jsonPath("$.details[?(@.skill == 'React')].matched").value(hasItem(true)))
                .andExpect(jsonPath("$.details[?(@.skill == 'Spring Boot')].matched").value(hasItem(true)));
    }

    // =========================================================================
    // Scenario 14: Java vs. JavaScript distinction maintained (no collision)
    // =========================================================================
    @Test
    @DisplayName("Scenario 14: Java vs JavaScript distinction is preserved without accidental collision")
    void test14_skillNormalization_JavaVsJavaScriptDistinction() throws Exception {
        // Candidate has ONLY JavaScript in resume (no Java)
        Resume jsOnlyResume = resumeRepository.save(Resume.builder()
                .user(userA)
                .fileName("js_only.pdf")
                .s3Key("resumes/" + userA.getId() + "/js_only.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .extractedText("Frontend Engineer with JavaScript and React skills.")
                .build());

        resumeAnalysisRepository.save(ResumeAnalysis.builder()
                .resume(jsOnlyResume)
                .aiProvider("mock")
                .aiModel("mock-model")
                .summary("Frontend specialist with JavaScript.")
                .technicalSkills(List.of("JavaScript", "React", "HTML", "CSS"))
                .programmingLanguages(List.of("JavaScript"))
                .frameworks(List.of("React"))
                .rawJson("{}")
                .build());

        // Job requires Java (backend)
        JobDescription javaJob = jobDescriptionRepository.save(JobDescription.builder()
                .user(userA)
                .title("Core Java Developer")
                .description("Requirements:\n- Java\n- Spring")
                .build());

        JobMatchRequest req = JobMatchRequest.builder()
                .resumeId(jsOnlyResume.getId())
                .jobDescriptionId(javaJob.getId())
                .build();

        mockMvc.perform(post("/api/ai/job-match")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                // Java must NOT be matched by JavaScript!
                .andExpect(jsonPath("$.details[?(@.skill == 'Java')].matched").value(hasItem(false)))
                .andExpect(jsonPath("$.missingSkills").value(hasItem("Java")));
    }

    // =========================================================================
    // Scenario 15: Required skill matching computes correct coverage
    // =========================================================================
    @Test
    @DisplayName("Scenario 15: Required skills are flagged required=true and contribute to coverage")
    void test15_requiredSkillMatching_ComputesCorrectCoverage() throws Exception {
        JobDescription job = jobDescriptionRepository.save(JobDescription.builder()
                .user(userA)
                .title("Backend Specialist")
                .description("Required Qualifications:\n- Java\n- Rust\n\nNice to have:\n- Docker")
                .build());

        JobMatchRequest req = JobMatchRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(job.getId())
                .build();

        mockMvc.perform(post("/api/ai/job-match")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details[?(@.skill == 'Java')].required").value(hasItem(true)))
                .andExpect(jsonPath("$.details[?(@.skill == 'Java')].matched").value(hasItem(true)))
                .andExpect(jsonPath("$.details[?(@.skill == 'Rust')].required").value(hasItem(true)))
                .andExpect(jsonPath("$.details[?(@.skill == 'Rust')].matched").value(hasItem(false)));
    }

    // =========================================================================
    // Scenario 16: Preferred skill matching computes correct coverage
    // =========================================================================
    @Test
    @DisplayName("Scenario 16: Preferred skills are flagged required=false and accurately matched")
    void test16_preferredSkillMatching_ComputesCorrectCoverage() throws Exception {
        JobDescription job = jobDescriptionRepository.save(JobDescription.builder()
                .user(userA)
                .title("Cloud Engineer")
                .description("Required:\n- Java\n\nPreferred Qualifications:\n- Docker\n- Kubernetes")
                .build());

        JobMatchRequest req = JobMatchRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(job.getId())
                .build();

        mockMvc.perform(post("/api/ai/job-match")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details[?(@.skill == 'Docker')].required").value(hasItem(false)))
                .andExpect(jsonPath("$.details[?(@.skill == 'Docker')].matched").value(hasItem(true)))
                .andExpect(jsonPath("$.details[?(@.skill == 'Kubernetes')].required").value(hasItem(false)))
                .andExpect(jsonPath("$.details[?(@.skill == 'Kubernetes')].matched").value(hasItem(false)));
    }

    // =========================================================================
    // Scenario 17: Missing skills correctly identified
    // =========================================================================
    @Test
    @DisplayName("Scenario 17: Skills not found on candidate resume are surfaced in missingSkills list")
    void test17_missingSkills_CorrectlyIdentified() throws Exception {
        JobDescription job = jobDescriptionRepository.save(JobDescription.builder()
                .user(userA)
                .title("Data Platform Engineer")
                .description("Requirements:\n- Java\n- Apache Kafka\n- Snowflake\n- Kubernetes")
                .build());

        JobMatchRequest req = JobMatchRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(job.getId())
                .build();

        mockMvc.perform(post("/api/ai/job-match")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missingSkills").value(hasItem("Event-Driven Architecture")))
                .andExpect(jsonPath("$.missingSkills").value(hasItem("Kubernetes")));
    }

    // =========================================================================
    // Scenario 18: Mock AI explanation parsed and persisted
    // =========================================================================
    @Test
    @DisplayName("Scenario 18: Valid AI JSON explanation is parsed, returned, and persisted")
    void test18_mockAiExplanation_ParsedAndPersisted() throws Exception {
        JobDescription job = jobDescriptionRepository.save(JobDescription.builder()
                .user(userA).title("Software Engineer").description("Java, AWS").build());

        JobMatchRequest req = JobMatchRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(job.getId())
                .build();

        mockMvc.perform(post("/api/ai/job-match")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Candidate is an excellent match for this senior backend engineering role."))
                .andExpect(jsonPath("$.strengths").value(hasItem("Strong Java and Spring Boot experience")))
                .andExpect(jsonPath("$.recommendations").value(hasItem("Emphasize cloud deployment automation")))
                .andExpect(jsonPath("$.preparationAreas").value(hasItem("Distributed database partitioning")))
                .andExpect(jsonPath("$.aiProvider").value("mock"))
                .andExpect(jsonPath("$.aiModel").value("mock-model"));

        // Verify persisted in DB
        List<JobMatch> matches = jobMatchRepository.findAll();
        assertEquals(1, matches.size());
        assertEquals("mock", matches.get(0).getAiProvider());
        assertEquals("mock-model", matches.get(0).getAiModel());
    }

    // =========================================================================
    // Scenario 19: Malformed AI response handled gracefully (fallback used)
    // =========================================================================
    @Test
    @DisplayName("Scenario 19: Malformed AI output does not fail request; deterministic fallback is returned")
    void test19_malformedAiResponse_HandledGracefully() throws Exception {
        // AI returns gibberish
        when(aiProvider.generateCompletion(anyString(), anyString())).thenReturn("<<< NOT VALID JSON >>> Oops!");

        JobDescription job = jobDescriptionRepository.save(JobDescription.builder()
                .user(userA).title("Backend Engineer").description("Java, AWS").build());

        JobMatchRequest req = JobMatchRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(job.getId())
                .build();

        mockMvc.perform(post("/api/ai/job-match")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallScore").isNumber())
                .andExpect(jsonPath("$.summary").value(containsString("deterministic match")))
                .andExpect(jsonPath("$.strengths", not(empty())));
    }

    // =========================================================================
    // Scenario 20: AI unconfigured handled gracefully (shows deterministic score + fallback notice)
    // =========================================================================
    @Test
    @DisplayName("Scenario 20: Unconfigured AI provider returns 200 OK with deterministic score and fallback notice")
    void test20_aiUnconfigured_HandledGracefully() throws Exception {
        when(aiProvider.isConfigured()).thenReturn(false);

        JobDescription job = jobDescriptionRepository.save(JobDescription.builder()
                .user(userA).title("Backend Engineer").description("Java, Spring Boot").build());

        JobMatchRequest req = JobMatchRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(job.getId())
                .build();

        mockMvc.perform(post("/api/ai/job-match")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallScore").isNumber())
                .andExpect(jsonPath("$.summary").value(containsString("AI recommendations are unavailable because the AI provider is not configured")))
                .andExpect(jsonPath("$.aiProvider").value("unconfigured"))
                .andExpect(jsonPath("$.details", not(empty())));
    }

    // =========================================================================
    // Scenario 21: AI interaction audit logging recorded
    // =========================================================================
    @Test
    @DisplayName("Scenario 21: AI interaction audit logging records JOB_MATCH entries")
    void test21_aiInteractionAuditLogging_Recorded() throws Exception {
        JobDescription job = jobDescriptionRepository.save(JobDescription.builder()
                .user(userA).title("Software Engineer").description("Java, AWS").build());

        JobMatchRequest req = JobMatchRequest.builder()
                .resumeId(resumeA.getId())
                .jobDescriptionId(job.getId())
                .build();

        mockMvc.perform(post("/api/ai/job-match")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Verify audit log has JOB_MATCH row
        List<AIInteraction> interactions = aiInteractionRepository.findByUserOrderByCreatedAtDesc(userA);
        assertFalse(interactions.isEmpty());
        AIInteraction lastInteraction = interactions.get(0);
        assertEquals("JOB_MATCH", lastInteraction.getFeature());
        assertEquals("mock", lastInteraction.getProvider());
        assertEquals("mock-model", lastInteraction.getModel());
        assertEquals("SUCCESS", lastInteraction.getStatus());
    }
}
