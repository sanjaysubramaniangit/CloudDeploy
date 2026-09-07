package com.clouddeploy.repository;

import com.clouddeploy.entity.JobMatch;
import com.clouddeploy.entity.Resume;
import com.clouddeploy.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobMatchRepository extends JpaRepository<JobMatch, Long> {
    List<JobMatch> findByUserOrderByCreatedAtDesc(User user);
    Optional<JobMatch> findByIdAndUser(Long id, User user);
    List<JobMatch> findByResumeOrderByCreatedAtDesc(Resume resume);
    List<JobMatch> findAllByOrderByCreatedAtDesc();
    long countByUser(User user);
}
