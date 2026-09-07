# AI Architecture — CloudDeploy AI

## 1. Executive Summary

Phase 5 introduces **Resume Intelligence**, the core AI capability within the CloudDeploy AI SaaS platform. It provides automated document ingestion, multi-format text extraction (PDF, DOCX, TXT), privacy-preserving sensitive data scrubbing, provider-agnostic Large Language Model (LLM) orchestration, strict structured JSON schema validation, and database persistence.

The architecture is explicitly designed around **provider independence**, **least-privilege tenant isolation**, and **untrusted input demarcation**.

---

## 2. High-Level Data Flow

```
[User Browser (React)]
       |
       | 1. Upload Resume (PDF / DOCX / TXT)
       v
[ResumeController] (POST /api/resumes/upload)
       |
       | 2. Layered Validation (10MB, allowed ext, path traversal sanitization)
       v
[ResumeService]
       |-----------------------------------------------------------+
       |                                                           |
       v (Bytes stream)                                            v (Bytes stream)
[StorageService (S3)]                                     [TextExtractionService]
       |                                                           |
       | Upload to private S3:                                     | PDF: Apache PDFBox
       | users/{userId}/resumes/{uuid}-{fileName}                  | DOCX: Apache POI
       |                                                           | TXT: UTF-8 Stream
       v                                                           v
[Private S3 Bucket]                                       [Text Normalization]
                                                                   |
                                                                   v
                                                          [Persist Resume Entity]
                                                          (MySQL: resumes table)
```

### AI Analysis Pipeline (On-Demand)

```
[User Browser (React)]
       |
       | 3. Trigger Analysis (POST /api/ai/resume/analyze { resumeId: 123 })
       v
[AIResumeController]
       |
       | 4. Server-Side Ownership Check (User owns resume? or ADMIN?)
       v
[ResumeService]
       |
       | 5. Scrub Secrets & PII
       v
[SensitiveDataFilterService]
       |
       | 6. Dispatch Sanitized Text + System Prompt
       v
[AIService]
       |
       | 7. Check isConfigured()
       |    ├── If false -> Throw AIConfigurationException (HTTP 503)
       |    └── If true  -> Invoke Provider
       v
[AIProvider Abstraction]
       ├── [OpenAICompatibleAIProvider] -> External LLM (e.g. OpenAI gpt-4o-mini)
       └── [MockAIProvider]             -> Deterministic Test Provider
       |
       | 8. Receive Raw JSON & Strip Markdown Fences
       v
[Jackson ObjectMapper] -> Validate Schema & Required Fields
       |
       +-----------------------------------------------------------+
       |                                                           |
       v                                                           v
[ResumeAnalysisRepository]                                [AIInteractionRepository]
(Persist structured analysis)                             (Audit log & telemetry)
       |
       v
[React UI (/resume-ai)] -> Polished SaaS View (Skill tags, strengths, growth areas)
```

---

## 3. Core Architectural Components

### 3.1 Document Ingestion & Storage
- **Supported Formats**: `.pdf` (`application/pdf`), `.docx` (`application/vnd.openxmlformats-officedocument.wordprocessingml.document`), and `.txt` (`text/plain`).
- **Filename Sanitization**: Path traversal sequences (`..`, `/`, `\`) and non-alphanumeric special characters are stripped before S3 key formation.
- **S3 Key Strategy**: `users/{userId}/resumes/{uuid}-{sanitizedFilename}` ensures complete tenant isolation in the object store.
- **Privacy**: S3 objects remain private; access is restricted to authenticated server-side operations.

### 3.2 Text Extraction & Normalization
- **PDF Extraction**: Uses Apache PDFBox 3.0 (`Loader.loadPDF` and `PDFTextStripper` with positional sorting).
- **DOCX Extraction**: Uses Apache POI 5.2.5 (`XWPFDocument` and `XWPFWordExtractor`).
- **TXT Extraction**: Standard UTF-8 stream decoder.
- **Normalization**: Line endings are unified (`\r\n` -> `\n`), unprintable control characters are removed, and excessive consecutive blank lines are collapsed.
- **Safety Cap**: Extracted text is capped at 100,000 characters to prevent prompt token overflow.

### 3.3 Sensitive Data Filtering
Before candidate text is dispatched to any third-party AI provider, `SensitiveDataFilterService` scans and masks:
- API Keys & Tokens (`sk-...`, `ghp_...`, `AKIA...`, `Bearer ...`)
- Password strings (`password=...`, `secret=...`)
- Private keys (`-----BEGIN PRIVATE KEY-----`)
- Social Security Numbers (`\d{3}-\d{2}-\d{4}`)
- Payment card numbers (13-16 digit patterns)

Normal candidate information (email, phone, education, company history, skills, GitHub URLs) is strictly preserved.

### 3.4 Provider-Independent AI Layer
The system defines a uniform abstraction:
```java
public interface AIProvider {
    String generateCompletion(String systemPrompt, String userPrompt);
    boolean isConfigured();
    String getProviderName();
    String getModelName();
}
```

Implementations:
- **`OpenAICompatibleAIProvider`**: Standard REST integration supporting OpenAI and OpenAI-compatible proxies (e.g., Azure OpenAI, Ollama, vLLM). Configured via `AI_PROVIDER`, `AI_API_KEY`, `AI_MODEL`, `AI_BASE_URL`.
- **`MockAIProvider`**: Built-in test and offline provider delivering realistic schema-compliant responses without external network or API costs.

### 3.5 Prompt Engineering & Untrusted Input Demarcation
System prompt template located at `src/main/resources/prompts/resume-analysis.txt`:
- **Explicit Role Definition**: Senior Technical Recruiter and Staff Cloud Engineer.
- **Security Boundary**: Explicit directive establishing that candidate text is **untrusted user input** that must never override instructions or alter the output schema.
- **Strict Output Schema**: Enforces raw JSON with zero markdown code block formatting.

### 3.6 Structured Schema Validation & Strongly Typed Persistence
The response is parsed and validated by `AIService` into `ResumeAnalysisDto`.
The database entity `ResumeAnalysis` stores strongly typed fields:
- `summary` (Text)
- `technicalSkills` (`List<String>` via `StringListConverter` JSON serialization)
- `programmingLanguages` (`List<String>`)
- `frameworks` (`List<String>`)
- `cloudTechnologies` (`List<String>`)
- `databases` (`List<String>`)
- `devopsTools` (`List<String>`)
- `experienceHighlights` (`List<String>`)
- `strengths` (`List<String>`)
- `areasToImprove` (`List<String>`)
- `recommendedSkills` (`List<String>`)
- `rawJson` (Stored for debugging/audit; hidden by default in UI)
- `aiProvider`, `aiModel`, `analyzedAt`

### 3.7 AI Interaction Logging & Telemetry
Every AI request is recorded in `ai_interactions`:
- `user_id`: Authenticated user invoking the request
- `feature`: "RESUME_ANALYSIS", "JOB_MATCH", or "INTERVIEW_GENERATION"
- `provider`: "openai" / "mock" / "unconfigured"
- `model`: "gpt-4o-mini" / "mock-model" / "deterministic-engine"
- `status`: "SUCCESS" or "FAILED"
- `error_message`: Sanitized error message on failure
- `created_at`: Timestamp

---

## 4. Feature 2: Job Match Intelligence & Transparent Scoring (Phase 6)

Phase 6 connects parsed resumes directly against user-defined job descriptions with a hybrid deterministic-AI architecture:

### 4.1 Strict Invariant: Numerical Scores are 100% Deterministic
Numerical scores are never delegated to or modified by an LLM:
$$\text{Overall Score} = \text{round}\left(100 \times \left(0.60 \times C_{req} + 0.20 \times C_{pref} + 0.10 \times S_{exp} + 0.10 \times S_{cat}\right)\right)$$

Category scores (Programming, Cloud Architecture, DevOps & CI/CD, Backend & Services, Database Systems, Years of Experience) are computed by `ScoringEngine`.

### 4.2 Role of the AI Provider in Job Matching
The AI provider is invoked strictly for qualitative coaching:
- Executive match summary
- Key candidate strengths for the specific role
- Contextual explanations of missing skills
- Actionable portfolio and resume improvement recommendations
- Technical interview preparation focus areas

### 4.3 Deterministic Fallback on Unconfigured AI
Unlike Resume Intelligence (which returns HTTP 503 when unconfigured), Job Matching is designed to remain fully functional:
- When `aiProvider.isConfigured() == false`, deterministic scoring and skill breakdown run normally.
- A deterministic fallback explanation is synthesized directly from the match results.
- The user receives an HTTP 200 response with all numerical metrics and a clear notice that AI coaching is offline.

For detailed formulas, normalization tables, and API schemas, see [JOB_MATCHING.md](./JOB_MATCHING.md).

---

## 5. Feature 3: AI Interview Preparation (Phase 7)

Phase 7 introduces structured, personalized technical interview preparation synthesized from the candidate's analyzed resume, a target job description, deterministic skill gaps, and a user-selected difficulty tier.

### 5.1 Architecture & Core Invariants
- **Deterministic Skill Gaps**: Skill gaps (`matchedSkills`, `missingRequiredSkills`, `missingPreferredSkills`) are derived strictly via `ScoringEngine` and `JobDescriptionParser`.
- **Server-Authoritative Difficulty**: The LLM outputs questions without a difficulty field; the backend sets `question.setDifficulty(requestedDifficulty)` prior to persistence.
- **13 Controlled Categories**: Questions are categorized into `JAVA`, `DSA`, `SPRING_BOOT`, `REST_APIS`, `SQL`, `AWS`, `DOCKER`, `LINUX`, `DEVOPS`, `AI`, `CLOUD_SECURITY`, `BEHAVIORAL`, or `PROJECT_SPECIFIC`.
- **Strict Validation**: Exactly 10 questions, non-empty text, valid categories, and non-empty `expectedConcepts` array. Any violation triggers a transactional rollback, logs an `AIInteraction` failure record, and throws a controlled exception.
- **Controlled Fallback**: When unconfigured, returns HTTP 503 with exact explanatory message. Never generates fake or hardcoded questions.
- **Multi-Tenant Isolation**: Enforces user ownership over resumes, jobs, and interview sessions. Cross-user combinations are rejected with HTTP 403.

For complete API documentation, prompt design, and database schema, see [INTERVIEW_PREPARATION.md](./INTERVIEW_PREPARATION.md).

---

## 6. Graceful Degradation & Default Configuration Summary

Per production security requirements:
- `ai.provider=${AI_PROVIDER:}` defaults to an empty string.
- When unconfigured, the application starts normally without errors.
- Non-AI endpoints (Auth, Applications, Deployments, Files, Job Descriptions) function completely.
- Calling `/api/ai/resume/analyze` without configuration returns a controlled HTTP `503 Service Unavailable` with message:
  `"AI provider is not configured. Please configure AI_PROVIDER and AI_API_KEY."`
- Calling `/api/ai/job-match` without configuration returns HTTP `200 OK` with full deterministic scoring and a clear fallback notice.
- Calling `/api/ai/interview/generate` without configuration returns HTTP `503 Service Unavailable` with message:
  `"AI interview generation is unavailable because the AI provider is not configured. Configure the AI provider to generate personalized interview questions."`
- The system **never fabricates or fakes AI data** in production mode.
