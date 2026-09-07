package com.clouddeploy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "deployments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(nullable = false, length = 50)
    private String version;

    @Column(name = "commit_hash", length = 40)
    private String commitHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeploymentStatus status;

    @Column(name = "deployment_message", length = 1000)
    private String deploymentMessage;

    @Column(name = "deployed_at", nullable = false)
    private LocalDateTime deployedAt;

    @PrePersist
    protected void onCreate() {
        if (deployedAt == null) {
            deployedAt = LocalDateTime.now();
        }
    }
}
