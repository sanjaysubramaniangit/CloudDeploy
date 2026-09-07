package com.clouddeploy.repository;

import com.clouddeploy.entity.Resume;
import com.clouddeploy.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResumeRepository extends JpaRepository<Resume, Long> {
    List<Resume> findByUserOrderByCreatedAtDesc(User user);
    Optional<Resume> findByIdAndUser(Long id, User user);
    List<Resume> findAllByOrderByCreatedAtDesc();
    long countByUser(User user);
}
