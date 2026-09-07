package com.clouddeploy.repository;

import com.clouddeploy.entity.Application;
import com.clouddeploy.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    List<StoredFile> findByApplicationOrderByUploadedAtDesc(Application application);

    Optional<StoredFile> findByIdAndApplication(Long id, Application application);

    long countByApplication(Application application);
}
