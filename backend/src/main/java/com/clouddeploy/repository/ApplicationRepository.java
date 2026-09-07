package com.clouddeploy.repository;

import com.clouddeploy.entity.Application;
import com.clouddeploy.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findByOwnerOrderByUpdatedAtDesc(User owner);

    List<Application> findAllByOrderByUpdatedAtDesc();

    Optional<Application> findByIdAndOwner(Long id, User owner);

    long countByOwner(User owner);

    List<Application> findTop5ByOwnerOrderByUpdatedAtDesc(User owner);

    List<Application> findTop5ByOrderByUpdatedAtDesc();
}
