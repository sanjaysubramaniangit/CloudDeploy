package com.clouddeploy.service;

import com.clouddeploy.dto.JobMatchDetailDto;
import com.clouddeploy.dto.JobMatchRequest;
import com.clouddeploy.dto.JobMatchResponse;
import com.clouddeploy.entity.*;
import com.clouddeploy.exception.AccessDeniedCustomException;
import com.clouddeploy.exception.FileValidationException;
import com.clouddeploy.exception.ResourceNotFoundException;
import com.clouddeploy.repository.JobMatchDetailRepository;
import com.clouddeploy.repository.JobMatchRepository;
import com.clouddeploy.repository.ResumeAnalysisRepository;
import com.clouddeploy.repository.ResumeRepository;
import com.clouddeploy.service.matching.JobDescriptionParser;
import com.clouddeploy.service.matching.JobMatchExplanationService;
import com.clouddeploy.service.matching.ScoringEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class JobMatchService {

    private static final Logger log = LoggerFactory.getLogger(JobMatchService.class);

    private final JobMatchRepository jobMatchRepository;
    private final JobMatchDetailRepository jobMatchDetailRepository;
    private final ResumeRepository resumeRepository;
    private final ResumeAnalysisRepository resumeAnalysisRepository;
    private final JobDescriptionService jobDescriptionService;
    private final JobDescriptionParser jobDescriptionParser;
    private final ScoringEngine scoringEngine;
    private final JobMatchExplanationService explanationService;

    public JobMatchService(
            JobMatchRepository jobMatchRepository,
            JobMatchDetailRepository jobMatchDetailRepository,
            ResumeRepository resumeRepository,
            ResumeAnalysisRepository resumeAnalysisRepository,
            JobDescriptionService jobDescriptionService,
            JobDescriptionParser jobDescriptionParser,
            ScoringEngine scoringEngine,
            JobMatchExplanationService explanationService) {
        this.jobMatchRepository = jobMatchRepository;
        this.jobMatchDetailRepository = jobMatchDetailRepository;
        this.resumeRepository = resumeRepository;
        this.resumeAnalysisRepository = resumeAnalysisRepository;
        this.jobDescriptionService = jobDescriptionService;
        this.jobDescriptionParser = jobDescriptionParser;
        this.scoringEngine = scoringEngine;
        this.explanationService = explanationService;
    }

    @Transactional
    public JobMatchResponse matchJob(JobMatchRequest request, User user, boolean isAdmin) {
        // 1. Authorize and load resume
        Resume resume = resumeRepository.findById(request.getResumeId())
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found with id: " + request.getResumeId()));

        if (!isAdmin && !resume.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedCustomException("You do not have permission to access this resume");
        }

        // 2. Ensure resume has already been analyzed by Resume Intelligence
        ResumeAnalysis resumeAnalysis = resumeAnalysisRepository.findByResume(resume)
                .orElseThrow(() -> new FileValidationException("Resume has not been analyzed yet. Please run Resume Intelligence analysis first."));

        // 3. Authorize and load job description
        JobDescription job = jobDescriptionService.getAuthorizedJob(request.getJobDescriptionId(), user, isAdmin);

        // 4. Parse job skills (Required vs Preferred)
        List<JobDescriptionParser.ParsedJobSkill> parsedJobSkills =
                jobDescriptionParser.parseJobSkills(job.getTitle(), job.getDescription());

        // 5. Compute deterministic scoring
        ScoringEngine.ScoringResult scoringResult =
                scoringEngine.computeMatch(parsedJobSkills, resumeAnalysis, job.getTitle(), job.getDescription());

        // 6. Generate AI qualitative explanation & recommendations (or deterministic fallback if unconfigured)
        JobMatchExplanationService.ExplanationResult explanationResult =
                explanationService.generateExplanation(job.getTitle(), job.getCompany(), job.getDescription(), scoringResult, resumeAnalysis, user);

        // 7. Persist JobMatch entity
        JobMatch jobMatch = JobMatch.builder()
                .user(user)
                .resume(resume)
                .jobDescription(job)
                .overallScore(scoringResult.overallScore())
                .technicalScore(scoringResult.technicalScore())
                .cloudScore(scoringResult.cloudScore())
                .devopsScore(scoringResult.devopsScore())
                .programmingScore(scoringResult.programmingScore())
                .backendScore(scoringResult.backendScore())
                .databaseScore(scoringResult.databaseScore())
                .experienceScore(scoringResult.experienceScore())
                .summary(explanationResult.dto().getSummary())
                .strengths(explanationResult.dto().getStrengths())
                .missingSkills(scoringResult.missingSkills())
                .recommendations(explanationResult.dto().getRecommendations())
                .preparationAreas(explanationResult.dto().getPreparationAreas())
                .aiProvider(explanationResult.provider())
                .aiModel(explanationResult.model())
                .build();

        JobMatch savedMatch = jobMatchRepository.save(jobMatch);

        // 8. Persist JobMatchDetail items
        List<JobMatchDetail> savedDetails = new ArrayList<>();
        for (JobMatchDetail detail : scoringResult.details()) {
            detail.setJobMatch(savedMatch);
            savedDetails.add(jobMatchDetailRepository.save(detail));
        }
        savedMatch.setDetails(savedDetails);

        log.info("Completed job match id [{}] for user [{}], overall score: {}%", savedMatch.getId(), user.getEmail(), savedMatch.getOverallScore());
        return toResponse(savedMatch);
    }

    @Transactional(readOnly = true)
    public JobMatchResponse getMatch(Long id, User user, boolean isAdmin) {
        JobMatch match = getAuthorizedMatch(id, user, isAdmin);
        return toResponse(match);
    }

    @Transactional(readOnly = true)
    public List<JobMatchResponse> listMatchesForUser(User user, boolean isAdmin) {
        List<JobMatch> matches = isAdmin
                ? jobMatchRepository.findAllByOrderByCreatedAtDesc()
                : jobMatchRepository.findByUserOrderByCreatedAtDesc(user);
        return matches.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<JobMatchResponse> listMatchesForResume(Long resumeId, User user, boolean isAdmin) {
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found with id: " + resumeId));

        if (!isAdmin && !resume.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedCustomException("You do not have permission to access this resume");
        }

        List<JobMatch> matches = jobMatchRepository.findByResumeOrderByCreatedAtDesc(resume);
        return matches.stream().map(this::toResponse).toList();
    }

    private JobMatch getAuthorizedMatch(Long id, User user, boolean isAdmin) {
        JobMatch match = jobMatchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job match not found with id: " + id));

        if (!isAdmin && !match.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedCustomException("You do not have permission to access this job match");
        }

        return match;
    }

    private JobMatchResponse toResponse(JobMatch match) {
        List<JobMatchDetailDto> detailDtos = new ArrayList<>();
        if (match.getDetails() != null) {
            for (JobMatchDetail d : match.getDetails()) {
                detailDtos.add(JobMatchDetailDto.builder()
                        .id(d.getId())
                        .skill(d.getSkill())
                        .category(d.getCategory())
                        .required(d.isRequired())
                        .matched(d.isMatched())
                        .matchType(d.getMatchType())
                        .confidence(d.getConfidence())
                        .evidence(d.getEvidence())
                        .recommendation(d.getRecommendation())
                        .build());
            }
        }

        return JobMatchResponse.builder()
                .id(match.getId())
                .resumeId(match.getResume().getId())
                .resumeFileName(match.getResume().getFileName())
                .jobDescriptionId(match.getJobDescription().getId())
                .jobTitle(match.getJobDescription().getTitle())
                .company(match.getJobDescription().getCompany())
                .overallScore(match.getOverallScore())
                .technicalScore(match.getTechnicalScore())
                .cloudScore(match.getCloudScore())
                .devopsScore(match.getDevopsScore())
                .programmingScore(match.getProgrammingScore())
                .backendScore(match.getBackendScore())
                .databaseScore(match.getDatabaseScore())
                .experienceScore(match.getExperienceScore())
                .summary(match.getSummary())
                .strengths(match.getStrengths())
                .missingSkills(match.getMissingSkills())
                .recommendations(match.getRecommendations())
                .preparationAreas(match.getPreparationAreas())
                .details(detailDtos)
                .aiProvider(match.getAiProvider())
                .aiModel(match.getAiModel())
                .createdAt(match.getCreatedAt())
                .updatedAt(match.getUpdatedAt())
                .build();
    }
}
