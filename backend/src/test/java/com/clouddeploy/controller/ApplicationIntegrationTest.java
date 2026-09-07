package com.clouddeploy.controller;

import com.clouddeploy.dto.ApplicationRequest;
import com.clouddeploy.dto.DeploymentRequest;
import com.clouddeploy.entity.Application;
import com.clouddeploy.entity.ApplicationStatus;
import com.clouddeploy.entity.Deployment;
import com.clouddeploy.entity.DeploymentStatus;
import com.clouddeploy.entity.Role;
import com.clouddeploy.entity.User;
import com.clouddeploy.repository.ApplicationRepository;
import com.clouddeploy.repository.DeploymentRepository;
import com.clouddeploy.repository.UserRepository;
import com.clouddeploy.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ApplicationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private com.clouddeploy.repository.StoredFileRepository storedFileRepository;

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User userA;
    private User userB;
    private User adminUser;
    private String tokenA;
    private String tokenB;
    private String tokenAdmin;

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
                .email("usera@example.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build());

        userB = userRepository.save(User.builder()
                .name("User B")
                .email("userb@example.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build());

        adminUser = userRepository.save(User.builder()
                .name("Admin User")
                .email("admin@example.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.ADMIN)
                .build());

        tokenA = jwtService.generateToken(userA);
        tokenB = jwtService.generateToken(userB);
        tokenAdmin = jwtService.generateToken(adminUser);
    }

    // 1. Authenticated user can create application
    @Test
    @DisplayName("1. Authenticated user can create application")
    void testCreateApplication() throws Exception {
        ApplicationRequest request = ApplicationRequest.builder()
                .name("Cloud Service App")
                .description("Production backend service")
                .repositoryUrl("https://github.com/cloud/service.git")
                .build();

        mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Cloud Service App"))
                .andExpect(jsonPath("$.deploymentStatus").value("OFFLINE"))
                .andExpect(jsonPath("$.ownerEmail").value("usera@example.com"));

        assertEquals(1, applicationRepository.countByOwner(userA));
    }

    // 2. User can list own applications
    @Test
    @DisplayName("2. User can list own applications")
    void testListOwnApplications() throws Exception {
        applicationRepository.save(Application.builder()
                .name("App One")
                .owner(userA)
                .deploymentStatus(ApplicationStatus.OFFLINE)
                .build());

        applicationRepository.save(Application.builder()
                .name("App Two")
                .owner(userA)
                .deploymentStatus(ApplicationStatus.SUCCESS)
                .build());

        // Save an application for userB
        applicationRepository.save(Application.builder()
                .name("User B App")
                .owner(userB)
                .deploymentStatus(ApplicationStatus.RUNNING)
                .build());

        mockMvc.perform(get("/api/applications")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("App One", "App Two")))
                .andExpect(jsonPath("$[*].name", not(hasItem("User B App"))));
    }

    // 3. User can retrieve own application
    @Test
    @DisplayName("3. User can retrieve own application")
    void testGetOwnApplication() throws Exception {
        Application app = applicationRepository.save(Application.builder()
                .name("App One")
                .description("Test Description")
                .owner(userA)
                .deploymentStatus(ApplicationStatus.OFFLINE)
                .build());

        mockMvc.perform(get("/api/applications/" + app.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(app.getId()))
                .andExpect(jsonPath("$.name").value("App One"))
                .andExpect(jsonPath("$.description").value("Test Description"));
    }

    // 4. User can update own application
    @Test
    @DisplayName("4. User can update own application")
    void testUpdateOwnApplication() throws Exception {
        Application app = applicationRepository.save(Application.builder()
                .name("Original Name")
                .description("Original Desc")
                .owner(userA)
                .deploymentStatus(ApplicationStatus.OFFLINE)
                .build());

        ApplicationRequest updateRequest = ApplicationRequest.builder()
                .name("Updated Name")
                .description("Updated Desc")
                .repositoryUrl("https://github.com/updated/repo")
                .build();

        mockMvc.perform(put("/api/applications/" + app.getId())
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.description").value("Updated Desc"));

        Application updated = applicationRepository.findById(app.getId()).orElseThrow();
        assertEquals("Updated Name", updated.getName());
    }

    // 5. User can delete own application (and cascading deployments)
    @Test
    @DisplayName("5. User can delete own application and cascade delete deployments")
    void testDeleteOwnApplication() throws Exception {
        Application app = applicationRepository.save(Application.builder()
                .name("App To Delete")
                .owner(userA)
                .deploymentStatus(ApplicationStatus.SUCCESS)
                .build());

        deploymentRepository.save(Deployment.builder()
                .application(app)
                .version("v1.0.0")
                .status(DeploymentStatus.SUCCESS)
                .deployedAt(LocalDateTime.now())
                .build());

        assertEquals(1, deploymentRepository.count());

        mockMvc.perform(delete("/api/applications/" + app.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        assertFalse(applicationRepository.existsById(app.getId()));
        assertEquals(0, deploymentRepository.count()); // verify cascade deletion
    }

    // 6. Invalid application data is rejected
    @Test
    @DisplayName("6. Invalid application data is rejected (400)")
    void testInvalidApplicationData() throws Exception {
        ApplicationRequest blankName = ApplicationRequest.builder()
                .name("") // blank name
                .build();

        mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));

        ApplicationRequest invalidUrl = ApplicationRequest.builder()
                .name("Valid Name")
                .repositoryUrl("ftp://invalid-url") // invalid pattern
                .build();

        mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidUrl)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));
    }

    // 7. Missing application returns 404
    @Test
    @DisplayName("7. Missing application returns 404")
    void testMissingApplicationNotFound() throws Exception {
        mockMvc.perform(get("/api/applications/999999")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    // 8. User A cannot retrieve User B application
    @Test
    @DisplayName("8. User A cannot retrieve User B application (403)")
    void testUserACannotGetApplicationB() throws Exception {
        Application appB = applicationRepository.save(Application.builder()
                .name("Private App B")
                .owner(userB)
                .deploymentStatus(ApplicationStatus.OFFLINE)
                .build());

        mockMvc.perform(get("/api/applications/" + appB.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    // 9. User A cannot update User B application
    @Test
    @DisplayName("9. User A cannot update User B application (403)")
    void testUserACannotUpdateApplicationB() throws Exception {
        Application appB = applicationRepository.save(Application.builder()
                .name("Private App B")
                .owner(userB)
                .deploymentStatus(ApplicationStatus.OFFLINE)
                .build());

        ApplicationRequest updateRequest = ApplicationRequest.builder()
                .name("Hacked Name")
                .build();

        mockMvc.perform(put("/api/applications/" + appB.getId())
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    // 10. User A cannot delete User B application
    @Test
    @DisplayName("10. User A cannot delete User B application (403)")
    void testUserACannotDeleteApplicationB() throws Exception {
        Application appB = applicationRepository.save(Application.builder()
                .name("Private App B")
                .owner(userB)
                .deploymentStatus(ApplicationStatus.OFFLINE)
                .build());

        mockMvc.perform(delete("/api/applications/" + appB.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        assertTrue(applicationRepository.existsById(appB.getId()));
    }

    // 11. User can create deployment for own application
    @Test
    @DisplayName("11. User can create deployment for own application")
    void testCreateDeploymentForOwnApplication() throws Exception {
        Application app = applicationRepository.save(Application.builder()
                .name("Deployable App")
                .owner(userA)
                .deploymentStatus(ApplicationStatus.OFFLINE)
                .build());

        DeploymentRequest request = DeploymentRequest.builder()
                .version("v1.0.0")
                .commitHash("7a8b9c0")
                .status(DeploymentStatus.SUCCESS)
                .deploymentMessage("Initial production release")
                .build();

        mockMvc.perform(post("/api/applications/" + app.getId() + "/deployments")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.version").value("v1.0.0"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.applicationName").value("Deployable App"));

        // Verify application deployment status updated
        Application updatedApp = applicationRepository.findById(app.getId()).orElseThrow();
        assertEquals(ApplicationStatus.SUCCESS, updatedApp.getDeploymentStatus());
    }

    // 12. User can list deployment history
    @Test
    @DisplayName("12. User can list deployment history")
    void testListDeploymentHistory() throws Exception {
        Application app = applicationRepository.save(Application.builder()
                .name("History App")
                .owner(userA)
                .deploymentStatus(ApplicationStatus.RUNNING)
                .build());

        deploymentRepository.save(Deployment.builder()
                .application(app)
                .version("v1.0.0")
                .status(DeploymentStatus.SUCCESS)
                .deployedAt(LocalDateTime.now().minusHours(2))
                .build());

        deploymentRepository.save(Deployment.builder()
                .application(app)
                .version("v1.1.0")
                .status(DeploymentStatus.RUNNING)
                .deployedAt(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/applications/" + app.getId() + "/deployments")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].version").value("v1.1.0"))
                .andExpect(jsonPath("$[1].version").value("v1.0.0"));
    }

    // 13. User can retrieve own deployment
    @Test
    @DisplayName("13. User can retrieve own deployment")
    void testGetOwnDeployment() throws Exception {
        Application app = applicationRepository.save(Application.builder()
                .name("App")
                .owner(userA)
                .deploymentStatus(ApplicationStatus.SUCCESS)
                .build());

        Deployment dep = deploymentRepository.save(Deployment.builder()
                .application(app)
                .version("v2.0.0")
                .commitHash("def4567")
                .status(DeploymentStatus.SUCCESS)
                .deploymentMessage("Feature complete")
                .deployedAt(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/deployments/" + dep.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dep.getId()))
                .andExpect(jsonPath("$.version").value("v2.0.0"))
                .andExpect(jsonPath("$.commitHash").value("def4567"));
    }

    // 14. User A cannot create deployment for User B application
    @Test
    @DisplayName("14. User A cannot create deployment for User B application (403)")
    void testUserACannotDeployToUserBApp() throws Exception {
        Application appB = applicationRepository.save(Application.builder()
                .name("App B")
                .owner(userB)
                .deploymentStatus(ApplicationStatus.OFFLINE)
                .build());

        DeploymentRequest request = DeploymentRequest.builder()
                .version("v1.0.0")
                .status(DeploymentStatus.SUCCESS)
                .build();

        mockMvc.perform(post("/api/applications/" + appB.getId() + "/deployments")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    // 15. User A cannot view User B deployment
    @Test
    @DisplayName("15. User A cannot view User B deployment (403)")
    void testUserACannotViewUserBDeployment() throws Exception {
        Application appB = applicationRepository.save(Application.builder()
                .name("App B")
                .owner(userB)
                .deploymentStatus(ApplicationStatus.SUCCESS)
                .build());

        Deployment depB = deploymentRepository.save(Deployment.builder()
                .application(appB)
                .version("v1.0.0")
                .status(DeploymentStatus.SUCCESS)
                .deployedAt(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/deployments/" + depB.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    // 16. Dashboard totals are correct
    @Test
    @DisplayName("16. Dashboard totals are correct")
    void testDashboardTotals() throws Exception {
        Application app1 = applicationRepository.save(Application.builder()
                .name("App 1")
                .owner(userA)
                .deploymentStatus(ApplicationStatus.SUCCESS)
                .build());

        Application app2 = applicationRepository.save(Application.builder()
                .name("App 2")
                .owner(userA)
                .deploymentStatus(ApplicationStatus.FAILED)
                .build());

        deploymentRepository.save(Deployment.builder()
                .application(app1)
                .version("v1.0.0")
                .status(DeploymentStatus.SUCCESS)
                .deployedAt(LocalDateTime.now().minusDays(1))
                .build());

        deploymentRepository.save(Deployment.builder()
                .application(app2)
                .version("v1.0.1")
                .status(DeploymentStatus.FAILED)
                .deployedAt(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApplications").value(2))
                .andExpect(jsonPath("$.totalDeployments").value(2))
                .andExpect(jsonPath("$.successfulDeployments").value(1))
                .andExpect(jsonPath("$.failedDeployments").value(1))
                .andExpect(jsonPath("$.recentApplications", hasSize(2)))
                .andExpect(jsonPath("$.recentDeployments", hasSize(2)));
    }

    // 17. Dashboard contains only authenticated user's data
    @Test
    @DisplayName("17. Dashboard contains only authenticated user's data")
    void testDashboardUserIsolation() throws Exception {
        // User A has 1 app and 1 success deployment
        Application appA = applicationRepository.save(Application.builder()
                .name("App A")
                .owner(userA)
                .deploymentStatus(ApplicationStatus.SUCCESS)
                .build());

        deploymentRepository.save(Deployment.builder()
                .application(appA)
                .version("v1.0.0")
                .status(DeploymentStatus.SUCCESS)
                .deployedAt(LocalDateTime.now())
                .build());

        // User B has 5 apps and 10 deployments
        Application appB = applicationRepository.save(Application.builder()
                .name("App B")
                .owner(userB)
                .deploymentStatus(ApplicationStatus.FAILED)
                .build());

        deploymentRepository.save(Deployment.builder()
                .application(appB)
                .version("v1.0.0")
                .status(DeploymentStatus.FAILED)
                .deployedAt(LocalDateTime.now())
                .build());

        // Verify User A only sees their own totals
        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApplications").value(1))
                .andExpect(jsonPath("$.totalDeployments").value(1))
                .andExpect(jsonPath("$.successfulDeployments").value(1))
                .andExpect(jsonPath("$.failedDeployments").value(0))
                .andExpect(jsonPath("$.recentApplications[0].name").value("App A"));
    }

    // 18. Unauthenticated user cannot access applications
    @Test
    @DisplayName("18. Unauthenticated user cannot access applications (401)")
    void testUnauthenticatedCannotAccessApplications() throws Exception {
        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // 19. Unauthenticated user cannot access deployments
    @Test
    @DisplayName("19. Unauthenticated user cannot access deployments (401)")
    void testUnauthenticatedCannotAccessDeployments() throws Exception {
        mockMvc.perform(get("/api/applications/1/deployments"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/deployments/1"))
                .andExpect(status().isUnauthorized());
    }

    // 20. ADMIN behavior follows intended authorization policy
    @Test
    @DisplayName("20. ADMIN can access and manage applications across users")
    void testAdminAuthorizationPolicy() throws Exception {
        Application userApp = applicationRepository.save(Application.builder()
                .name("User A Protected App")
                .owner(userA)
                .deploymentStatus(ApplicationStatus.OFFLINE)
                .build());

        // Admin can view User A's application
        mockMvc.perform(get("/api/applications/" + userApp.getId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("User A Protected App"));

        // Admin sees all applications in listing
        mockMvc.perform(get("/api/applications")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // Admin can delete User A's application
        mockMvc.perform(delete("/api/applications/" + userApp.getId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());

        assertFalse(applicationRepository.existsById(userApp.getId()));
    }
}
