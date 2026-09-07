package com.clouddeploy.repository;

import com.clouddeploy.entity.TroubleshootingSession;
import com.clouddeploy.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TroubleshootingSessionRepository extends JpaRepository<TroubleshootingSession, Long> {
    List<TroubleshootingSession> findByUserOrderByUpdatedAtDesc(User user);
    List<TroubleshootingSession> findAllByOrderByUpdatedAtDesc();
}
