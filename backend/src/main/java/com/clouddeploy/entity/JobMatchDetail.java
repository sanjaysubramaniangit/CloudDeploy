package com.clouddeploy.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "job_match_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobMatchDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_match_id", nullable = false)
    private JobMatch jobMatch;

    @Column(nullable = false, length = 100)
    private String skill;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false)
    private boolean required;

    @Column(nullable = false)
    private boolean matched;

    @Column(name = "match_type", nullable = false, length = 30)
    private String matchType; // EXACT, NORMALIZED, NONE

    @Column(nullable = false)
    private Double confidence;

    @Column(length = 500)
    private String evidence;

    @Column(length = 500)
    private String recommendation;
}
