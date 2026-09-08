package com.clouddeploy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "resume_analyses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumeAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false, unique = true)
    private Resume resume;

    @Lob
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Convert(converter = StringListConverter.class)
    @Lob
    @Column(name = "technical_skills", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> technicalSkills = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Lob
    @Column(name = "programming_languages", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> programmingLanguages = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Lob
    @Column(name = "frameworks", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> frameworks = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Lob
    @Column(name = "cloud_technologies", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> cloudTechnologies = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Lob
    @Column(name = "database_skills", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> databases = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Lob
    @Column(name = "devops_tools", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> devopsTools = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Lob
    @Column(name = "experience_highlights", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> experienceHighlights = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Lob
    @Column(name = "strengths", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> strengths = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Lob
    @Column(name = "areas_to_improve", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> areasToImprove = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Lob
    @Column(name = "recommended_skills", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> recommendedSkills = new ArrayList<>();

    @Lob
    @Column(name = "raw_json", columnDefinition = "LONGTEXT")
    private String rawJson;

    @Column(name = "ai_provider", length = 50)
    private String aiProvider;

    @Column(name = "ai_model", length = 100)
    private String aiModel;

    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;

    @PrePersist
    protected void onCreate() {
        if (analyzedAt == null) {
            analyzedAt = LocalDateTime.now();
        }
    }
}
