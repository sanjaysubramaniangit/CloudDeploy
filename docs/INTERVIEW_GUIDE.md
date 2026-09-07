# Technical Interview Guide & Engineering Deep Dive

This guide prepares you to explain the technical architecture, design trade-offs, and engineering decisions implemented in **CloudDeploy AI**.

---

## 1. REST API Design & HTTP Status Codes

### Question: How did you design the REST endpoints for CloudDeploy AI?
**Talking Points:**
- **Resource-Oriented URIs**: Followed RESTful best practices using plural nouns representing resources:
  - `/api/applications`
  - `/api/applications/{id}`
  - Sub-resources for nested dependencies: `/api/applications/{applicationId}/deployments`
- **Predictable HTTP Verbs**:
  - `GET`: Idempotent retrieval of resources.
  - `POST`: Creation of new entities (returns `201 Created`).
  - `PUT`: Idempotent replacement or complete update of existing resources (returns `200 OK`).
  - `DELETE`: Removal of resources (returns `204 No Content`).
- **Standardized Error Responses**: All errors return a uniform JSON schema (`timestamp`, `status`, `error`, `message`, `path`, and optional field-level `details`).

---

## 2. Authentication vs. Authorization

### Question: What is the difference between Authentication and Authorization in your system, and how are HTTP 401 and 403 handled?
**Talking Points:**
- **Authentication (401 Unauthorized)**: Verifying *who* you are.
  - Handled by `JwtAuthenticationFilter` and `JwtAuthEntryPoint`. If the JWT token is missing, expired, or cryptographically invalid, the filter rejects the request immediately with `401 Unauthorized`.
- **Authorization (403 Forbidden)**: Verifying *what* you are permitted to do.
  - Handled by `ApplicationService` and `DeploymentService` after the user identity is proven. If User A attempts to view, modify, or delete an application belonging to User B, the server intercepts the request and throws `AccessDeniedCustomException`, returning `403 Forbidden`.
  - We never rely on frontend route guards alone; authorization is strictly validated on the backend.

---

## 3. Server-Side Ownership Authorization

### Question: How do you prevent Insecure Direct Object References (IDOR) and cross-user tampering?
**Talking Points:**
- **Deriving Identity from JWT**: We never accept an `ownerId` or `userId` in the request body or query parameters to determine resource ownership. The caller's identity is derived strictly from `SecurityContextHolder.getContext().getAuthentication().getName()`.
- **Ownership Verification in the Service Layer**:
  ```java
  public Application findApplicationAndVerifyOwnership(Long id, User user) {
      Application application = applicationRepository.findById(id)
              .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));

      if (user.getRole() != Role.ADMIN && !application.getOwner().getId().equals(user.getId())) {
          throw new AccessDeniedCustomException("You do not have permission to access this resource");
      }
      return application;
  }
  ```
- **Admin Bypass**: Users with `Role.ADMIN` have organizational authority to inspect or manage resources across accounts, while standard `Role.USER` members are strictly limited to their own records.

---

## 4. JPA Relationships & Cascading Deletions

### Question: How are database relationships modeled in Hibernate, and how do you prevent orphaned records?
**Talking Points:**
- **Relationship Modeling**:
  - `User` 1 → * `Application`: `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id")`
  - `Application` 1 → * `Deployment`: `@OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)`
- **FetchType.LAZY**: Prevents the N+1 query problem by only loading child collections when explicitly requested.
- **CascadeType.ALL & orphanRemoval = true**: When an `Application` is deleted via `applicationRepository.delete(app)`, Hibernate automatically issues `DELETE FROM deployments WHERE application_id = ?` before removing the application row.
- **Test Fixture Ordering**: In unit tests, fixtures must be cleaned up in reverse dependency order (`deployments` -> `applications` -> `users`) to respect database foreign key integrity constraints.

---

## 5. Why Use DTOs (Data Transfer Objects)?

### Question: Why not return JPA entity objects directly from your controllers?
**Talking Points:**
1. **Preventing Circular Reference Recursion**: Bidirectional relationships (`Application` ↔ `Deployment`) cause infinite JSON serialization loops when using Jackson without DTOs.
2. **Security & Information Hiding**: Prevents internal persistence details (like password hashes, internal database IDs, or unneeded audit columns) from leaking to client responses.
3. **API Contract Decoupling**: Database schema refactorings (renaming columns or altering table structures) do not break frontend client contracts.
4. **Targeted Validation**: Input DTOs like `ApplicationRequest` validate only incoming client payloads via `@NotBlank` and `@Size`, independent of database column definitions.

---

## 6. Real Aggregations vs. Mock Data

### Question: How does the Dashboard work without hardcoding numbers?
**Talking Points:**
- The `DashboardService` executes real SQL aggregation queries via Spring Data JPA derived queries:
  - `applicationRepository.countByOwner(user)`
  - `deploymentRepository.countByApplicationOwner(user)`
  - `deploymentRepository.countByApplicationOwnerAndStatus(user, DeploymentStatus.SUCCESS)`
  - `deploymentRepository.countByApplicationOwnerAndStatus(user, DeploymentStatus.FAILED)`
- If a brand-new user registers, their dashboard reports strictly `0` totals and displays polished empty states. When they create applications and deployments, the metrics reflect live database counts immediately.

---

## 7. Deployment Records vs. Automated CI/CD

### Question: What is the purpose of the "Deployment" model in Phase 3 versus future phases?
**Talking Points:**
- **Phase 3**: Focuses on **Application & Deployment Management as an audit system**. It models software releases, commit hashes, versions, and deployment statuses (`PENDING`, `RUNNING`, `SUCCESS`, `FAILED`).
- **Roadmap (Phase 4+)**: Connects these deployment records to real cloud workflows, transitioning from manual recording to automated cloud provisioning.

---

## 8. AI Interview Preparation System Design (Phase 7)

### Question: How did you design the AI Interview Preparation feature to prevent hallucinations, schema corruption, and security leaks?
**Talking Points:**
1. **Deterministic Grounding Over Hallucination**:
   - Instead of asking the LLM to guess what skills are missing or evaluate the match, the backend computes skill gaps using the deterministic `ScoringEngine` and `JobDescriptionParser`.
   - The candidate's verified skills, target job requirements, and missing required/preferred skills are injected into the prompt as explicit constraints.
2. **Server-Authoritative Difficulty**:
   - The user selects the difficulty (`BEGINNER`, `INTERMEDIATE`, `ADVANCED`), but the LLM output schema intentionally **omits** the difficulty field.
   - The backend explicitly sets `question.setDifficulty(requestedDifficulty)` in Java prior to saving, preventing model hallucinations or invalid strings from corrupting difficulty tiers.
3. **Strict Validation Invariants & Atomic Rollback**:
   - The AI output is validated strictly: exactly 10 questions, non-empty text, valid categories from a 13-member enum, non-empty expected concepts.
   - If validation fails, `@Transactional` rolls back the database, an `AIInteraction` audit record is stored with `status: "FAILED"`, and a controlled exception is returned.
4. **Cross-Tenant Isolation Invariants**:
   - The backend verifies that the candidate resume and target job description belong to the calling user: if User A attempts to generate interview questions using User B's job description, the server rejects the request with HTTP `403 Forbidden`.
5. **Controlled Fallback Without Fake Questions**:
   - When the AI provider is not configured, the service returns HTTP `503 Service Unavailable` with a clear actionable message, rather than fabricating hardcoded or fake questions.

---

## 9. AI Cloud Troubleshooting Assistant System Design (Phase 8)

### Question: How does the AI Cloud Troubleshooting Assistant work, and how did you address command safety, multi-tenancy, and LLM boundaries?
**Talking Points:**
1. **Truthful Grounding Without False Telemetry Claims**:
   - The assistant explicitly does **not** claim live connections to AWS CloudWatch, Kubernetes daemons, or EC2 APIs.
   - It is grounded strictly in stored application and deployment metadata (`repositoryUrl`, `version`, `commitHash`, `deploymentStatus`, `deploymentMessage`) combined with user-provided log snippets and stack traces.
2. **Advisory Text vs. Remote Process Execution (Command Safety)**:
   - Suggested CLI commands (`kubectl logs`, `journalctl`, `aws s3 ls`) are generated as **display-only text**.
   - CloudDeploy **never executes** these commands on the host or cloud infrastructure (`Runtime.getRuntime().exec()` and `ProcessBuilder` are forbidden).
   - Commands are validated against length ($\le 500$ chars) and quantity ($\le 10$) limits before storage, and displayed with copy-to-clipboard functionality and an explicit warning banner.
3. **Multi-Tenant Ownership & Relationship Integrity**:
   - The backend validates that the application and deployment belong to the authenticated user.
   - It also validates that the deployment is actually associated with the specified application (`deployment.getApplication().equals(application)`). Mismatches are rejected with `400 Bad Request`.
4. **Context Immutability Across Chat Turns**:
   - Once a troubleshooting thread is created, its context (application and deployment binding) is immutable. Users cannot re-bind or manipulate the target system in subsequent chat turns of that session.
5. **Sensitive Credential Sanitization**:
   - Before queries and log snippets are incorporated into the system prompt, they pass through `SensitiveDataFilterService`, automatically scrubbing AWS keys, passwords, bearer tokens, and private keys.
6. **Separation of Network I/O from Database Transactions**:
   - The AI network call is decoupled from the transaction boundary.
   - If the AI call fails, an `AIInteraction` failure record is committed immediately for observability.
   - If the AI call succeeds, the session update, user message, assistant response, and `AIInteraction` success record are committed atomically in a single transaction.

---

## 10. AWS Cloud Architecture, Security Invariants, and Cost-Conscious Infrastructure Design (Phase 10)

### Question: How did you design the AWS cloud infrastructure for CloudDeploy AI, and how did you address network isolation, secret management, and cost optimization?
**Talking Points:**
1. **Multi-Tier Subnet Isolation**:
   - The architecture partitions the VPC (`10.0.0.0/16`) into public subnets (`10.0.1.0/24`, `10.0.2.0/24`) and private subnets (`10.0.11.0/24`, `10.0.12.0/24`) across 2 Availability Zones.
   - The compute tier (EC2) is placed in the public subnet, while the database tier (AWS RDS MySQL 8.0) is placed in the private subnets with `PubliclyAccessible: false`.
2. **Security Group Ingress Chains**:
   - RDS Security Group permits inbound MySQL (Port 3306) traffic **exclusively from the EC2 Security Group**. There is zero route or rule allowing public database ingress.
   - The EC2 Security Group exposes only Port 80 (HTTP) to the public Internet for Nginx frontend traffic.
3. **Keyless Administration (No SSH Ingress)**:
   - Instead of opening TCP port 22 or deploying an expensive bastion jump host, administration is conducted keylessly through **AWS Systems Manager (SSM) Session Manager** via `AmazonSSMManagedInstanceCore`.
4. **IMDSv2 & Zero Static AWS Credentials**:
   - The EC2 instance requires IMDSv2 (`HttpTokens: required`, `HttpPutResponseHopLimit: 2`).
   - The Spring Boot backend uses AWS SDK v2 `DefaultCredentialsProvider`, which retrieves rotating temporary credentials directly from the EC2 Instance Profile across the Docker bridge. No AWS access keys are written to disk or baked into container images.
5. **Runtime Secret Management via AWS Systems Manager Parameter Store**:
   - Production secrets (`JWT_SECRET`, `MYSQL_PASSWORD`, `AI_API_KEY`) are stored as SSM `SecureString` parameters under `/clouddeploy/*`.
   - The EC2 instance IAM role is granted least-privilege read access strictly to `/clouddeploy/*`.
   - The host bootstrap script pulls these parameters into `/opt/clouddeploy/.env` with strict `chmod 600` permissions at runtime.
6. **NAT Gateway Cost Avoidance & S3 Gateway Endpoint**:
   - A managed NAT Gateway incurs ~$32+/month plus data transfer fees.
   - By placing the EC2 instance in the public subnet and using an **AWS S3 VPC Gateway Endpoint** (which is 100% free of charge and routes privately), the architecture avoids NAT Gateway fees entirely while keeping the RDS database private.
7. **S3 Bucket & Database Lifecycle Protection**:
   - The CloudFormation-managed S3 bucket has Block Public Access enabled, SSE-S3 AES256 encryption, and `DeletionPolicy: Retain`.
   - RDS MySQL has automated daily backups (`BackupRetentionPeriod: 7`) and `DeletionPolicy: Snapshot` to ensure data preservation before stack teardown.

---

## 11. Enterprise CI/CD Pipelines, GitHub Actions OIDC Federation, and Safe Rollback Architecture (Phase 11)

### Question: How did you design the automated CI/CD pipeline for CloudDeploy AI, and how do you ensure controlled deployments with health-gated rollback and rapid recovery from failed releases?
**Talking Points:**
1. **GitHub Actions OIDC Federation (Zero Static AWS Keys)**:
   - Rather than storing long-lived `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` in GitHub repository secrets, the pipeline uses OpenID Connect (OIDC) with AWS STS (`sts:AssumeRoleWithWebIdentity`).
   - GitHub generates a cryptographically signed JSON Web Token (JWT) that AWS STS exchanges for temporary, short-lived session credentials.
2. **Two-Role Privilege Separation Model**:
   - **`GitHubActionsBuildRole`**: Scoped strictly to `repo:<org>/<repo>:ref:refs/heads/main` with permissions limited to pushing images to private Amazon ECR repositories. Has zero access to SSM Run Command, RDS, S3, or secrets.
   - **`GitHubActionsDeployRole`**: Scoped strictly to the `production` GitHub environment (`repo:<org>/<repo>:environment:production`) with permissions limited to dispatching `ssm:SendCommand` (`AWS-RunShellScript`) targeting EC2 hosts tagged `Name=clouddeploy-ec2-host`. Has zero access to push images to ECR, zero access to application S3 data, and zero access to Parameter Store secrets.
   - **`EC2Role` (Runtime)**: Retains read-only ECR image pull permissions, runtime secret decryption via Parameter Store, and application S3 storage access. CI/CD roles cannot access runtime secrets.
3. **Container Registry Architecture (Amazon ECR vs. Local EC2 Builds)**:
   - Compiling Java 17 / Maven and building Node.js / Vite bundles on a burstable `t3.small` / `t3.micro` EC2 instance risks severe CPU/RAM exhaustion, depletes CPU burst credits, and risks crashing running production containers.
   - Using Amazon ECR offloads all compilation and image packaging to GitHub Actions runners. EC2 only pulls the pre-built, multi-stage Alpine images.
   - ECR lifecycle policies automatically retain the last 10 immutable images to cap storage costs.
   - Same-Region transfer (`us-east-1` to `us-east-1`) incurs $0.00 data transfer charges.
4. **Keyless SSM Run Command Deployment (No Port 22 / SSH)**:
   - Deployments are triggered on EC2 keylessly via AWS Systems Manager Run Command (`AWS-RunShellScript`).
   - SSH Port 22 remains closed in the security group; no SSH keys exist to be leaked, rotated, or managed.
   - Every deployment invocation and log stream is recorded immutably in AWS Systems Manager.
5. **Immutable SHA Deployments & Robust Automated Rollback**:
   - Production deployments strictly use the full Git commit SHA (`backend:<SHA>`, `frontend:<SHA>`), never the mutable `latest` tag.
   - The authoritative deployment script (`deploy.sh`) records the active SHA in `/opt/clouddeploy/.current_deploy`.
   - After deploying containers with `docker compose up -d`, the script executes a multi-point health gate polling Port 80 (Nginx) and `/api/health` through the reverse proxy for up to 60 seconds.
   - If the health gate fails, `deploy.sh` automatically redeploys the previous known-good SHA recorded in `.current_deploy`, verifies rollback health, captures container diagnostic logs to stdout, and exits with a non-zero code to fail the GitHub Actions workflow.

---

## 12. Amazon CloudWatch Observability, Production Monitoring, Custom Metrics, and Cost-Conscious Log Retention (Phase 12)

### Question: How did you design observability and monitoring for CloudDeploy AI, and how do you ensure high-fidelity alerting while minimizing cloud costs?
**Talking Points:**
1. **Host-Level Agent vs. Containerized Agent Architecture**:
   - The CloudWatch Agent is installed natively on the EC2 host managed by Amazon Linux 2023 `systemd`, rather than inside a Docker container.
   - This provides direct kernel access to `/proc` and `/sys` for memory (`mem_used_percent`) and disk (`disk_used_percent`) telemetry without requiring elevated container privileges (`--privileged`).
   - Crucially, running under host `systemd` ensures daemon decoupling: if the Docker daemon crashes or runs out of memory, the CloudWatch Agent stays alive to transmit crash logs and trigger critical alerts.
2. **Custom Least-Privilege IAM Policy**:
   - Rather than attaching broad managed policies, the EC2 role uses an explicit custom inline policy (`CloudDeployObservabilityPolicy`).
   - `cloudwatch:PutMetricData` is strictly conditioned on `cloudwatch:namespace` to allow only `CWAgent` and `CloudDeploy/Application`.
   - CloudWatch Logs actions (`CreateLogStream`, `PutLogEvents`) are scoped strictly to `/clouddeploy/*`.
   - Zero `AdministratorAccess` and zero access to database credentials or application S3 data.
3. **Controlled Metric Cardinality & Free Tier Optimization**:
   - CloudDeploy distinguishes between standard free EC2 metrics (`CPUUtilization`, `StatusCheckFailed`) and custom metrics.
   - Custom metrics are capped strictly to three high-value indicators: `mem_used_percent`, `disk_used_percent`, and `HealthCheckStatus`.
   - Dimensions are strictly minimal (`InstanceId`, `Environment`), completely avoiding high-cardinality request or user IDs.
   - This keeps metric and alarm quantities within currently documented AWS Free Tier allowances when the account qualifies.
4. **Heartbeat Failure Detection (`TreatMissingData: breaching`)**:
   - The synthetic health probe runs every 60 seconds via local cron, publishing `HealthCheckStatus` (1 = UP, 0 = DOWN) to CloudWatch.
   - For `ApplicationHealthAlarm`, `TreatMissingData` is explicitly set to `breaching`. If the EC2 host freezes, panics, or terminates, the cron script cannot run to publish a 0. Treating missing data as breaching guarantees that total host failure immediately triggers an incident alarm rather than remaining silent.
5. **Two-Tier Log Retention & Host Rotation**:
   - On the host, `/etc/logrotate.d/clouddeploy` rotates container logs daily, keeps 7 compressed archives, and uses `copytruncate` to protect the root EBS volume from filling up.
   - In AWS, all five CloudWatch Log Groups enforce `RetentionInDays: 14` via CloudFormation, preventing unbounded cloud storage costs.




