package com.clouddeploy.repository;

import com.clouddeploy.entity.Application;
import com.clouddeploy.entity.Deployment;
import com.clouddeploy.entity.DeploymentStatus;
import com.clouddeploy.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, Long> {

    List<Deployment> findByApplicationOrderByDeployedAtDesc(Application application);

    List<Deployment> findByApplicationOwnerOrderByDeployedAtDesc(User owner);

    long countByApplicationOwner(User owner);

    long countByApplicationOwnerAndStatus(User owner, DeploymentStatus status);

    long countByStatus(DeploymentStatus status);

    List<Deployment> findTop5ByApplicationOwnerOrderByDeployedAtDesc(User owner);

    List<Deployment> findTop5ByOrderByDeployedAtDesc();
}
