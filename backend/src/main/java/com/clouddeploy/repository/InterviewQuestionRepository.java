package com.clouddeploy.repository;

import com.clouddeploy.entity.InterviewQuestion;
import com.clouddeploy.entity.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Long> {
    List<InterviewQuestion> findBySessionOrderByCreatedAtAsc(InterviewSession session);
}
