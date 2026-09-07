package com.clouddeploy.repository;

import com.clouddeploy.entity.Resume;
import com.clouddeploy.entity.ResumeAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ResumeAnalysisRepository extends JpaRepository<ResumeAnalysis, Long> {
    Optional<ResumeAnalysis> findByResume(Resume resume);
    Optional<ResumeAnalysis> findByResumeId(Long resumeId);
}
