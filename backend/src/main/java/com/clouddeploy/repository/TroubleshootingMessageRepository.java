package com.clouddeploy.repository;

import com.clouddeploy.entity.TroubleshootingMessage;
import com.clouddeploy.entity.TroubleshootingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TroubleshootingMessageRepository extends JpaRepository<TroubleshootingMessage, Long> {
    List<TroubleshootingMessage> findBySessionOrderByCreatedAtAsc(TroubleshootingSession session);
    long countBySession(TroubleshootingSession session);
}
