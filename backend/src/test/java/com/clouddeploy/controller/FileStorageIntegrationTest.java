package com.clouddeploy.controller;

import com.clouddeploy.entity.Application;
import com.clouddeploy.entity.ApplicationStatus;
import com.clouddeploy.entity.Role;
import com.clouddeploy.entity.StoredFile;
import com.clouddeploy.entity.User;
import com.clouddeploy.exception.StorageConfigurationException;
import com.clouddeploy.repository.ApplicationRepository;
import com.clouddeploy.repository.DeploymentRepository;
import com.clouddeploy.repository.StoredFileRepository;
import com.clouddeploy.repository.UserRepository;
import com.clouddeploy.security.JwtService;
import com.clouddeploy.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class FileStorageIntegrationTest {

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
    private com.clouddeploy.repository.ResumeAnalysisRepository resumeAnalysisRepository;

    @Autowired
    private com.clouddeploy.repository.ResumeRepository resumeRepository;

    @Autowired
    private com.clouddeploy.repository.AIInteractionRepository aiInteractionRepository;

    @Autowired
    private com.clouddeploy.repository.JobMatchDetailRepository jobMatchDetailRepository;

    @Autowired
    private com.clouddeploy.repository.JobMatchRepository jobMatchRepository;

    @Autowired
    private com.clouddeploy.repository.JobDescriptionRepository jobDescriptionRepository;

    @Autowired
    private com.clouddeploy.repository.TroubleshootingMessageRepository troubleshootingMessageRepository;

    @Autowired
    private com.clouddeploy.repository.TroubleshootingSessionRepository troubleshootingSessionRepository;

    @Autowired
    private com.clouddeploy.repository.InterviewQuestionRepository interviewQuestionRepository;

    @Autowired
    private com.clouddeploy.repository.InterviewSessionRepository interviewSessionRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private StorageService storageService;

    private User userA;
    private User userB;
    private User adminUser;
    private String tokenA;
    private String tokenB;
    private String tokenAdmin;
    private Application appA;
    private Application appB;

    @BeforeEach
    void setUp() {
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

        userA = userRepository.save(User.builder()
                .name("User A")
                .email("usera_storage@example.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build());

        userB = userRepository.save(User.builder()
                .name("User B")
                .email("userb_storage@example.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build());

        adminUser = userRepository.save(User.builder()
                .name("Admin User")
                .email("admin_storage@example.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.ADMIN)
                .build());

        tokenA = jwtService.generateToken(userA);
        tokenB = jwtService.generateToken(userB);
        tokenAdmin = jwtService.generateToken(adminUser);

        appA = applicationRepository.save(Application.builder()
                .name("App A Storage")
                .owner(userA)
                .deploymentStatus(ApplicationStatus.OFFLINE)
                .build());

        appB = applicationRepository.save(Application.builder()
                .name("App B Storage")
                .owner(userB)
                .deploymentStatus(ApplicationStatus.OFFLINE)
                .build());

        // Default mock behavior
        when(storageService.isConfigured()).thenReturn(true);
        when(storageService.uploadFile(anyString(), any(InputStream.class), anyLong(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // 1. Authenticated user can upload file to own application
    @Test
    @DisplayName("1. User can upload file to own application")
    void testUploadFileToOwnApplication() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "config.yaml",
                "text/yaml",
                "server:\n  port: 8080".getBytes()
        );

        mockMvc.perform(multipart("/api/applications/" + appA.getId() + "/files")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.fileName").value("config.yaml"))
                .andExpect(jsonPath("$.contentType").value("text/yaml"))
                .andExpect(jsonPath("$.fileSize").value(file.getSize()))
                .andExpect(jsonPath("$.uploadedByEmail").value("usera_storage@example.com"));

        assertEquals(1, storedFileRepository.countByApplication(appA));
        verify(storageService, times(1)).uploadFile(anyString(), any(InputStream.class), eq(file.getSize()), eq("text/yaml"));
    }

    // 2. User can list files for own application
    @Test
    @DisplayName("2. User can list files for own application")
    void testListFilesForOwnApplication() throws Exception {
        storedFileRepository.save(StoredFile.builder()
                .application(appA)
                .fileName("deployment-spec.json")
                .s3Key("applications/" + appA.getId() + "/files/test-key-1")
                .contentType("application/json")
                .fileSize(1024L)
                .uploadedBy(userA)
                .uploadedAt(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/applications/" + appA.getId() + "/files")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fileName").value("deployment-spec.json"))
                .andExpect(jsonPath("$[0].fileSize").value(1024));
    }

    // 3. User can delete own file (S3 and DB removed)
    @Test
    @DisplayName("3. User can delete own file (storage + metadata deleted)")
    void testDeleteOwnFile() throws Exception {
        StoredFile file = storedFileRepository.save(StoredFile.builder()
                .application(appA)
                .fileName("obsolete.log")
                .s3Key("applications/" + appA.getId() + "/files/obsolete.log")
                .contentType("text/plain")
                .fileSize(500L)
                .uploadedBy(userA)
                .uploadedAt(LocalDateTime.now())
                .build());

        mockMvc.perform(delete("/api/files/" + file.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        assertFalse(storedFileRepository.existsById(file.getId()));
        verify(storageService, times(1)).deleteFile(eq("applications/" + appA.getId() + "/files/obsolete.log"));
    }

    // 4. User can generate pre-signed access URL for own file
    @Test
    @DisplayName("4. User can generate pre-signed access URL for own file")
    void testGeneratePresignedAccessUrl() throws Exception {
        StoredFile file = storedFileRepository.save(StoredFile.builder()
                .application(appA)
                .fileName("data.csv")
                .s3Key("applications/" + appA.getId() + "/files/data.csv")
                .contentType("text/csv")
                .fileSize(2048L)
                .uploadedBy(userA)
                .uploadedAt(LocalDateTime.now())
                .build());

        when(storageService.generatePresignedUrl(eq(file.getS3Key()), any(Duration.class)))
                .thenReturn("https://test-bucket.s3.amazonaws.com/applications/" + appA.getId() + "/files/data.csv?X-Amz-Signature=abc123xyz");

        mockMvc.perform(get("/api/files/" + file.getId() + "/access-url")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value(file.getId()))
                .andExpect(jsonPath("$.fileName").value("data.csv"))
                .andExpect(jsonPath("$.accessUrl", containsString("https://test-bucket.s3.amazonaws.com")))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    // 5. Unauthenticated user cannot access file endpoints
    @Test
    @DisplayName("5. Unauthenticated user cannot access file endpoints (401)")
    void testUnauthenticatedAccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "data".getBytes());

        mockMvc.perform(multipart("/api/applications/" + appA.getId() + "/files").file(file))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/applications/" + appA.getId() + "/files"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/files/1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/files/1/access-url"))
                .andExpect(status().isUnauthorized());
    }

    // 6. User A cannot upload file to User B's application
    @Test
    @DisplayName("6. User A cannot upload file to User B's application (403)")
    void testCrossUserUploadRejection() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "hacked.txt", "text/plain", "payload".getBytes());

        mockMvc.perform(multipart("/api/applications/" + appB.getId() + "/files")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(storageService, never()).uploadFile(anyString(), any(), anyLong(), anyString());
    }

    // 7. User A cannot list User B's application files
    @Test
    @DisplayName("7. User A cannot list User B's application files (403)")
    void testCrossUserListRejection() throws Exception {
        mockMvc.perform(get("/api/applications/" + appB.getId() + "/files")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    // 8. User A cannot delete User B's file
    @Test
    @DisplayName("8. User A cannot delete User B's file (403)")
    void testCrossUserDeleteRejection() throws Exception {
        StoredFile fileB = storedFileRepository.save(StoredFile.builder()
                .application(appB)
                .fileName("privateB.txt")
                .s3Key("applications/" + appB.getId() + "/files/privateB.txt")
                .contentType("text/plain")
                .fileSize(100L)
                .uploadedBy(userB)
                .uploadedAt(LocalDateTime.now())
                .build());

        mockMvc.perform(delete("/api/files/" + fileB.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        assertTrue(storedFileRepository.existsById(fileB.getId()));
        verify(storageService, never()).deleteFile(anyString());
    }

    // 9. User A cannot generate access URL for User B's file
    @Test
    @DisplayName("9. User A cannot generate access URL for User B's file (403)")
    void testCrossUserAccessUrlRejection() throws Exception {
        StoredFile fileB = storedFileRepository.save(StoredFile.builder()
                .application(appB)
                .fileName("secretB.pdf")
                .s3Key("applications/" + appB.getId() + "/files/secretB.pdf")
                .contentType("application/pdf")
                .fileSize(5000L)
                .uploadedBy(userB)
                .uploadedAt(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/files/" + fileB.getId() + "/access-url")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(storageService, never()).generatePresignedUrl(anyString(), any());
    }

    // 10. Dangerous file types are rejected
    @Test
    @DisplayName("10. Dangerous file types (.exe, .sh, .bat) are rejected (400)")
    void testDangerousFileTypeRejected() throws Exception {
        MockMultipartFile exeFile = new MockMultipartFile("file", "trojan.exe", "application/x-msdownload", "binary".getBytes());
        MockMultipartFile shFile = new MockMultipartFile("file", "script.sh", "application/x-sh", "#!/bin/bash".getBytes());
        MockMultipartFile batFile = new MockMultipartFile("file", "payload.BAT", "text/plain", "@echo off".getBytes());

        mockMvc.perform(multipart("/api/applications/" + appA.getId() + "/files")
                        .file(exeFile)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("File Validation Error"));

        mockMvc.perform(multipart("/api/applications/" + appA.getId() + "/files")
                        .file(shFile)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("File Validation Error"));

        mockMvc.perform(multipart("/api/applications/" + appA.getId() + "/files")
                        .file(batFile)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("File Validation Error"));
    }

    // 11. Oversized file is rejected
    @Test
    @DisplayName("11. Oversized file is rejected (400)")
    void testOversizedFileRejected() throws Exception {
        // Construct a file reporting size > 10MB
        byte[] largeBytes = new byte[100];
        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "big.bin",
                "application/octet-stream",
                largeBytes
        ) {
            @Override
            public long getSize() {
                return 11 * 1024 * 1024; // 11 MB
            }
        };

        mockMvc.perform(multipart("/api/applications/" + appA.getId() + "/files")
                        .file(largeFile)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("File Validation Error"))
                .andExpect(jsonPath("$.message", containsString("10MB")));
    }

    // 12. Path traversal in filename is rejected
    @Test
    @DisplayName("12. Path traversal in filename is rejected (400)")
    void testPathTraversalRejected() throws Exception {
        MockMultipartFile traversalFile1 = new MockMultipartFile("file", "../../etc/passwd", "text/plain", "root:x:0:0".getBytes());
        MockMultipartFile traversalFile2 = new MockMultipartFile("file", "..\\windows\\system32", "text/plain", "data".getBytes());

        mockMvc.perform(multipart("/api/applications/" + appA.getId() + "/files")
                        .file(traversalFile1)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("File Validation Error"))
                .andExpect(jsonPath("$.message", containsString("path traversal")));

        mockMvc.perform(multipart("/api/applications/" + appA.getId() + "/files")
                        .file(traversalFile2)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("File Validation Error"));
    }

    // 13. Safe UUID scoped S3 key and metadata persistence
    @Test
    @DisplayName("13. Safe UUID/application scoped key and metadata persistence")
    void testSafeKeyAndMetadataPersistence() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "my test report.pdf", "application/pdf", "pdf bytes".getBytes());

        mockMvc.perform(multipart("/api/applications/" + appA.getId() + "/files")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("my_test_report.pdf"));

        StoredFile persisted = storedFileRepository.findAll().get(0);
        assertTrue(persisted.getS3Key().startsWith("applications/" + appA.getId() + "/files/"));
        assertTrue(persisted.getS3Key().endsWith("-my_test_report.pdf"));
        assertEquals("application/pdf", persisted.getContentType());
        assertEquals(userA.getId(), persisted.getUploadedBy().getId());
    }

    // 14. Missing S3 configuration reports clear error (503)
    @Test
    @DisplayName("14. Missing S3 configuration reports 503 Service Unavailable")
    void testMissingS3ConfigurationReports503() throws Exception {
        doThrow(new StorageConfigurationException("AWS S3 storage is not configured. Please set AWS_REGION and AWS_S3_BUCKET."))
                .when(storageService).uploadFile(anyString(), any(), anyLong(), anyString());

        MockMultipartFile file = new MockMultipartFile("file", "valid.txt", "text/plain", "text".getBytes());

        mockMvc.perform(multipart("/api/applications/" + appA.getId() + "/files")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message", containsString("AWS S3 storage is not configured")));
    }

    // 15. ADMIN can view and delete files across applications
    @Test
    @DisplayName("15. ADMIN can view and delete files across applications")
    void testAdminFileManagement() throws Exception {
        StoredFile fileA = storedFileRepository.save(StoredFile.builder()
                .application(appA)
                .fileName("appA_file.txt")
                .s3Key("applications/" + appA.getId() + "/files/appA_file.txt")
                .contentType("text/plain")
                .fileSize(100L)
                .uploadedBy(userA)
                .uploadedAt(LocalDateTime.now())
                .build());

        // Admin can list User A's files
        mockMvc.perform(get("/api/applications/" + appA.getId() + "/files")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fileName").value("appA_file.txt"));

        // Admin can delete User A's file
        mockMvc.perform(delete("/api/files/" + fileA.getId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());

        assertFalse(storedFileRepository.existsById(fileA.getId()));
    }
}
