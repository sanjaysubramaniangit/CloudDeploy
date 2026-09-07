package com.clouddeploy.service;

import com.clouddeploy.dto.JobDescriptionRequest;
import com.clouddeploy.dto.JobDescriptionResponse;
import com.clouddeploy.entity.JobDescription;
import com.clouddeploy.entity.User;
import com.clouddeploy.exception.AccessDeniedCustomException;
import com.clouddeploy.exception.ResourceNotFoundException;
import com.clouddeploy.repository.JobDescriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class JobDescriptionService {

    private static final Logger log = LoggerFactory.getLogger(JobDescriptionService.class);

    private final JobDescriptionRepository jobDescriptionRepository;

    public JobDescriptionService(JobDescriptionRepository jobDescriptionRepository) {
        this.jobDescriptionRepository = jobDescriptionRepository;
    }

    @Transactional
    public JobDescriptionResponse createJob(JobDescriptionRequest request, User user) {
        JobDescription job = JobDescription.builder()
                .user(user)
                .title(request.getTitle().trim())
                .company(request.getCompany() != null ? request.getCompany().trim() : null)
                .description(request.getDescription().trim())
                .sourceUrl(request.getSourceUrl() != null ? request.getSourceUrl().trim() : null)
                .build();

        JobDescription saved = jobDescriptionRepository.save(job);
        log.info("Created job description id [{}] for user [{}]", saved.getId(), user.getEmail());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<JobDescriptionResponse> listJobs(User user, boolean isAdmin) {
        List<JobDescription> jobs;
        if (isAdmin) {
            jobs = jobDescriptionRepository.findAllByOrderByCreatedAtDesc();
        } else {
            jobs = jobDescriptionRepository.findByUserOrderByCreatedAtDesc(user);
        }
        return jobs.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public JobDescriptionResponse getJob(Long id, User user, boolean isAdmin) {
        JobDescription job = getAuthorizedJob(id, user, isAdmin);
        return toResponse(job);
    }

    @Transactional
    public JobDescriptionResponse updateJob(Long id, JobDescriptionRequest request, User user, boolean isAdmin) {
        JobDescription job = getAuthorizedJob(id, user, isAdmin);

        job.setTitle(request.getTitle().trim());
        job.setCompany(request.getCompany() != null ? request.getCompany().trim() : null);
        job.setDescription(request.getDescription().trim());
        job.setSourceUrl(request.getSourceUrl() != null ? request.getSourceUrl().trim() : null);

        JobDescription updated = jobDescriptionRepository.save(job);
        log.info("Updated job description id [{}] for user [{}]", id, user.getEmail());
        return toResponse(updated);
    }

    @Transactional
    public void deleteJob(Long id, User user, boolean isAdmin) {
        JobDescription job = getAuthorizedJob(id, user, isAdmin);
        jobDescriptionRepository.delete(job);
        log.info("Deleted job description id [{}] for user [{}]", id, user.getEmail());
    }

    public JobDescription getAuthorizedJob(Long id, User user, boolean isAdmin) {
        JobDescription job = jobDescriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job description not found with id: " + id));

        if (!isAdmin && !job.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedCustomException("You do not have permission to access this job description");
        }

        return job;
    }

    private JobDescriptionResponse toResponse(JobDescription job) {
        return JobDescriptionResponse.builder()
                .id(job.getId())
                .title(job.getTitle())
                .company(job.getCompany())
                .description(job.getDescription())
                .sourceUrl(job.getSourceUrl())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
