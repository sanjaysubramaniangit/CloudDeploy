package com.clouddeploy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "job_matches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_description_id", nullable = false)
    private JobDescription jobDescription;

    @Column(name = "overall_score", nullable = false)
    private Integer overallScore;

    @Column(name = "technical_score", nullable = false)
    private Integer technicalScore;

    @Column(name = "cloud_score", nullable = false)
    private Integer cloudScore;

    @Column(name = "devops_score", nullable = false)
    private Integer devopsScore;

    @Column(name = "programming_score", nullable = false)
    private Integer programmingScore;

    @Column(name = "backend_score", nullable = false)
    private Integer backendScore;

    @Column(name = "database_score", nullable = false)
    private Integer databaseScore;

    @Column(name = "experience_score", nullable = false)
    private Integer experienceScore;

    @Lob
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Convert(converter = StringListConverter.class)
    @Lob
    @Column(name = "strengths", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> strengths = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Lob
    @Column(name = "missing_skills", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> missingSkills = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Lob
    @Column(name = "recommendations", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> recommendations = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Lob
    @Column(name = "preparation_areas", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> preparationAreas = new ArrayList<>();

    @Column(name = "ai_provider", length = 50)
    private String aiProvider;

    @Column(name = "ai_model", length = 100)
    private String aiModel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "jobMatch", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<JobMatchDetail> details = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
