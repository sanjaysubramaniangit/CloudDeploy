package com.clouddeploy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "troubleshooting_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TroubleshootingMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private TroubleshootingSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TroubleshootingMessageRole role;

    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TroubleshootingSeverity severity;

    @Column(name = "root_cause", length = 1000)
    private String rootCause;

    @Convert(converter = StringListConverter.class)
    @Column(name = "remediation_steps", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> remediationSteps = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Column(name = "suggested_commands", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> suggestedCommands = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
