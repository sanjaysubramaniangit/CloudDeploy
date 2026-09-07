package com.clouddeploy.service.matching;

import com.clouddeploy.entity.JobMatchDetail;
import com.clouddeploy.entity.ResumeAnalysis;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ScoringEngine {

    private final SkillNormalizationService normalizationService;

    public record ScoringResult(
            int overallScore,
            int technicalScore,
            int cloudScore,
            int devopsScore,
            int programmingScore,
            int backendScore,
            int databaseScore,
            int experienceScore,
            List<JobMatchDetail> details,
            List<String> matchedSkills,
            List<String> missingSkills
    ) {}

    public ScoringEngine(SkillNormalizationService normalizationService) {
        this.normalizationService = normalizationService;
    }

    public ScoringResult computeMatch(
            List<JobDescriptionParser.ParsedJobSkill> jobSkills,
            ResumeAnalysis resumeAnalysis,
            String jobTitle,
            String jobDescriptionText) {

        // 1. Gather all candidate skills from resume analysis
        Set<String> candidateSkills = new LinkedHashSet<>();
        if (resumeAnalysis.getProgrammingLanguages() != null) candidateSkills.addAll(resumeAnalysis.getProgrammingLanguages());
        if (resumeAnalysis.getFrameworks() != null) candidateSkills.addAll(resumeAnalysis.getFrameworks());
        if (resumeAnalysis.getCloudTechnologies() != null) candidateSkills.addAll(resumeAnalysis.getCloudTechnologies());
        if (resumeAnalysis.getDatabases() != null) candidateSkills.addAll(resumeAnalysis.getDatabases());
        if (resumeAnalysis.getDevopsTools() != null) candidateSkills.addAll(resumeAnalysis.getDevopsTools());
        if (resumeAnalysis.getTechnicalSkills() != null) candidateSkills.addAll(resumeAnalysis.getTechnicalSkills());

        // 2. Perform skill-by-skill matching
        List<JobMatchDetail> details = new ArrayList<>();
        List<String> matchedSkills = new ArrayList<>();
        List<String> missingSkills = new ArrayList<>();

        int reqTotal = 0;
        int reqMatched = 0;
        int prefTotal = 0;
        int prefMatched = 0;

        Map<String, Integer> catTotal = new HashMap<>();
        Map<String, Integer> catMatched = new HashMap<>();

        for (JobDescriptionParser.ParsedJobSkill js : jobSkills) {
            SkillNormalizationService.MatchResult mr = normalizationService.matchSkill(js.displayName(), candidateSkills);
            boolean matched = !"NONE".equals(mr.matchType());

            if (js.required()) {
                reqTotal++;
                if (matched) reqMatched++;
            } else {
                prefTotal++;
                if (matched) prefMatched++;
            }

            // Category tracking
            String category = js.category();
            catTotal.put(category, catTotal.getOrDefault(category, 0) + 1);
            if (matched) {
                catMatched.put(category, catMatched.getOrDefault(category, 0) + 1);
                matchedSkills.add(js.displayName());
            } else {
                missingSkills.add(js.displayName());
            }

            String evidenceText = matched
                    ? "Candidate demonstrated competency in " + (mr.evidence() != null ? mr.evidence() : js.displayName())
                    : "Not found in candidate resume analysis";

            JobMatchDetail detail = JobMatchDetail.builder()
                    .skill(js.displayName())
                    .category(js.category())
                    .required(js.required())
                    .matched(matched)
                    .matchType(mr.matchType())
                    .confidence(matched ? 1.0 : 0.0)
                    .evidence(evidenceText)
                    .recommendation(!matched ? "Consider acquiring hands-on practice or certification in " + js.displayName() : null)
                    .build();

            details.add(detail);
        }

        // 3. Compute C_req and C_pref
        double cReq = reqTotal > 0 ? ((double) reqMatched / reqTotal) : 1.0;
        double cPref = prefTotal > 0 ? ((double) prefMatched / prefTotal) : cReq;

        // 4. Compute Category Coverage & Category Balance Score (S_cat)
        double progScore = computeCategoryScore(catTotal.get("PROGRAMMING"), catMatched.get("PROGRAMMING"));
        double cloudScore = computeCategoryScore(catTotal.get("CLOUD"), catMatched.get("CLOUD"));
        double devopsScore = computeCategoryScore(catTotal.get("DEVOPS"), catMatched.get("DEVOPS"));
        double dbScore = computeCategoryScore(catTotal.get("DATABASE"), catMatched.get("DATABASE"));
        
        // Backend includes BACKEND and FRAMEWORK
        int backendTotal = catTotal.getOrDefault("BACKEND", 0) + catTotal.getOrDefault("FRAMEWORK", 0);
        int backendMatched = catMatched.getOrDefault("BACKEND", 0) + catMatched.getOrDefault("FRAMEWORK", 0);
        double backendScore = computeCategoryScore(backendTotal, backendMatched);

        List<Double> activeCategoryScores = new ArrayList<>();
        if (catTotal.getOrDefault("PROGRAMMING", 0) > 0) activeCategoryScores.add(progScore);
        if (catTotal.getOrDefault("CLOUD", 0) > 0) activeCategoryScores.add(cloudScore);
        if (catTotal.getOrDefault("DEVOPS", 0) > 0) activeCategoryScores.add(devopsScore);
        if (catTotal.getOrDefault("DATABASE", 0) > 0) activeCategoryScores.add(dbScore);
        if (backendTotal > 0) activeCategoryScores.add(backendScore);

        double sCat = activeCategoryScores.isEmpty()
                ? cReq
                : activeCategoryScores.stream().mapToDouble(Double::doubleValue).average().orElse(cReq);

        // 5. Evidence-based Experience Score (S_exp)
        double sExp = computeExperienceScore(jobTitle, jobDescriptionText, resumeAnalysis);

        // 6. Final Deterministic Formula:
        // Score = 0.60 * C_req + 0.20 * C_pref + 0.10 * S_exp + 0.10 * S_cat
        double rawOverall = (0.60 * cReq) + (0.20 * cPref) + (0.10 * sExp) + (0.10 * sCat);
        int overallScore = (int) Math.round(rawOverall * 100.0);
        overallScore = Math.max(0, Math.min(100, overallScore));

        int intProgScore = (int) Math.round(progScore * 100.0);
        int intCloudScore = (int) Math.round(cloudScore * 100.0);
        int intDevopsScore = (int) Math.round(devopsScore * 100.0);
        int intBackendScore = (int) Math.round(backendScore * 100.0);
        int intDbScore = (int) Math.round(dbScore * 100.0);
        int intTechnicalScore = (int) Math.round(((progScore + backendScore) / 2.0) * 100.0);
        int intExpScore = (int) Math.round(sExp * 100.0);

        return new ScoringResult(
                overallScore,
                intTechnicalScore,
                intCloudScore,
                intDevopsScore,
                intProgScore,
                intBackendScore,
                intDbScore,
                intExpScore,
                details,
                matchedSkills,
                missingSkills
        );
    }

    private double computeCategoryScore(Integer total, Integer matched) {
        if (total == null || total == 0) {
            return 1.0; // Category not evaluated in JD defaults to neutral perfect
        }
        int m = matched != null ? matched : 0;
        return (double) m / total;
    }

    private double computeExperienceScore(String jobTitle, String jobDesc, ResumeAnalysis analysis) {
        String jobText = ((jobTitle != null ? jobTitle : "") + " " + (jobDesc != null ? jobDesc : "")).toLowerCase();

        int jobSeniority = 2; // Mid/General by default
        if (jobText.contains("principal") || jobText.contains("staff") || jobText.contains("architect") || jobText.contains("8+ years") || jobText.contains("10+ years")) {
            jobSeniority = 4;
        } else if (jobText.contains("senior") || jobText.contains("sr") || jobText.contains("lead") || jobText.contains("5+ years")) {
            jobSeniority = 3;
        } else if (jobText.contains("junior") || jobText.contains("entry") || jobText.contains("intern") || jobText.contains("associate")) {
            jobSeniority = 1;
        }

        // Candidate evidence from summary & highlights
        String candidateSummary = (analysis.getSummary() != null ? analysis.getSummary() : "").toLowerCase();
        String candidateHighlights = (analysis.getExperienceHighlights() != null ? String.join(" ", analysis.getExperienceHighlights()) : "").toLowerCase();
        String candidateEvidence = candidateSummary + " " + candidateHighlights;

        int candidateSeniority = 2;
        if (candidateEvidence.contains("principal") || candidateEvidence.contains("staff") || candidateEvidence.contains("architect") || candidateEvidence.contains("director")) {
            candidateSeniority = 4;
        } else if (candidateEvidence.contains("senior") || candidateEvidence.contains("lead") || candidateEvidence.contains("sr.") || candidateEvidence.contains("8+ years") || candidateEvidence.contains("5+ years")) {
            candidateSeniority = 3;
        }

        if (candidateSeniority >= jobSeniority) {
            return 1.0;
        } else if (candidateSeniority == jobSeniority - 1) {
            return 0.75;
        } else {
            return 0.50;
        }
    }
}
