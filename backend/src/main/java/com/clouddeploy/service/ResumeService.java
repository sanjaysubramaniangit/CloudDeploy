package com.clouddeploy.service;

import com.clouddeploy.dto.ResumeAnalysisDto;
import com.clouddeploy.dto.ResumeAnalysisResponse;
import com.clouddeploy.dto.ResumeResponse;
import com.clouddeploy.entity.Resume;
import com.clouddeploy.entity.ResumeAnalysis;
import com.clouddeploy.entity.User;
import com.clouddeploy.exception.AccessDeniedCustomException;
import com.clouddeploy.exception.FileValidationException;
import com.clouddeploy.exception.ResourceNotFoundException;
import com.clouddeploy.repository.ResumeAnalysisRepository;
import com.clouddeploy.repository.ResumeRepository;
import com.clouddeploy.service.ai.AIProvider;
import com.clouddeploy.service.ai.AIService;
import com.clouddeploy.service.ai.SensitiveDataFilterService;
import com.clouddeploy.service.ai.TextExtractionService;
import com.clouddeploy.service.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger( ResumeService.class);
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".pdf", ".docx", ".txt");

    private final ResumeRepository resumeRepository;
    private final ResumeAnalysisRepository resumeAnalysisRepository;
    private final StorageService storageService;
    private final TextExtractionService textExtractionService;
    private final SensitiveDataFilterService sensitiveDataFilterService;
    private final AIService aiService;
    private final AIProvider aiProvider;

    public ResumeService(ResumeRepository resumeRepository,
                         ResumeAnalysisRepository resumeAnalysisRepository,
                         StorageService storageService,
                         TextExtractionService textExtractionService,
                         SensitiveDataFilterService sensitiveDataFilterService,
                         AIService aiService,
                         AIProvider aiProvider) {
        this.resumeRepository = resumeRepository;
        this.resumeAnalysisRepository = resumeAnalysisRepository;
        this.storageService = storageService;
        this.textExtractionService = textExtractionService;
        this.sensitiveDataFilterService = sensitiveDataFilterService;
        this.aiService = aiService;
        this.aiProvider = aiProvider;
    }

    @Transactional
    public ResumeResponse uploadResume(MultipartFile file, User user) {
        if (file == null || file.isEmpty()) {
            throw new FileValidationException("Uploaded file cannot be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileValidationException("File size exceeds maximum permitted limit of 10MB");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new FileValidationException("File must have a valid name");
        }

        // Sanitize filename to prevent path traversal
        String baseName = Paths.get(originalFilename).getFileName().toString();
        String sanitizedFilename = baseName.replaceAll("[^a-zA-Z0-9._-]", "_");

        // Validate extension
        String lowerName = sanitizedFilename.toLowerCase();
        boolean validExtension = ALLOWED_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
        if (!validExtension) {
            throw new FileValidationException("Invalid file type. Only PDF, DOCX, and TXT files are supported.");
        }

        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        String s3Key = "users/" + user.getId() + "/resumes/" + UUID.randomUUID() + "-" + sanitizedFilename;

        try {
            byte[] fileBytes = file.getBytes();

            // 1. Upload to S3 private storage
            storageService.uploadFile(s3Key, new ByteArrayInputStream(fileBytes), fileBytes.length, contentType);
            log.info("Uploaded resume file [{}] to S3 key [{}] for user [{}]", sanitizedFilename, s3Key, user.getEmail());

            // 2. Extract text from document
            String extractedText = textExtractionService.extractText(new ByteArrayInputStream(fileBytes), contentType, sanitizedFilename);

            // 3. Persist Resume metadata and extracted text
            Resume resume = Resume.builder()
                    .user(user)
                    .fileName(sanitizedFilename)
                    .s3Key(s3Key)
                    .contentType(contentType)
                    .fileSize(file.getSize())
                    .extractedText(extractedText)
                    .build();

            Resume savedResume = resumeRepository.save(resume);
            log.info("Saved resume id [{}] for user [{}]", savedResume.getId(), user.getEmail());

            return toResumeResponse(savedResume);
        } catch (FileValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to process resume upload: {}", e.getMessage(), e);
            throw new FileValidationException("Failed to upload and process resume: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<ResumeResponse> listResumes(User user, boolean isAdmin) {
        List<Resume> resumes;
        if (isAdmin) {
            resumes = resumeRepository.findAllByOrderByCreatedAtDesc();
        } else {
            resumes = resumeRepository.findByUserOrderByCreatedAtDesc(user);
        }
        return resumes.stream().map(this::toResumeResponse).toList();
    }

    @Transactional(readOnly = true)
    public ResumeResponse getResume(Long id, User user, boolean isAdmin) {
        Resume resume = getAuthorizedResume(id, user, isAdmin);
        return toResumeResponse(resume);
    }

    @Transactional
    public ResumeAnalysisResponse analyzeResume(Long resumeId, User user, boolean isAdmin) {
        Resume resume = getAuthorizedResume(resumeId, user, isAdmin);

        if (resume.getExtractedText() == null || resume.getExtractedText().trim().isEmpty()) {
            throw new FileValidationException("Resume has no extracted text to analyze");
        }

        // Scrub sensitive credentials and tokens before dispatching to AI
        String scrubbedText = sensitiveDataFilterService.filterSensitiveData(resume.getExtractedText());

        // Perform AI analysis
        AIService.AnalysisResult result = aiService.analyzeResume(scrubbedText, user);
        ResumeAnalysisDto dto = result.dto();

        // Upsert ResumeAnalysis
        Optional<ResumeAnalysis> existingAnalysisOpt = resumeAnalysisRepository.findByResume(resume);
        ResumeAnalysis analysis = existingAnalysisOpt.orElseGet(() -> ResumeAnalysis.builder().resume(resume).build());

        analysis.setSummary(dto.getSummary());
        analysis.setTechnicalSkills(dto.getTechnicalSkills() != null ? dto.getTechnicalSkills() : new ArrayList<>());
        analysis.setProgrammingLanguages(dto.getProgrammingLanguages() != null ? dto.getProgrammingLanguages() : new ArrayList<>());
        analysis.setFrameworks(dto.getFrameworks() != null ? dto.getFrameworks() : new ArrayList<>());
        analysis.setCloudTechnologies(dto.getCloudTechnologies() != null ? dto.getCloudTechnologies() : new ArrayList<>());
        analysis.setDatabases(dto.getDatabases() != null ? dto.getDatabases() : new ArrayList<>());
        analysis.setDevopsTools(dto.getDevopsTools() != null ? dto.getDevopsTools() : new ArrayList<>());
        analysis.setExperienceHighlights(dto.getExperienceHighlights() != null ? dto.getExperienceHighlights() : new ArrayList<>());
        analysis.setStrengths(dto.getStrengths() != null ? dto.getStrengths() : new ArrayList<>());
        analysis.setAreasToImprove(dto.getAreasToImprove() != null ? dto.getAreasToImprove() : new ArrayList<>());
        analysis.setRecommendedSkills(dto.getRecommendedSkills() != null ? dto.getRecommendedSkills() : new ArrayList<>());
        analysis.setRawJson(result.rawJson());
        analysis.setAiProvider(aiProvider.getProviderName());
        analysis.setAiModel(aiProvider.getModelName());
        analysis.setAnalyzedAt(LocalDateTime.now());

        ResumeAnalysis savedAnalysis = resumeAnalysisRepository.save(analysis);
        resume.setAnalysis(savedAnalysis);
        resumeRepository.save(resume);

        return toResumeAnalysisResponse(savedAnalysis, resume);
    }

    @Transactional(readOnly = true)
    public ResumeAnalysisResponse getAnalysis(Long resumeId, User user, boolean isAdmin) {
        Resume resume = getAuthorizedResume(resumeId, user, isAdmin);
        ResumeAnalysis analysis = resumeAnalysisRepository.findByResume(resume)
                .orElseThrow(() -> new ResourceNotFoundException("No analysis found for resume id: " + resumeId));

        return toResumeAnalysisResponse(analysis, resume);
    }

    @Transactional
    public void deleteResume(Long id, User user, boolean isAdmin) {
        Resume resume = getAuthorizedResume(id, user, isAdmin);

        try {
            storageService.deleteFile(resume.getS3Key());
            log.info("Deleted S3 object [{}] for resume [{}]", resume.getS3Key(), id);
        } catch (Exception e) {
            log.warn("Could not delete S3 object [{}] during resume deletion: {}", resume.getS3Key(), e.getMessage());
        }

        resumeRepository.delete(resume);
        log.info("Deleted resume [{}] from database for user [{}]", id, user.getEmail());
    }

    private Resume getAuthorizedResume(Long id, User user, boolean isAdmin) {
        Resume resume = resumeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found with id: " + id));

        if (!isAdmin && !resume.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedCustomException("You do not have permission to access this resume");
        }

        return resume;
    }

    private ResumeResponse toResumeResponse(Resume resume) {
        String text = resume.getExtractedText();
        String snippet = "";
        if (text != null) {
            snippet = text.length() > 300 ? text.substring(0, 300) + "..." : text;
        }

        return ResumeResponse.builder()
                .id(resume.getId())
                .fileName(resume.getFileName())
                .contentType(resume.getContentType())
                .fileSize(resume.getFileSize())
                .extractedSnippet(snippet)
                .hasAnalysis(resume.getAnalysis() != null || resumeAnalysisRepository.findByResume(resume).isPresent())
                .createdAt(resume.getCreatedAt())
                .updatedAt(resume.getUpdatedAt())
                .build();
    }

    private ResumeAnalysisResponse toResumeAnalysisResponse(ResumeAnalysis analysis, Resume resume) {
        return ResumeAnalysisResponse.builder()
                .id(analysis.getId())
                .resumeId(resume.getId())
                .fileName(resume.getFileName())
                .summary(analysis.getSummary())
                .technicalSkills(analysis.getTechnicalSkills())
                .programmingLanguages(analysis.getProgrammingLanguages())
                .frameworks(analysis.getFrameworks())
                .cloudTechnologies(analysis.getCloudTechnologies())
                .databases(analysis.getDatabases())
                .devopsTools(analysis.getDevopsTools())
                .experienceHighlights(analysis.getExperienceHighlights())
                .strengths(analysis.getStrengths())
                .areasToImprove(analysis.getAreasToImprove())
                .recommendedSkills(analysis.getRecommendedSkills())
                .rawJson(analysis.getRawJson())
                .aiProvider(analysis.getAiProvider())
                .aiModel(analysis.getAiModel())
                .analyzedAt(analysis.getAnalyzedAt())
                .build();
    }
}
