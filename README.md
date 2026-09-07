# CloudDeploy AI

## Overview
CloudDeploy AI is a production-oriented cloud engineering and deployment management platform built with a Java 17 / Spring Boot backend and a React / Vite frontend. It allows engineering teams to manage applications, track release deployments, store build artifacts securely in AWS S3, view system health, and inspect real aggregated cloud operations metrics.

---

## Current Status: Phase 10 Complete
The platform has completed **Phase 10: AWS Cloud Infrastructure**.

### Key Capabilities Across Phases 1–10
1. **AWS Cloud Infrastructure as Code (CloudFormation)**:
   - Declarative multi-tier AWS CloudFormation template (`infra/cloudformation/clouddeploy-infra.yml`) establishing a production-grade VPC (`10.0.0.0/16`) across 2 Availability Zones (`us-east-1a`, `us-east-1b`).
   - Network isolation: 2 public subnets for compute ingress and 2 private isolated subnets strictly for RDS MySQL 8.0 (`PubliclyAccessible: false`).
   - Cost-optimized architectural design: Free AWS S3 VPC Gateway Endpoint (`com.amazonaws.us-east-1.s3`) routing private S3 traffic directly over AWS internal network, saving ~$32+/month by eliminating NAT Gateway requirements.
   - Defensive Security Groups: `clouddeploy-ec2-sg` allowing port 80 public ingress (port 22 closed by default), and `clouddeploy-rds-sg` permitting port 3306 exclusively from the EC2 security group.
   - CloudFormation-managed S3 bucket (`ApplicationS3Bucket`) with default Block Public Access, SSE-S3 AES256 server-side encryption, and `DeletionPolicy: Retain`.
   - RDS MySQL 8.0 instance with 7-day automated backups and `DeletionPolicy: Snapshot`.
   - IAM least-privilege EC2 role with scoped S3 bucket actions, scoped SSM Parameter Store read access (`/clouddeploy/*`), and `AmazonSSMManagedInstanceCore` for secure keyless SSH-less host administration via AWS Systems Manager.
   - Hardened IMDSv2 enforcement (`HttpTokens: required`, `HttpPutResponseHopLimit: 2`) enabling containerized workloads on Docker bridge network to securely resolve IAM role credentials.
   - Production Docker Compose overlay (`docker-compose.prod.yml`) connecting containerized frontend and backend directly to managed RDS MySQL.
   - Runtime secret management script (`infra/scripts/fetch-secrets.sh`) pulling credentials securely from SSM Parameter Store into `/opt/clouddeploy/.env` (`chmod 600`) without baking secrets into Docker images.
2. **Production Docker Packaging & Compose Orchestration**:
   - Multi-stage production `Dockerfile` for Spring Boot backend (Eclipse Temurin JRE 17, non-root user `appuser` UID 1001, container JVM optimization, BusyBox `wget` healthcheck).
   - Multi-stage production `Dockerfile` for React frontend (`node:18-alpine` builder + `nginx:1.25-alpine` runtime).
   - Production Nginx configuration with client-side SPA routing fallback (`try_files $uri $uri/ /index.html`), `/api/` reverse proxy pass to backend with 12MB multipart upload limit, gzip compression, and defensive security headers (`X-Content-Type-Options: nosniff`, `X-Frame-Options: SAMEORIGIN`).
   - `docker-compose.yml` orchestrating `mysql` (internal-only, persistent `mysql_data` volume, healthcheck), `backend` (internal-only, healthcheck), and `frontend` (sole ingress on port 80).
   - Health-based startup dependency ordering (`depends_on: ...: condition: service_healthy`) ensuring MySQL is fully initialized before Spring Boot starts.
   - Zero hardcoded secrets: mandatory environment variable substitution (`${JWT_SECRET:?...}`, `${MYSQL_PASSWORD:?...}`) with documentation template `.env.docker.example`.
2. **AI Cloud Troubleshooting Assistant**:
   - AI-powered, context-aware cloud troubleshooting assistant grounded strictly in stored application/deployment metadata and user-provided diagnostic logs.
   - Truthful operational boundaries: explicitly does not claim live connections to AWS CloudWatch, Kubernetes daemons, or EC2 APIs.
   - Command safety: suggested CLI commands are generated as advisory display-only text and never executed by the backend.
   - Multi-tenant security: enforces application and deployment ownership, verifies deployment-application relationship, and locks session context across subsequent chat turns.
   - Sensitive credential scrubbing: user queries and log snippets are automatically sanitized for AWS keys, bearer tokens, passwords, and private keys.
   - Dedicated SaaS interface at `/assistant` (with alias `/cloud-assistant`) featuring context selector bar, 5 quick diagnostic starters, chat feed with severity badges (`CRITICAL`, `HIGH`, `MEDIUM`, `LOW`, `INFO`), root cause callouts, remediation steps, and copyable advisory commands.
2. **AI Interview Preparation**:
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
- **Testing**: JUnit 5, Spring MockMvc, Mockito, Maven Surefire (126 automated integration tests across 7 test suites)

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

# Run complete integration test suite (126 tests)
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

### 5. Production Docker Compose Execution
For containerized deployment with isolated internal MySQL and Nginx reverse proxy:
```bash
# 1. Copy environment template and configure secure passwords
cp .env.docker.example .env

# 2. Build and launch multi-container stack in detached mode
docker compose up --build -d

# 3. Check container health status
docker compose ps
```
The application will be accessible at `http://localhost/` (sole host ingress on port 80).
See [Production Docker Deployment Guide](docs/DOCKER_DEPLOYMENT.md) for full operational details.

### 6. AWS Cloud Deployment (CloudFormation)
For real AWS infrastructure deployment:
1. Review the architecture and parameter requirements in [AWS Cloud Infrastructure Guide](docs/AWS_INFRASTRUCTURE.md).
2. Store runtime secrets securely in AWS SSM Parameter Store as `SecureString` types (`/clouddeploy/jwt_secret`, `/clouddeploy/db_password`, `/clouddeploy/ai_api_key`).
3. Deploy the CloudFormation template:
```bash
aws cloudformation deploy \
  --template-file infra/cloudformation/clouddeploy-infra.yml \
  --stack-name clouddeploy-prod \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides \
      DBPassword="YourSecureMasterPassword123!"
```
4. Access EC2 keylessly via AWS SSM Session Manager (`aws ssm start-session --target <instance-id>`).
5. Run `/opt/clouddeploy/infra/scripts/fetch-secrets.sh` to generate the secure runtime `.env` file (`chmod 600`), then launch the production overlay:
```bash
cd /opt/clouddeploy && docker compose -f docker-compose.prod.yml up -d
```

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
| `POST` | `/api/ai/assistant/chat` | Authenticated | Send troubleshooting prompt & receive analysis |
| `GET` | `/api/ai/assistant/sessions` | Authenticated | List lightweight troubleshooting sessions |
| `GET` | `/api/ai/assistant/sessions/{id}` | Authenticated | Retrieve full troubleshooting session thread |
| `DELETE` | `/api/ai/assistant/sessions/{id}` | Authenticated | Delete troubleshooting session & cascade messages |

---

## Architecture & Security Documentation
- [AWS Cloud Infrastructure Architecture](docs/AWS_INFRASTRUCTURE.md)
- [Production Docker Deployment Guide](docs/DOCKER_DEPLOYMENT.md)
- [AI Cloud Troubleshooting Assistant Specification](docs/TROUBLESHOOTING_ASSISTANT.md)
- [AI Interview Preparation Specification](docs/INTERVIEW_PREPARATION.md)
- [Job Matching & Scoring Specification](docs/JOB_MATCHING.md)
- [AI Architecture Guide](docs/AI_ARCHITECTURE.md)
- [AI Security & Privacy Policy](docs/AI_SECURITY.md)
- [S3 Storage Architecture](docs/S3_STORAGE.md)
- [Technical Interview Guide](docs/INTERVIEW_GUIDE.md)



