# CloudDeploy AI

## Overview
CloudDeploy AI is a production-oriented cloud engineering and deployment management platform built with a Java 17 / Spring Boot backend and a React / Vite frontend. It allows engineering teams to manage applications, track release deployments, store build artifacts securely in AWS S3, view system health, and inspect real aggregated cloud operations metrics.

---

## Current Status: Phase 7 Complete
The platform has completed **Phase 7: AI Interview Preparation**.

### Key Capabilities Across Phases 1–7
1. **AI Interview Preparation**:
   - Synthesizes 10 structured, personalized technical interview questions grounded in the candidate's analyzed resume, a target job description, and deterministic skill-gap analysis.
   - Server-authoritative difficulty enforcement (`BEGINNER`, `INTERMEDIATE`, `ADVANCED`).
   - Standardized 13-category taxonomic classification (`JAVA`, `DSA`, `SPRING_BOOT`, `REST_APIS`, `SQL`, `AWS`, `DOCKER`, `LINUX`, `DEVOPS`, `AI`, `CLOUD_SECURITY`, `BEHAVIORAL`, `PROJECT_SPECIFIC`).
   - Strict JSON validation: non-empty questions, bounded character lengths, valid categories, and expected concepts evaluation guides.
   - Controlled fallback returning HTTP 503 when the AI provider is offline (zero hallucinated/fake fallback questions).
   - Multi-tenant ownership isolation: verifies candidate resume and job description belong to the requesting user (rejecting cross-user requests with HTTP 403).
   - Dedicated SaaS interface at `/interview-prep` featuring setup wizard, interactive practice mode with question strip navigator, and session history management.
2. **AI Job Match Intelligence & Transparent Scoring**:
   - 100% deterministic mathematical scoring engine: $\text{Overall Score} = \text{round}\left(100 \times \left(0.60 \times C_{req} + 0.20 \times C_{pref} + 0.10 \times S_{exp} + 0.10 \times S_{cat}\right)\right)$.
   - Transparent category breakdowns: Programming Score, Cloud Architecture Score, DevOps & CI/CD Score, Backend & Services Score, Database Systems Score, and Experience Alignment Score.
   - Comprehensive skill canonicalization and normalization mapping raw aliases (e.g. `k8s` -> `Kubernetes`, `aws` -> `AWS`, `reactjs` -> `React`) while maintaining strict technological boundaries (Java != JavaScript, C != C++, AWS != Azure, MySQL != PostgreSQL).
   - Regex-based job description parser segmenting requirements into mandatory vs. preferred qualifications.
   - AI qualitative reasoning for natural language summaries, strengths, missing-skill context, recommendations, and technical interview preparation topics.
   - Graceful offline fallback: when AI is unconfigured, deterministic scoring and skill breakdown run normally without error.
   - User-scoped persistence for `JobDescription`, `JobMatch`, and `JobMatchDetail` records with server-side multi-tenant authorization.
   - High-fidelity SaaS interface at `/job-match` featuring circular score gauge, category score bars, filterable skill breakdown table, AI coaching cards, and match history drawer.
2. **AI-Powered Resume Intelligence**:
   - Ingest candidate resumes in multiple document formats: PDF (`.pdf`), Microsoft Word (`.docx`), and Plain Text (`.txt`).
   - Secure private AWS S3 object storage isolated per user (`users/{userId}/resumes/{uuid}-{sanitizedFilename}`).
   - Native document text extraction using Apache PDFBox 3.0 and Apache POI 5.2.5.
   - Intelligent sensitive data sanitization (`SensitiveDataFilterService`) redacting API tokens, passwords, private keys, SSNs, and credit cards before calling external AI providers.
   - Provider-agnostic LLM integration via `AIProvider` interface supporting OpenAI, OpenAI-compatible REST endpoints, and offline mock execution.
   - Prompt engineering with explicit untrusted input defense directives preventing prompt injection.
   - Strict structured JSON schema validation and strongly typed persistence in MySQL (`ResumeAnalysis`).
   - Dedicated SaaS interface at `/resume-ai` featuring drag-and-drop upload, candidate resume list, categorized skill badges, strengths, areas for growth, and recommended career technologies.
3. **AWS S3 File & Artifact Storage**:
   - Upload build artifacts, configuration files, and deployment manifests directly to applications.
   - Storage abstraction via `StorageService` interface backed by `S3StorageService` (AWS SDK v2).
   - Strict bucket privacy with short-lived pre-signed download URLs (15-minute expiration).
   - Unique, tenant-isolated S3 object key schema (`applications/{applicationId}/files/{uuid}-{sanitizedFilename}`).
   - Multi-layer file validation: 10MB ceiling, path traversal protection (`..`), and restricted dangerous extensions (`.exe`, `.sh`, `.bat`, etc.).
   - Graceful degradation when S3 configuration is absent (core features remain operational, file actions return clean 503).
4. **Application & Deployment Management**:
   - Create, list, inspect, edit, and delete application services.
   - Maintain an immutable audit history of software releases (`version`, `commitHash`, `status`, release notes).
   - Real-time application lifecycle synchronization (`PENDING`, `RUNNING`, `SUCCESS`, `FAILED`, `OFFLINE`).
5. **Real Aggregated Dashboard**:
   - Live metrics calculated directly from database records: Total Applications, Total Deployments, Successful Deployments, Failed Deployments.
   - Recent Applications and Recent Deployments feeds with zero-state handling for new users.
6. **Server-Side Ownership & Multi-Tenant Authorization**:
   - Strict ownership isolation enforced on all endpoints via JWT `SecurityContext`.
   - Cross-user resource access returns HTTP `403 Forbidden`.
   - Administrators (`ROLE_ADMIN`) maintain global visibility across applications.
   - Relational cascading deletion cleanly removes child deployment, file metadata, and resume records.

---

## Technology Stack
- **Frontend**: React 18, Vite, React Router 6, Axios, Lucide React, Custom SaaS Design System
- **Backend**: Java 17, Spring Boot 4.1.1, Spring Security (Stateless JWT), Spring Data JPA, Hibernate, Jakarta Validation, AWS SDK v2 S3 (`software.amazon.awssdk:s3:2.25.27`), Apache PDFBox 3.0.4, Apache POI 5.2.5
- **Database**: MySQL (Production/Local via Env Vars), H2 (In-Memory for Isolated Integration Tests)
- **Object Storage**: AWS S3 (via official AWS SDK v2, DefaultCredentialsProvider, S3Presigner)
- **AI & NLP**: Provider-independent `AIProvider` (OpenAI-compatible REST LLM, MockAIProvider), Apache PDFBox, Apache POI OOXML
- **Testing**: JUnit 5, Spring MockMvc, Mockito, Maven Surefire (106 automated integration tests across 6 test suites)

---

## Local Development Setup

### 1. Prerequisites
- Java 17+
- Apache Maven 3.9+
- Node.js 18+ and npm

### 2. Environment Configuration
Copy `.env.example` to `.env` and configure environment variables as needed:
```bash
# Database Configuration
DB_HOST=localhost
DB_PORT=3306
DB_NAME=clouddeploy
DB_USERNAME=root
DB_PASSWORD=password
DB_DIALECT=org.hibernate.dialect.MySQLDialect

# JWT Configuration
JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
JWT_EXPIRATION=86400000

# AWS S3 Configuration (Optional for local testing; default credentials chain used)
AWS_REGION=us-east-1
AWS_S3_BUCKET=my-clouddeploy-bucket
AWS_S3_URL_DURATION=15

# AI Configuration (Optional; unconfigured by default, 503 fallback)
AI_PROVIDER=openai
AI_API_KEY=your-openai-api-key
AI_MODEL=gpt-4o-mini
AI_BASE_URL=https://api.openai.com/v1
```

### 3. Backend Execution
```bash
cd CloudDeploy/backend

# Run complete integration test suite (106 tests)
mvn clean test

# Start the Spring Boot backend
mvn spring-boot:run
```
Backend API will be live at `http://localhost:8080`.

### 4. Frontend Execution
```bash
cd CloudDeploy/frontend

# Install dependencies
npm install

# Start Vite development server
npm run dev

# Or build production static assets
npm run build
```
Frontend will be accessible at `http://localhost:5173`.

---

## API Summary

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/health` | Public | Service health verification |
| `POST` | `/api/auth/register` | Public | Register new user account |
| `POST` | `/api/auth/login` | Public | Authenticate user & issue JWT |
| `GET` | `/api/auth/me` | Authenticated | Retrieve profile for authenticated user |
| `GET` | `/api/dashboard` | Authenticated | Aggregated metrics & recent activity |
| `GET` | `/api/applications` | Authenticated | List accessible applications |
| `POST` | `/api/applications` | Authenticated | Create application |
| `GET` | `/api/applications/{id}` | Authenticated | Retrieve application details |
| `PUT` | `/api/applications/{id}` | Authenticated | Update application details |
| `DELETE` | `/api/applications/{id}` | Authenticated | Delete application & cascading records |
| `GET` | `/api/applications/{id}/deployments` | Authenticated | List deployment history for application |
| `POST` | `/api/applications/{id}/deployments` | Authenticated | Record deployment release |
| `GET` | `/api/deployments/{id}` | Authenticated | Retrieve deployment record details |
| `POST` | `/api/applications/{id}/files` | Authenticated | Upload file to application (S3) |
| `GET` | `/api/applications/{id}/files` | Authenticated | List files for application |
| `DELETE` | `/api/files/{id}` | Authenticated | Delete file from S3 & metadata |
| `GET` | `/api/files/{id}/access-url` | Authenticated | Generate short-lived pre-signed S3 URL |
| `POST` | `/api/resumes/upload` | Authenticated | Upload resume (PDF/DOCX/TXT) to S3 & extract text |
| `GET` | `/api/resumes` | Authenticated | List uploaded resumes for user |
| `GET` | `/api/resumes/{id}` | Authenticated | Retrieve resume metadata & text snippet |
| `DELETE` | `/api/resumes/{id}` | Authenticated | Delete resume, S3 object & analysis |
| `POST` | `/api/ai/resume/analyze` | Authenticated | Trigger AI analysis on uploaded resume |
| `GET` | `/api/ai/resume/{id}/analysis` | Authenticated | Retrieve structured analysis result |
| `POST` | `/api/jobs` | Authenticated | Create a target job description |
| `GET` | `/api/jobs` | Authenticated | List job descriptions for user |
| `GET` | `/api/jobs/{id}` | Authenticated | Retrieve job description details |
| `PUT` | `/api/jobs/{id}` | Authenticated | Update job description |
| `DELETE` | `/api/jobs/{id}` | Authenticated | Delete job description |
| `POST` | `/api/ai/job-match` | Authenticated | Execute deterministic match & AI reasoning |
| `GET` | `/api/ai/job-match/{id}` | Authenticated | Retrieve job match result & breakdown |
| `GET` | `/api/ai/job-match` | Authenticated | List all previous matches for current user |
| `GET` | `/api/ai/job-match/resume/{resumeId}` | Authenticated | List all matches for a specific resume |
| `POST` | `/api/ai/interview/generate` | Authenticated | Synthesize 10 structured interview questions |
| `GET` | `/api/ai/interview` | Authenticated | List lightweight interview session summaries |
| `GET` | `/api/ai/interview/{id}` | Authenticated | Retrieve full interview session with questions |
| `DELETE` | `/api/ai/interview/{id}` | Authenticated | Delete interview session & cascade questions |

---

## Architecture & Security Documentation
- [AI Interview Preparation Specification](docs/INTERVIEW_PREPARATION.md)
- [Job Matching & Scoring Specification](docs/JOB_MATCHING.md)
- [AI Architecture Guide](docs/AI_ARCHITECTURE.md)
- [AI Security & Privacy Policy](docs/AI_SECURITY.md)
- [S3 Storage Architecture](docs/S3_STORAGE.md)
- [Technical Interview Guide](docs/INTERVIEW_GUIDE.md)

