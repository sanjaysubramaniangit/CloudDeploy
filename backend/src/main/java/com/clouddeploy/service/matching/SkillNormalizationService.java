package com.clouddeploy.service.matching;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class SkillNormalizationService {

    public record SkillDefinition(String canonicalKey, String displayName, String category, List<String> aliases) {}

    public record MatchResult(String canonicalKey, String displayName, String category, String matchType, String evidence) {}

    private final Map<String, SkillDefinition> aliasToDefinition = new HashMap<>();
    private final Map<String, SkillDefinition> canonicalMap = new LinkedHashMap<>();
    private final List<SkillDefinition> allDefinitions = new ArrayList<>();

    public SkillNormalizationService() {
        initDefinitions();
    }

    private void initDefinitions() {
        // PROGRAMMING
        register("java", "Java", "PROGRAMMING", List.of("java", "core java", "java 17", "java 11", "java 8", "java 21"));
        register("python", "Python", "PROGRAMMING", List.of("python", "python3", "python 3", "py"));
        register("javascript", "JavaScript", "PROGRAMMING", List.of("javascript", "js", "ecmascript", "es6"));
        register("typescript", "TypeScript", "PROGRAMMING", List.of("typescript", "ts"));
        register("golang", "Go", "PROGRAMMING", List.of("go", "golang"));
        register("rust", "Rust", "PROGRAMMING", List.of("rust"));
        register("c", "C", "PROGRAMMING", List.of("c"));
        register("cpp", "C++", "PROGRAMMING", List.of("c++", "cpp"));
        register("csharp", "C#", "PROGRAMMING", List.of("c#", "csharp", ".net"));
        register("ruby", "Ruby", "PROGRAMMING", List.of("ruby"));
        register("sql", "SQL", "PROGRAMMING", List.of("sql", "structured query language"));

        // BACKEND
        register("rest_api", "REST APIs", "BACKEND", List.of("rest", "rest api", "rest apis", "restful", "restful apis"));
        register("graphql", "GraphQL", "BACKEND", List.of("graphql"));
        register("microservices", "Microservices", "BACKEND", List.of("microservices", "microservice", "micro-services"));
        register("grpc", "gRPC", "BACKEND", List.of("grpc"));
        register("event_driven", "Event-Driven Architecture", "BACKEND", List.of("event-driven", "kafka", "event driven", "message broker", "rabbitmq"));

        // FRAMEWORK
        register("spring_boot", "Spring Boot", "FRAMEWORK", List.of("spring boot", "springboot", "spring-boot"));
        register("spring_framework", "Spring Framework", "FRAMEWORK", List.of("spring", "spring framework", "spring cloud", "spring security"));
        register("react", "React", "FRAMEWORK", List.of("react", "react.js", "reactjs"));
        register("angular", "Angular", "FRAMEWORK", List.of("angular", "angular.js", "angularjs"));
        register("vue", "Vue.js", "FRAMEWORK", List.of("vue", "vue.js", "vuejs"));
        register("nodejs", "Node.js", "FRAMEWORK", List.of("node", "node.js", "nodejs"));
        register("express", "Express.js", "FRAMEWORK", List.of("express", "express.js", "expressjs"));
        register("django", "Django", "FRAMEWORK", List.of("django"));
        register("flask", "Flask", "FRAMEWORK", List.of("flask"));
        register("fastapi", "FastAPI", "FRAMEWORK", List.of("fastapi"));
        register("nextjs", "Next.js", "FRAMEWORK", List.of("next.js", "nextjs"));

        // DATABASE
        register("mysql", "MySQL", "DATABASE", List.of("mysql"));
        register("postgresql", "PostgreSQL", "DATABASE", List.of("postgres", "postgresql", "postgre"));
        register("mongodb", "MongoDB", "DATABASE", List.of("mongodb", "mongo"));
        register("redis", "Redis", "DATABASE", List.of("redis"));
        register("dynamodb", "DynamoDB", "DATABASE", List.of("dynamodb", "dynamo"));
        register("cassandra", "Cassandra", "DATABASE", List.of("cassandra"));
        register("sqlite", "SQLite", "DATABASE", List.of("sqlite"));
        register("oracle", "Oracle DB", "DATABASE", List.of("oracle", "oracle db"));

        // CLOUD
        register("aws", "AWS", "CLOUD", List.of("aws", "amazon web services"));
        register("aws_s3", "AWS S3", "CLOUD", List.of("s3", "aws s3", "amazon s3"));
        register("aws_ec2", "AWS EC2", "CLOUD", List.of("ec2", "aws ec2", "amazon ec2"));
        register("aws_lambda", "AWS Lambda", "CLOUD", List.of("lambda", "aws lambda", "serverless"));
        register("aws_rds", "AWS RDS", "CLOUD", List.of("rds", "aws rds"));
        register("aws_iam", "AWS IAM", "CLOUD", List.of("iam", "aws iam"));
        register("azure", "Azure", "CLOUD", List.of("azure", "microsoft azure"));
        register("gcp", "Google Cloud", "CLOUD", List.of("gcp", "google cloud", "google cloud platform"));

        // DEVOPS
        register("docker", "Docker", "DEVOPS", List.of("docker", "containers", "containerization"));
        register("kubernetes", "Kubernetes", "DEVOPS", List.of("kubernetes", "k8s"));
        register("terraform", "Terraform", "DEVOPS", List.of("terraform", "tf", "iac", "infrastructure as code"));
        register("github_actions", "GitHub Actions", "DEVOPS", List.of("github actions", "gh actions"));
        register("jenkins", "Jenkins", "DEVOPS", List.of("jenkins"));
        register("ci_cd", "CI/CD", "DEVOPS", List.of("ci/cd", "cicd", "continuous integration", "continuous deployment"));
        register("ansible", "Ansible", "DEVOPS", List.of("ansible"));
        register("helm", "Helm", "DEVOPS", List.of("helm"));
        register("linux", "Linux", "DEVOPS", List.of("linux", "unix", "bash", "shell"));

        // TOOLS
        register("git", "Git", "TOOLS", List.of("git", "github", "gitlab"));
        register("maven", "Maven", "TOOLS", List.of("maven"));
        register("gradle", "Gradle", "TOOLS", List.of("gradle"));
        register("jira", "Jira", "TOOLS", List.of("jira"));
        register("postman", "Postman", "TOOLS", List.of("postman"));
        register("webpack", "Webpack", "TOOLS", List.of("webpack"));
        register("vite", "Vite", "TOOLS", List.of("vite"));

        // OTHER
        register("system_design", "System Design", "OTHER", List.of("system design", "distributed systems", "software architecture"));
        register("agile", "Agile", "OTHER", List.of("agile", "scrum", "kanban"));
        register("tdd", "TDD", "OTHER", List.of("tdd", "unit testing", "test driven development"));
    }

    private void register(String canonicalKey, String displayName, String category, List<String> aliases) {
        SkillDefinition def = new SkillDefinition(canonicalKey, displayName, category, aliases);
        canonicalMap.put(canonicalKey, def);
        allDefinitions.add(def);
        for (String alias : aliases) {
            aliasToDefinition.put(clean(alias), def);
        }
        aliasToDefinition.put(clean(displayName), def);
    }

    private String clean(String text) {
        if (text == null) return "";
        return text.trim().toLowerCase();
    }

    /**
     * Resolves a raw string (e.g. "ReactJS", "Amazon Web Services") to a canonical skill definition.
     */
    public Optional<SkillDefinition> findDefinition(String rawSkill) {
        if (rawSkill == null || rawSkill.trim().isEmpty()) {
            return Optional.empty();
        }

        String cleaned = clean(rawSkill);
        if (aliasToDefinition.containsKey(cleaned)) {
            return Optional.of(aliasToDefinition.get(cleaned));
        }

        // Direct canonical check
        if (canonicalMap.containsKey(cleaned)) {
            return Optional.of(canonicalMap.get(cleaned));
        }

        return Optional.empty();
    }

    public List<SkillDefinition> getAllDefinitions() {
        return Collections.unmodifiableList(allDefinitions);
    }

    /**
     * Attempts to match a target job skill against a collection of candidate resume skills.
     * Respects exact matching, canonical matching, and prevents false collisions (e.g. Java != JavaScript).
     */
    public MatchResult matchSkill(String jobSkillRaw, Collection<String> candidateSkills) {
        Optional<SkillDefinition> jobDefOpt = findDefinition(jobSkillRaw);
        String jobDisplayName = jobDefOpt.map(SkillDefinition::displayName).orElse(jobSkillRaw);
        String jobCanonical = jobDefOpt.map(SkillDefinition::canonicalKey).orElse(clean(jobSkillRaw));
        String jobCategory = jobDefOpt.map(SkillDefinition::category).orElse("OTHER");

        for (String candidateRaw : candidateSkills) {
            if (candidateRaw == null || candidateRaw.trim().isEmpty()) continue;

            // 1. EXACT match (case-insensitive)
            if (candidateRaw.trim().equalsIgnoreCase(jobSkillRaw.trim())) {
                return new MatchResult(jobCanonical, jobDisplayName, jobCategory, "EXACT", candidateRaw);
            }

            // Disambiguate critical collisions
            if (isForbiddenCollision(candidateRaw, jobSkillRaw)) {
                continue;
            }

            // 2. NORMALIZED match via canonical definitions
            Optional<SkillDefinition> candidateDefOpt = findDefinition(candidateRaw);
            if (candidateDefOpt.isPresent() && jobDefOpt.isPresent()) {
                if (candidateDefOpt.get().canonicalKey().equals(jobDefOpt.get().canonicalKey())) {
                    return new MatchResult(jobCanonical, jobDisplayName, jobCategory, "NORMALIZED", candidateRaw);
                }
            }
        }

        // No match found
        return new MatchResult(jobCanonical, jobDisplayName, jobCategory, "NONE", null);
    }

    /**
     * Explicit guard against false collisions.
     */
    public boolean isForbiddenCollision(String a, String b) {
        String ca = clean(a);
        String cb = clean(b);

        // Java != JavaScript
        boolean aIsJava = ca.equals("java") || ca.equals("core java");
        boolean bIsJs = cb.equals("javascript") || cb.equals("js") || cb.equals("typescript") || cb.equals("ts");
        if (aIsJava && bIsJs) return true;

        boolean bIsJava = cb.equals("java") || cb.equals("core java");
        boolean aIsJs = ca.equals("javascript") || ca.equals("js") || ca.equals("typescript") || ca.equals("ts");
        if (bIsJava && aIsJs) return true;

        // C != C++
        if (ca.equals("c") && (cb.equals("c++") || cb.equals("cpp"))) return true;
        if (cb.equals("c") && (ca.equals("c++") || ca.equals("cpp"))) return true;

        // Spring Framework != Spring Boot
        if ((ca.equals("spring") || ca.equals("spring framework")) && (cb.equals("spring boot") || cb.equals("springboot"))) return true;
        if ((cb.equals("spring") || cb.equals("spring framework")) && (ca.equals("spring boot") || ca.equals("springboot"))) return true;

        // AWS != Azure
        if ((ca.equals("aws") || ca.equals("amazon web services")) && (cb.equals("azure") || cb.equals("microsoft azure"))) return true;
        if ((cb.equals("aws") || cb.equals("amazon web services")) && (ca.equals("azure") || ca.equals("microsoft azure"))) return true;

        // MySQL != PostgreSQL
        if (ca.equals("mysql") && (cb.equals("postgres") || cb.equals("postgresql"))) return true;
        if (cb.equals("mysql") && (ca.equals("postgres") || ca.equals("postgresql"))) return true;

        return false;
    }
}
