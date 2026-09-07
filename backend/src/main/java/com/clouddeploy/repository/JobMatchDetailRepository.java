package com.clouddeploy.repository;

import com.clouddeploy.entity.JobMatch;
import com.clouddeploy.entity.JobMatchDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobMatchDetailRepository extends JpaRepository<JobMatchDetail, Long> {
    List<JobMatchDetail> findByJobMatchOrderByIdAsc(JobMatch jobMatch);
}
