package com.clouddeploy.repository;

import com.clouddeploy.entity.AIInteraction;
import com.clouddeploy.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AIInteractionRepository extends JpaRepository<AIInteraction, Long> {
    List<AIInteraction> findByUserOrderByCreatedAtDesc(User user);
    long countByUser(User user);
}
