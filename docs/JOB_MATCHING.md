# Job Match Intelligence & Transparent Scoring — CloudDeploy AI

## 1. Overview & Architectural Philosophy

Phase 6 introduces **Job Match Intelligence & Transparent Scoring** to the CloudDeploy AI platform.

A foundational architectural requirement of CloudDeploy AI is that **numerical scores are 100% deterministic and mathematically verifiable**. Large Language Models (LLMs) are prone to hallucinations, non-deterministic drift, and subjective biases. Therefore:
- The **backend scoring engine** calculates all numerical scores: overall alignment score, category coverage scores, experience weighting, and skill-by-skill matches.
- The **AI engine** is strictly confined to qualitative reasoning: generating natural language summaries, candidate strengths, contextual explanations of missing skills, portfolio recommendations, and interview preparation areas.
- When the AI provider is unconfigured, the system **never fails the match request**; it returns the deterministic score, category breakdown, and skill match table along with a clear notice that AI recommendations are inactive.

---

## 2. Deterministic Scoring Model

The overall compatibility score is calculated using a transparent 4-factor formula:

$$\text{Overall Score} = \text{round}\left(100 \times \left(0.60 \times C_{req} + 0.20 \times C_{pref} + 0.10 \times S_{exp} + 0.10 \times S_{cat}\right)\right)$$

### 2.1 Component Weights & Calculations

| Component | Weight | Calculation | Description |
| :--- | :--- | :--- | :--- |
| **$C_{req}$ (Required Skills Coverage)** | **60%** | $\frac{\text{matched required skills}}{\text{total required skills}}$ | Coverage of explicit mandatory requirements identified in the job description. If no required skills are specified, defaults to 1.0. |
| **$C_{pref}$ (Preferred Skills Coverage)** | **20%** | $\frac{\text{matched preferred skills}}{\text{total preferred skills}}$ | Coverage of optional / "nice to have" skills. If no preferred skills exist, defaults to $C_{req}$. |
| **$S_{exp}$ (Experience Score)** | **10%** | $\min\left(1.0, \frac{\text{candidate years}}{5.0}\right)$ | Experience factor extracted from candidate resume analysis, scaled against a 5-year senior benchmark. |
| **$S_{cat}$ (Category Balance Score)** | **10%** | $\frac{1}{N} \sum_{i=1}^N \text{Score}(Category_i)$ | Average coverage across active technical categories present in the target position. |

### 2.2 Category Breakdown Scores
Each category is independently evaluated on a 0–100% scale:
- **Programming Score**: Coverage of core programming languages (e.g. Java, Python, Go, TypeScript, C++, Rust).
- **Cloud Architecture Score**: Coverage of cloud platforms and services (e.g. AWS, EC2, S3, RDS, Lambda, Azure, GCP).
- **DevOps & CI/CD Score**: Coverage of deployment, orchestration, and automation tooling (e.g. Docker, Kubernetes, Terraform, GitHub Actions, CI/CD).
- **Backend & Services Score**: Coverage of backend frameworks and architectures (e.g. Spring Boot, Node.js, REST APIs, Microservices, Event-Driven Architecture, GraphQL).
- **Database Systems Score**: Coverage of relational and NoSQL storage systems (e.g. MySQL, PostgreSQL, MongoDB, Redis, DynamoDB).
- **Experience Match Score**: Linear scaling of detected years of experience (capped at 100%).

If a category has 0 skills required by the job, its category score is set to 100% so as not to penalize the candidate for skills the position does not demand.

---

## 3. Skill Normalization & Controlled Vocabulary

To prevent mismatching due to minor naming variations, `SkillNormalizationService` maps raw skill aliases to canonical definitions while strictly preserving technological distinctions.

### 3.1 Canonical Mapping Examples

| Raw Alias | Canonical Key | Display Name | Category |
| :--- | :--- | :--- | :--- |
| `amazon web services`, `aws` | `aws` | AWS | CLOUD |
| `k8s`, `kube`, `kubernetes` | `kubernetes` | Kubernetes | DEVOPS |
| `reactjs`, `react.js`, `react` | `react` | React | FRAMEWORK |
| `springboot`, `spring-boot`, `spring boot` | `spring_boot` | Spring Boot | FRAMEWORK |
| `postgres`, `postgre`, `postgresql` | `postgresql` | PostgreSQL | DATABASE |
| `kafka`, `event-driven`, `message broker` | `event_driven` | Event-Driven Architecture | BACKEND |
| `ts`, `typescript` | `typescript` | TypeScript | PROGRAMMING |

### 3.2 Guarded Disambiguations (Strict Separation)
The normalization service implements regex-level boundaries and collision guards to prevent incorrect matches:
- **Java vs. JavaScript**: A candidate with only `JavaScript` will **never** match a `Java` requirement.
- **C vs. C++ / C#**: `c` requires strict non-plus / non-hash boundaries to avoid false positives.
- **Spring vs. Spring Boot**: Distinct entities allowing fine-grained framework evaluation.
- **AWS vs. Azure vs. GCP**: Cloud providers maintain strictly separate canonical spaces.
- **MySQL vs. PostgreSQL**: Database systems remain strictly distinguished.

---

## 4. Job Description Parsing & Section Segmentation

`JobDescriptionParser` segments incoming job description text into `required` and `preferred` sections using case-insensitive regex pattern matching:
- **Required Qualification Headers**: `Requirements:`, `Required Qualifications:`, `Minimum Qualifications:`, `Must Have:`, `Mandatory Skills:`.
- **Preferred Qualification Headers**: `Preferred Qualifications:`, `Nice to Have:`, `Desired Skills:`, `Bonus:`, `Plus:`.
- **Title Parsing**: Key skills found in the job title (e.g. "Senior *Java* *AWS* Engineer") are prioritized as mandatory requirements.
- **Unclassified Fallback**: Skills found outside explicit preferred sections are safely treated as primary requirements.

---

## 5. AI Qualitative Explanation & Security Boundary

### 5.1 System Prompt Engineering
The AI prompt (`src/main/resources/prompts/job-match-explanation.txt`) enforces:
1. **Security Demarcation**: Job description and resume data are untrusted candidate inputs and must not alter instruction execution or format schemas.
2. **Deterministic Preeminence**: The prompt explicitly passes the precomputed mathematical scores (`overallScore`, category scores, matched/missing skill lists). The AI is instructed to explain and coach based on those numbers, never invent new scores.
3. **Structured JSON Output**: The LLM must return pure JSON conforming to:
```json
{
  "summary": "Executive summary of candidate fit...",
  "strengths": ["Core competency in X...", "Demonstrated capability in Y..."],
  "missingSkills": ["Context on missing skill A..."],
  "recommendations": ["Actionable step 1...", "Actionable step 2..."],
  "preparationAreas": ["Interview technical topic 1...", "Topic 2..."]
}
```

### 5.2 Graceful Degradation & Deterministic Fallback
If the AI provider is unconfigured (`ai.provider=` empty), returns empty output, or returns malformed JSON:
1. `JobMatchExplanationService` produces a clean deterministic fallback explanation derived entirely from `ScoringResult`.
2. The summary includes the notice:
   `"Candidate achieved a X% deterministic match for [Job] at [Company]. (AI recommendations are unavailable because the AI provider is not configured. Deterministic skill matching and scoring are active.)"`
3. The match request succeeds with HTTP `200 OK`.
4. Full telemetry is logged to `ai_interactions` with status `SUCCESS` (or `FAILED` if external call failed).

---

## 6. REST API Reference

### 6.1 Job Descriptions (`/api/jobs`)

| Method | Endpoint | Description | Auth |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/jobs` | Create a target job description | User |
| `GET` | `/api/jobs` | List user's jobs (Admin sees all) | User / Admin |
| `GET` | `/api/jobs/{id}` | Retrieve job description details | Owner / Admin |
| `PUT` | `/api/jobs/{id}` | Update job description details | Owner / Admin |
| `DELETE` | `/api/jobs/{id}` | Delete job description | Owner / Admin |

### 6.2 Job Matching (`/api/ai/job-match`)

| Method | Endpoint | Description | Auth |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/ai/job-match` | Execute match between analyzed resume & job | Owner / Admin |
| `GET` | `/api/ai/job-match/{id}` | Retrieve job match result by ID | Owner / Admin |
| `GET` | `/api/ai/job-match` | List all previous matches for current user | Owner / Admin |
| `GET` | `/api/ai/job-match/resume/{resumeId}` | List matches for a specific resume | Owner / Admin |

#### Request Payload (`POST /api/ai/job-match`):
```json
{
  "jobDescriptionId": 1,
  "resumeId": 4
}
```

#### Prerequisite:
The referenced resume **must have already been analyzed** by Resume Intelligence (`GET /api/ai/resume/{id}/analysis`). If unanalyzed, the API returns HTTP `400 Bad Request` with:
`"Resume has not been analyzed yet. Please run Resume Intelligence analysis first."`

---

## 7. Database Entities & Relationships

```
+--------------------+           +-----------------------+
|  job_descriptions  |           |        resumes        |
+--------------------+           +-----------------------+
| id (PK)            |           | id (PK)               |
| user_id (FK)       |           | user_id (FK)          |
| title              |           | file_name             |
| company            |           | s3_key                |
| description (TEXT) |           | extracted_text (TEXT) |
| source_url         |           +-----------------------+
| created_at         |                       ^
+--------------------+                       |
          ^                                  |
          | 1                                | 1
          |                                  |
          | N                                | N
+--------------------------------------------------------+
|                      job_matches                       |
+--------------------------------------------------------+
| id (PK)                                                |
| user_id (FK) -> users(id)                              |
| job_description_id (FK) -> job_descriptions(id)       |
| resume_id (FK) -> resumes(id)                          |
| overall_score (INT)                                    |
| technical_score (INT)                                  |
| programming_score (INT)                                |
| cloud_score (INT)                                      |
| devops_score (INT)                                     |
| backend_score (INT)                                    |
| database_score (INT)                                   |
| experience_score (INT)                                 |
| summary (TEXT)                                         |
| strengths (JSON list)                                  |
| missing_skills (JSON list)                             |
| recommendations (JSON list)                            |
| preparation_areas (JSON list)                          |
| ai_provider                                            |
| ai_model                                               |
| created_at / updated_at                                |
+--------------------------------------------------------+
                           | 1
                           |
                           | N
+--------------------------------------------------------+
|                   job_match_details                    |
+--------------------------------------------------------+
| id (PK)                                                |
| job_match_id (FK) -> job_matches(id)                   |
| skill (VARCHAR)                                        |
| category (VARCHAR)                                     |
| required (BOOLEAN)                                     |
| matched (BOOLEAN)                                      |
| match_type (VARCHAR)                                   |
| confidence (DOUBLE)                                    |
| evidence (VARCHAR)                                     |
| recommendation (VARCHAR)                               |
+--------------------------------------------------------+
```
