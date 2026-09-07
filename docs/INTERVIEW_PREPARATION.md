# AI Interview Preparation — CloudDeploy AI

## 1. Executive Summary

Phase 7 introduces **AI Interview Preparation** into CloudDeploy AI. Building directly on the Resume Intelligence (Phase 5) and Job Match Intelligence (Phase 6) foundations, this capability synthesizes targeted, personalized technical interview questions grounded in:
1. The candidate's analyzed resume.
2. The target job description.
3. The deterministic skill-gap analysis calculated by the `ScoringEngine`.
4. A server-authoritative difficulty tier (`BEGINNER`, `INTERMEDIATE`, `ADVANCED`).

---

## 2. Architecture & Data Flow

```
[User Browser / React Frontend]
       |
       | POST /api/ai/interview/generate { resumeId, jobDescriptionId, difficulty }
       v
[AIInterviewController]
       |
       | 1. Authentication & Tenant Authorization
       v
[InterviewService]
       |
       | 2. Multi-Tenant Ownership Validation (Resume & Job must belong to User)
       | 3. Analyzed Resume Precondition Check
       | 4. Deterministic Skill Gap Extraction (ScoringEngine + JobDescriptionParser)
       | 5. AI Configuration Check (aiProvider.isConfigured())
       |    - If false -> throw AIConfigurationException (HTTP 503)
       v
[AIService]
       |
       | 6. Render Prompt (prompts/interview-generation.txt)
       |    - Untrusted Input delimiters (<<<CANDIDATE RESUME>>>, <<<JOB DESCRIPTION>>>)
       |    - Grounding: matched skills, missing required/preferred skills
       |    - Request exactly 10 questions in JSON schema (WITHOUT difficulty)
       v
[AIProvider] (OpenAI-Compatible / Mock)
       |
       | 7. Return Raw JSON String
       v
[InterviewService Validation]
       |
       | 8. Strict JSON & Invariant Validation:
       |    - Exactly 10 questions
       |    - Valid InterviewCategory enum (13 controlled categories)
       |    - Non-empty question text (<= 1000 characters)
       |    - Non-empty expectedConcepts array
       |    - Server assigns: question.difficulty = requestedDifficulty
       v
[MySQL Persistence (@Transactional)]
       |
       | 9. Save InterviewSession (cascade saves InterviewQuestion + concepts)
       | 10. Audit Log in AIInteraction table (feature: "INTERVIEW_GENERATION")
       v
[HTTP 200 OK: InterviewSessionResponse]
```

---

## 3. Core Design Invariants

### 3.1 Single Deterministic Authority for Skill Gaps
The LLM is **never** permitted to guess, hallucinate, or score skill gaps. Instead, `InterviewService` executes the Phase 6 `JobDescriptionParser` and `ScoringEngine` to obtain:
- Candidate skills from `ResumeAnalysis`.
- Job requirements parsed deterministically.
- `matchedSkills`: Candidate competencies meeting job requirements.
- `missingRequiredSkills`: Gaps in essential requirements.
- `missingPreferredSkills`: Gaps in secondary requirements.

These deterministic lists are injected directly into the prompt to ensure every question addresses actual matched proficiencies or critical skill gaps.

### 3.2 Server-Authoritative Difficulty
The requested difficulty (`BEGINNER`, `INTERMEDIATE`, `ADVANCED`) is strictly server-authoritative:
- The system prompt instructs the model to tailor questions to the requested difficulty level.
- The AI output JSON schema **omits** the `difficulty` field.
- The backend assigns `question.setDifficulty(requestedDifficulty)` in Java prior to database persistence.
- This eliminates any risk of LLM hallucinations or schema mismatches altering difficulty tiers.

### 3.3 Controlled 13 Interview Categories
Every generated question must belong to one of 13 predefined categories in the `InterviewCategory` enum:
- `JAVA`
- `DSA`
- `SPRING_BOOT`
- `REST_APIS`
- `SQL`
- `AWS`
- `DOCKER`
- `LINUX`
- `DEVOPS`
- `AI`
- `CLOUD_SECURITY`
- `BEHAVIORAL`
- `PROJECT_SPECIFIC`

Unrecognized category strings are rejected during strict validation.

### 3.4 Strict Validation & Atomic Rollback
Before saving to the database, the backend parses the AI response with Jackson into `InterviewAIOutputDto`:
- The array must contain **exactly 10 questions**.
- No question may be null or blank.
- No question text may exceed 1000 characters.
- Every question must specify a valid category.
- Every question must contain at least 1 expected concept.
- If validation fails, the transaction rolls back, an `AIInteraction` audit record is stored with `status: "FAILED"`, and a controlled `AIServiceException` (HTTP 500/controlled error) is thrown. **No partial or corrupted sessions are ever stored.**

### 3.5 Controlled AI Fallback (Zero Fake Questions)
In accordance with system-wide integrity rules:
- When `aiProvider.isConfigured() == false`, the endpoint returns HTTP `503 Service Unavailable` with message:
  `"AI interview generation is unavailable because the AI provider is not configured. Configure the AI provider to generate personalized interview questions."`
- The system **never fabricates or returns hardcoded fallback questions**.

### 3.6 Multi-Tenant Security & Ownership Isolation
- Users can only generate sessions using their own resumes and job descriptions.
- If a user attempts to combine User A's resume with User B's job description, the server rejects the request with HTTP `403 Forbidden` (`AccessDeniedCustomException`).
- Users can only view (`GET /api/ai/interview/{id}`) and delete (`DELETE /api/ai/interview/{id}`) their own sessions.
- Users with role `ROLE_ADMIN` can view any session.

---

## 4. API Endpoints

### 4.1 Generate Interview Session
- **Endpoint**: `POST /api/ai/interview/generate`
- **Request Body**:
  ```json
  {
    "resumeId": 1,
    "jobDescriptionId": 2,
    "difficulty": "INTERMEDIATE"
  }
  ```
- **Response**: `200 OK`
  ```json
  {
    "id": 10,
    "resumeId": 1,
    "resumeFileName": "john_doe_cloud_engineer.pdf",
    "jobDescriptionId": 2,
    "jobTitle": "Senior DevOps Engineer",
    "company": "CloudTech Systems",
    "difficulty": "INTERMEDIATE",
    "createdAt": "2026-09-07T15:00:00",
    "questions": [
      {
        "id": 101,
        "question": "How would you design an immutable infrastructure deployment pipeline with Docker and AWS ECS?",
        "category": "DEVOPS",
        "difficulty": "INTERMEDIATE",
        "expectedConcepts": [
          "Docker image tagging strategies",
          "ECS task definition updates",
          "Zero-downtime rolling deployments",
          "Rollback triggers and health checks"
        ],
        "createdAt": "2026-09-07T15:00:00"
      }
    ]
  }
  ```

### 4.2 List User Interview Sessions (Lightweight)
- **Endpoint**: `GET /api/ai/interview`
- **Response**: `200 OK` (Array of summaries without questions array for high performance)
  ```json
  [
    {
      "id": 10,
      "resumeId": 1,
      "resumeFileName": "john_doe_cloud_engineer.pdf",
      "jobDescriptionId": 2,
      "jobTitle": "Senior DevOps Engineer",
      "company": "CloudTech Systems",
      "difficulty": "INTERMEDIATE",
      "questionCount": 10,
      "createdAt": "2026-09-07T15:00:00"
    }
  ]
  ```

### 4.3 Get Specific Interview Session
- **Endpoint**: `GET /api/ai/interview/{id}`
- **Response**: `200 OK` (Full session including all 10 questions)

### 4.4 Delete Interview Session
- **Endpoint**: `DELETE /api/ai/interview/{id}`
- **Response**: `200 OK`
  ```json
  {
    "message": "Interview session deleted successfully"
  }
  ```

---

## 5. Database Schema

```sql
CREATE TABLE interview_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    resume_id BIGINT NOT NULL,
    job_description_id BIGINT NOT NULL,
    difficulty VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_interview_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_interview_sessions_resume FOREIGN KEY (resume_id) REFERENCES resumes(id) ON DELETE CASCADE,
    CONSTRAINT fk_interview_sessions_job FOREIGN KEY (job_description_id) REFERENCES job_descriptions(id) ON DELETE CASCADE
);

CREATE TABLE interview_questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    question VARCHAR(1000) NOT NULL,
    category VARCHAR(64) NOT NULL,
    difficulty VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_interview_questions_session FOREIGN KEY (session_id) REFERENCES interview_sessions(id) ON DELETE CASCADE
);

CREATE TABLE interview_question_concepts (
    question_id BIGINT NOT NULL,
    concept VARCHAR(255) NOT NULL,
    CONSTRAINT fk_iqc_question FOREIGN KEY (question_id) REFERENCES interview_questions(id) ON DELETE CASCADE
);
```

---

## 6. Frontend SaaS User Interface

The frontend interface at `/interview-prep` provides:
1. **Configuration & Setup**:
   - Resume selector (filtering for analyzed resumes).
   - Target job selector (with quick "+ Add Job" modal).
   - Difficulty pill selection (`BEGINNER`, `INTERMEDIATE`, `ADVANCED`).
   - Loading spinners and controlled 503 error banners.
2. **Interactive Practice Mode**:
   - Question Navigator (buttons 1 through 10 with active and visited tracking).
   - Question card displaying question index, category badge, and difficulty badge.
   - Collapsible "Expected Concepts / Key Discussion Points" toggle.
   - Previous and Next question navigation controls.
3. **Session History**:
   - Tabular view of past sessions with creation date, company, difficulty, and question count.
   - One-click loading of any past session into practice mode.
   - Delete session modal with cascade deletion confirmation.
