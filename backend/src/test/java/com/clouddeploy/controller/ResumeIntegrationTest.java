package com.clouddeploy.controller;

import com.clouddeploy.dto.ResumeAnalysisRequest;
import com.clouddeploy.entity.Resume;
import com.clouddeploy.entity.ResumeAnalysis;
import com.clouddeploy.entity.Role;
import com.clouddeploy.entity.User;
import com.clouddeploy.repository.*;
import com.clouddeploy.security.JwtService;
import com.clouddeploy.service.ai.AIProvider;
import com.clouddeploy.service.storage.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ResumeIntegrationTest {

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
    private JobMatchDetailRepository jobMatchDetailRepository;

    @Autowired
    private JobMatchRepository jobMatchRepository;

    @Autowired
    private JobDescriptionRepository jobDescriptionRepository;

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

    private static final String SAMPLE_VALID_ANALYSIS_JSON = """
    {
      "summary": "Accomplished Senior Cloud Architect with 8+ years experience building distributed AWS systems and resilient microservices.",
      "technicalSkills": ["Java", "Spring Boot", "AWS", "Docker", "Kubernetes", "Microservices"],
      "programmingLanguages": ["Java", "Python", "TypeScript"],
      "frameworks": ["Spring Boot", "React", "Node.js"],
      "cloudTechnologies": ["AWS S3", "AWS EC2", "AWS Lambda", "AWS RDS"],
      "databases": ["MySQL", "PostgreSQL", "Redis"],
      "devopsTools": ["Docker", "Kubernetes", "Terraform", "GitHub Actions"],
      "experienceHighlights": [
        "Architected multi-region cloud deployment on AWS serving 5M requests daily",
        "Reduced cloud infrastructure costs by 35% through containerization and rightsizing"
      ],
      "strengths": [
        "Deep expertise in cloud-native distributed architecture",
        "Strong security automation and infrastructure-as-code mastery"
      ],
      "areasToImprove": [
        "Limited exposure to multi-cloud architectures (Azure / GCP)"
      ],
      "recommendedSkills": [
        "Apache Kafka event streaming",
        "OpenTelemetry distributed tracing"
      ]
    }
    """;

    @BeforeEach
    void setUp() {
        cleanDatabases();

        userA = userRepository.save(User.builder()
                .name("User A")
                .email("usera_resume@example.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build());

        userB = userRepository.save(User.builder()
                .name("User B")
                .email("userb_resume@example.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build());

        adminUser = userRepository.save(User.builder()
                .name("Admin User")
                .email("admin_resume@example.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.ADMIN)
                .build());

        tokenA = jwtService.generateToken(userA);
        tokenB = jwtService.generateToken(userB);
        tokenAdmin = jwtService.generateToken(adminUser);

        when(storageService.isConfigured()).thenReturn(true);
        when(storageService.uploadFile(anyString(), any(InputStream.class), anyLong(), anyString()))
                .thenReturn("https://test-clouddeploy-bucket.s3.us-east-1.amazonaws.com/test-key");

        when(aiProvider.isConfigured()).thenReturn(true);
        when(aiProvider.getProviderName()).thenReturn("mock");
        when(aiProvider.getModelName()).thenReturn("mock-model");
        when(aiProvider.generateCompletion(anyString(), anyString())).thenReturn(SAMPLE_VALID_ANALYSIS_JSON);
    }

    @AfterEach
    void tearDown() {
        cleanDatabases();
    }

    private void cleanDatabases() {
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

    private byte[] createTestPdf(String text) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createTestDocx(String text) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun r = p.createRun();
            r.setText(text);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            return baos.toByteArray();
        }
    }

    // 1. Upload valid PDF
    @Test
    @DisplayName("1. Upload valid PDF resume successfully extracts text and saves to S3 and database")
    void uploadResume_validPdf_success() throws Exception {
        byte[] pdfBytes = createTestPdf("Jane Doe - Senior Full Stack Cloud Engineer - Skills: Java, Spring Boot, AWS, Docker");
        MockMultipartFile file = new MockMultipartFile("file", "jane_doe_resume.pdf", "application/pdf", pdfBytes);

        mockMvc.perform(multipart("/api/resumes/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.fileName", is("jane_doe_resume.pdf")))
                .andExpect(jsonPath("$.contentType", is("application/pdf")))
                .andExpect(jsonPath("$.extractedSnippet", containsString("Jane Doe")))
                .andExpect(jsonPath("$.hasAnalysis", is(false)));

        verify(storageService, times(1)).uploadFile(anyString(), any(InputStream.class), eq((long) pdfBytes.length), eq("application/pdf"));
        assertEquals(1, resumeRepository.countByUser(userA));
    }

    // 2. Upload valid DOCX
    @Test
    @DisplayName("2. Upload valid DOCX resume successfully extracts text and saves")
    void uploadResume_validDocx_success() throws Exception {
        byte[] docxBytes = createTestDocx("Alex Smith - Lead DevOps Engineer - Skills: Kubernetes, Terraform, AWS, CI/CD");
        MockMultipartFile file = new MockMultipartFile("file", "alex_smith_cv.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes);

        mockMvc.perform(multipart("/api/resumes/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.fileName", is("alex_smith_cv.docx")))
                .andExpect(jsonPath("$.extractedSnippet", containsString("Alex Smith")));

        assertEquals(1, resumeRepository.countByUser(userA));
    }

    // 3. Upload valid TXT
    @Test
    @DisplayName("3. Upload valid TXT resume successfully extracts text and saves")
    void uploadResume_validTxt_success() throws Exception {
        byte[] txtBytes = "Carlos Ray\nSoftware Architect\nExpertise: Distributed Systems, Java, Microservices".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "carlos_ray_resume.txt", "text/plain", txtBytes);

        mockMvc.perform(multipart("/api/resumes/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.fileName", is("carlos_ray_resume.txt")))
                .andExpect(jsonPath("$.extractedSnippet", containsString("Carlos Ray")));

        assertEquals(1, resumeRepository.countByUser(userA));
    }

    // 4. Unsupported extension returns 400
    @Test
    @DisplayName("4. Upload unsupported file extension returns 400 Bad Request")
    void uploadResume_unsupportedExtension_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "malicious_script.sh", "application/x-sh",
                "echo 'hacked'".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/resumes/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Only PDF, DOCX, and TXT files are supported")));
    }

    // 5. Empty file returns 400
    @Test
    @DisplayName("5. Upload empty file returns 400 Bad Request")
    void uploadResume_emptyFile_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "empty_resume.txt", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/api/resumes/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot be empty")));
    }

    // 6. Oversized file returns 400
    @Test
    @DisplayName("6. Upload file exceeding 10MB limit returns 400 Bad Request")
    void uploadResume_oversizedFile_returns400() throws Exception {
        byte[] largeBytes = new byte[11 * 1024 * 1024]; // 11MB
        MockMultipartFile file = new MockMultipartFile("file", "huge_resume.txt", "text/plain", largeBytes);

        mockMvc.perform(multipart("/api/resumes/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest());
    }

    // 7. Unauthenticated upload returns 401
    @Test
    @DisplayName("7. Unauthenticated resume upload returns 401 Unauthorized")
    void uploadResume_unauthenticated_returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "Hello".getBytes());

        mockMvc.perform(multipart("/api/resumes/upload").file(file))
                .andExpect(status().isUnauthorized());
    }

    // 8. List resumes returns only current user's resumes
    @Test
    @DisplayName("8. List resumes isolates records per authenticated user")
    void listResumes_returnsOnlyUserResumes() throws Exception {
        resumeRepository.save(Resume.builder().user(userA).fileName("userA.pdf").s3Key("k1").fileSize(100L).extractedText("User A Resume").build());
        resumeRepository.save(Resume.builder().user(userB).fileName("userB.pdf").s3Key("k2").fileSize(200L).extractedText("User B Resume").build());

        mockMvc.perform(get("/api/resumes")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fileName", is("userA.pdf")));
    }

    // 9. Get resume owner access success
    @Test
    @DisplayName("9. Resume owner can retrieve their resume by ID")
    void getResume_ownerAccess_success() throws Exception {
        Resume resume = resumeRepository.save(Resume.builder().user(userA).fileName("userA.pdf").s3Key("k1").fileSize(100L).extractedText("User A Resume").build());

        mockMvc.perform(get("/api/resumes/" + resume.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(resume.getId().intValue())))
                .andExpect(jsonPath("$.fileName", is("userA.pdf")));
    }

    // 10. Cross-tenant access returns 403
    @Test
    @DisplayName("10. Accessing another user's resume returns 403 Forbidden")
    void getResume_otherUserAccess_returns403() throws Exception {
        Resume resume = resumeRepository.save(Resume.builder().user(userA).fileName("userA.pdf").s3Key("k1").fileSize(100L).extractedText("User A Resume").build());

        mockMvc.perform(get("/api/resumes/" + resume.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    // 11. Admin access success
    @Test
    @DisplayName("11. Admin can access any user's resume")
    void getResume_adminAccess_success() throws Exception {
        Resume resume = resumeRepository.save(Resume.builder().user(userA).fileName("userA.pdf").s3Key("k1").fileSize(100L).extractedText("User A Resume").build());

        mockMvc.perform(get("/api/resumes/" + resume.getId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(resume.getId().intValue())));
    }

    // 12. Get non-existent resume returns 404
    @Test
    @DisplayName("12. Fetching non-existent resume returns 404 Not Found")
    void getResume_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/resumes/99999")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    // 13. Delete resume cascades S3 and database
    @Test
    @DisplayName("13. Deleting resume deletes S3 object and database record")
    void deleteResume_owner_success() throws Exception {
        Resume resume = resumeRepository.save(Resume.builder().user(userA).fileName("delete_me.pdf").s3Key("s3-key-to-delete").fileSize(100L).extractedText("Text").build());

        mockMvc.perform(delete("/api/resumes/" + resume.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("deleted successfully")));

        verify(storageService, times(1)).deleteFile("s3-key-to-delete");
        assertTrue(resumeRepository.findById(resume.getId()).isEmpty());
    }

    // 14. Cross-tenant delete returns 403
    @Test
    @DisplayName("14. Deleting another user's resume returns 403 Forbidden")
    void deleteResume_otherUser_returns403() throws Exception {
        Resume resume = resumeRepository.save(Resume.builder().user(userA).fileName("protected.pdf").s3Key("s3-key-protected").fileSize(100L).extractedText("Text").build());

        mockMvc.perform(delete("/api/resumes/" + resume.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        assertTrue(resumeRepository.findById(resume.getId()).isPresent());
    }

    // 15. Analyze resume successfully
    @Test
    @DisplayName("15. Analyze resume with AI saves structured analysis and logs interaction")
    void analyzeResume_success_savesAnalysisAndInteraction() throws Exception {
        Resume resume = resumeRepository.save(Resume.builder()
                .user(userA)
                .fileName("cloud_engineer.pdf")
                .s3Key("key123")
                .fileSize(1000L)
                .extractedText("John Doe\nCloud Architect\nSkills: AWS, Java, Kubernetes")
                .build());

        ResumeAnalysisRequest request = new ResumeAnalysisRequest(resume.getId());

        mockMvc.perform(post("/api/ai/resume/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumeId", is(resume.getId().intValue())))
                .andExpect(jsonPath("$.summary", containsString("Senior Cloud Architect")))
                .andExpect(jsonPath("$.technicalSkills", hasItem("Java")))
                .andExpect(jsonPath("$.cloudTechnologies", hasItem("AWS S3")))
                .andExpect(jsonPath("$.aiProvider", is("mock")));

        // Verify analysis persisted in repository
        assertTrue(resumeAnalysisRepository.findByResume(resume).isPresent());

        // Verify interaction audit log recorded
        assertEquals(1, aiInteractionRepository.countByUser(userA));
    }

    // 16. Cross-tenant analyze returns 403
    @Test
    @DisplayName("16. Analyzing another user's resume returns 403 Forbidden")
    void analyzeResume_otherUser_returns403() throws Exception {
        Resume resume = resumeRepository.save(Resume.builder()
                .user(userA)
                .fileName("userA.pdf")
                .s3Key("key123")
                .fileSize(1000L)
                .extractedText("User A resume text")
                .build());

        ResumeAnalysisRequest request = new ResumeAnalysisRequest(resume.getId());

        mockMvc.perform(post("/api/ai/resume/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    // 17. Analyze non-existent resume returns 404
    @Test
    @DisplayName("17. Analyzing non-existent resume returns 404 Not Found")
    void analyzeResume_notFound_returns404() throws Exception {
        ResumeAnalysisRequest request = new ResumeAnalysisRequest(99999L);

        mockMvc.perform(post("/api/ai/resume/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    // 18. AI provider unconfigured returns 503
    @Test
    @DisplayName("18. When AI provider is unconfigured, returns clean 503 Service Unavailable")
    void analyzeResume_aiUnconfigured_returns503() throws Exception {
        when(aiProvider.isConfigured()).thenReturn(false);

        Resume resume = resumeRepository.save(Resume.builder()
                .user(userA)
                .fileName("test.pdf")
                .s3Key("key")
                .fileSize(100L)
                .extractedText("Some resume text")
                .build());

        ResumeAnalysisRequest request = new ResumeAnalysisRequest(resume.getId());

        mockMvc.perform(post("/api/ai/resume/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message", containsString("AI provider is not configured")));
    }

    // 19. Malformed AI output handled gracefully
    @Test
    @DisplayName("19. Malformed AI JSON response handled gracefully without crashing")
    void analyzeResume_malformedJsonResponse_handlesErrorGracefully() throws Exception {
        when(aiProvider.generateCompletion(anyString(), anyString())).thenReturn("Not valid JSON at all");

        Resume resume = resumeRepository.save(Resume.builder()
                .user(userA)
                .fileName("test.pdf")
                .s3Key("key")
                .fileSize(100L)
                .extractedText("Valid resume text")
                .build());

        ResumeAnalysisRequest request = new ResumeAnalysisRequest(resume.getId());

        mockMvc.perform(post("/api/ai/resume/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message", containsString("Failed to analyze resume with AI")));
    }

    // 20. Sensitive data sanitized before AI call
    @Test
    @DisplayName("20. Sensitive API tokens and passwords in resume are redacted before calling AI provider")
    void analyzeResume_sensitiveDataSanitizedBeforeAiCall() throws Exception {
        String sensitiveResume = """
        Alice Johnson
        Senior Security Engineer
        Email: alice@example.com
        OpenAI Key: sk-live12345678901234567890
        GitHub Token: ghp_abcdefghijklmnopqrstuvwxyz123456
        AWS Access: AKIA1234567890ABCDEF
        Database password: password=superSecretPassword123
        SSN: 123-45-6789
        Skills: Java, Spring Boot, Security Auditing, AWS
        """;

        Resume resume = resumeRepository.save(Resume.builder()
                .user(userA)
                .fileName("alice_security.txt")
                .s3Key("sec-key")
                .fileSize(500L)
                .extractedText(sensitiveResume)
                .build());

        ResumeAnalysisRequest request = new ResumeAnalysisRequest(resume.getId());

        mockMvc.perform(post("/api/ai/resume/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiProvider).generateCompletion(anyString(), promptCaptor.capture());

        String capturedPrompt = promptCaptor.getValue();

        // Must NOT contain sensitive credentials
        assertFalse(capturedPrompt.contains("sk-live12345678901234567890"));
        assertFalse(capturedPrompt.contains("ghp_abcdefghijklmnopqrstuvwxyz123456"));
        assertFalse(capturedPrompt.contains("AKIA1234567890ABCDEF"));
        assertFalse(capturedPrompt.contains("superSecretPassword123"));
        assertFalse(capturedPrompt.contains("123-45-6789"));

        // Must contain redaction tags
        assertTrue(capturedPrompt.contains("[REDACTED_API_KEY]"));
        assertTrue(capturedPrompt.contains("[REDACTED_GITHUB_TOKEN]"));
        assertTrue(capturedPrompt.contains("[REDACTED_AWS_KEY]"));
        assertTrue(capturedPrompt.contains("[REDACTED_PASSWORD]"));
        assertTrue(capturedPrompt.contains("[REDACTED_SSN]"));

        // Must preserve normal candidate information
        assertTrue(capturedPrompt.contains("Alice Johnson"));
        assertTrue(capturedPrompt.contains("Senior Security Engineer"));
        assertTrue(capturedPrompt.contains("alice@example.com"));
        assertTrue(capturedPrompt.contains("Spring Boot"));
    }

    // 21. Get analysis owner success
    @Test
    @DisplayName("21. Owner can fetch existing resume analysis")
    void getAnalysis_owner_success() throws Exception {
        Resume resume = resumeRepository.save(Resume.builder().user(userA).fileName("r.pdf").s3Key("k").fileSize(100L).extractedText("Text").build());
        ResumeAnalysis analysis = resumeAnalysisRepository.save(ResumeAnalysis.builder()
                .resume(resume)
                .summary("Executive Summary")
                .technicalSkills(List.of("Java", "Spring"))
                .aiProvider("mock")
                .aiModel("mock-model")
                .build());

        mockMvc.perform(get("/api/ai/resume/" + resume.getId() + "/analysis")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary", is("Executive Summary")))
                .andExpect(jsonPath("$.technicalSkills", hasItem("Java")));
    }

    // 22. Get analysis not analyzed yet returns 404
    @Test
    @DisplayName("22. Fetching analysis for unanalyzed resume returns 404 Not Found")
    void getAnalysis_notAnalyzedYet_returns404() throws Exception {
        Resume resume = resumeRepository.save(Resume.builder().user(userA).fileName("r.pdf").s3Key("k").fileSize(100L).extractedText("Text").build());

        mockMvc.perform(get("/api/ai/resume/" + resume.getId() + "/analysis")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("No analysis found")));
    }

    // 23. Cross-tenant get analysis returns 403
    @Test
    @DisplayName("23. Cross-tenant access to resume analysis returns 403 Forbidden")
    void getAnalysis_otherUser_returns403() throws Exception {
        Resume resume = resumeRepository.save(Resume.builder().user(userA).fileName("r.pdf").s3Key("k").fileSize(100L).extractedText("Text").build());
        resumeAnalysisRepository.save(ResumeAnalysis.builder()
                .resume(resume)
                .summary("Executive Summary")
                .build());

        mockMvc.perform(get("/api/ai/resume/" + resume.getId() + "/analysis")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }
}
