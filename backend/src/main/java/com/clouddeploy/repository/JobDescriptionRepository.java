package com.clouddeploy.repository;

import com.clouddeploy.entity.JobDescription;
import com.clouddeploy.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobDescriptionRepository extends JpaRepository<JobDescription, Long> {
    List<JobDescription> findByUserOrderByCreatedAtDesc(User user);
    Optional<JobDescription> findByIdAndUser(Long id, User user);
    List<JobDescription> findAllByOrderByCreatedAtDesc();
    long countByUser(User user);
}
