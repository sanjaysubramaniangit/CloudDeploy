# CloudDeploy AI — CI/CD Pipeline Architecture & Deployment Runbook

## 1. Overview

CloudDeploy AI employs an enterprise-grade, fully automated Continuous Integration and Continuous Deployment (CI/CD) pipeline built with **GitHub Actions**, **Amazon Elastic Container Registry (ECR)**, **AWS OpenID Connect (OIDC) Federation**, and **AWS Systems Manager (SSM) Run Command**.

The pipeline validates every pull request, builds production container images upon merges to `main`, pushes immutable SHA-tagged artifacts to private ECR repositories, and triggers controlled container replacement on the production Amazon EC2 instance with automated multi-point health check verification and safe rollback. (Note: on a single-EC2 Docker Compose host, container replacement may incur a brief replacement interruption rather than true zero-downtime).

---

## 2. CI/CD Pipeline Architecture

```
Developer
    │
    ├── Pull Request ────────► GitHub Actions: PR Checks (.github/workflows/pr-checks.yml)
    │                          ├── Backend Maven Integration Tests (126 tests with H2)
    │                          ├── Frontend Production Build (Node 18 / Vite)
    │                          └── Dockerfile & Shell Script Syntax Validation
    │                              (Zero AWS Credentials / Zero Deployment)
    │
    └── Push to 'main' ──────► GitHub Actions: Production Pipeline (.github/workflows/deploy.yml)
                               ├── Stage 1: Validation Matrix (126 Tests + Vite Build)
                               │
                               ├── Stage 2: Build & Push Images
                               │   ├── Assume GitHubActionsBuildRole (AWS OIDC)
                               │   ├── Authenticate Docker to Amazon ECR
                               │   ├── Build Backend & Frontend Multi-Stage Docker Images
                               │   └── Push clouddeploy-backend:<SHA> & clouddeploy-frontend:<SHA>
                               │
                               └── Stage 3: Orchestrate EC2 Deployment (Environment: production)
                                   ├── Concurrency Lock: group: production-deployment
                                   ├── Assume GitHubActionsDeployRole (AWS OIDC)
                                   ├── Discover EC2 Target (tag: Name=clouddeploy-ec2-host)
                                   ├── Dispatch AWS SSM Run Command (AWS-RunShellScript)
                                   │       │
                                   │       ▼
                                   │   Amazon EC2 Application Host (us-east-1)
                                   │   ├── Execute /opt/clouddeploy/infra/scripts/deploy.sh <SHA> <SHA>
                                   │   ├── Authenticate Docker via Instance Role (EC2Role)
                                   │   ├── Pull Target Immutable Images from ECR
                                   │   ├── Graceful Rollout: docker compose -f docker-compose.prod.yml up -d
                                   │   ├── Multi-Point Health Check Gate (60-second polling)
                                   │   │   ├── Port 80 (Nginx root)
                                   │   │   ├── Port 80 (/api/health through Nginx)
                                   │   │   └── Container Health (docker inspect)
                                   │   ├── [PASS] Update .current_deploy, prune dangling images, exit 0
                                   │   └── [FAIL] Restore previous known-good SHA, verify rollback, exit 1
                                   │
                                   ├── Poll SSM Command Invocation Status until Terminal
                                   ├── Stream StandardOutputContent & StandardErrorContent
                                   └── Mark GitHub Actions Workflow Status (Pass / Fail)
```

---

## 3. Two-Role OIDC Privilege Separation Architecture

To prevent privilege escalation and enforce the principle of least privilege, CloudDeploy AI separates CI/CD responsibilities into two distinct AWS IAM roles assumed via **OpenID Connect (OIDC)** without any long-lived access keys (`AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`).

### IAM Role Separation Matrix

| Capability / Resource | `GitHubActionsBuildRole` | `GitHubActionsDeployRole` | `EC2Role` (EC2 Runtime) |
|---|---|---|---|
| **OIDC Trust Condition** | `repo:<org>/<repo>:ref:refs/heads/main` | `repo:<org>/<repo>:environment:production` | Instance Profile (`ec2.amazonaws.com`) |
| **Amazon ECR Authentication** | `ecr:GetAuthorizationToken` | **DENIED** | `ecr:GetAuthorizationToken` |
| **Amazon ECR Push** | `ecr:PutImage`, `InitiateLayerUpload`, etc. | **DENIED** | **DENIED** |
| **Amazon ECR Pull** | `ecr:GetDownloadUrlForLayer`, `BatchGetImage` | **DENIED** | `ecr:GetDownloadUrlForLayer`, `BatchGetImage` |
| **SSM SendCommand** | **DENIED** | Scoped to `AWS-RunShellScript` & EC2 Tag | **DENIED** |
| **SSM Command Polling** | **DENIED** | `ssm:GetCommandInvocation` | **DENIED** |
| **SSM Session Manager** | **DENIED** | **DENIED** | `AmazonSSMManagedInstanceCore` |
| **SSM Parameter Store Secrets** | **DENIED** | **DENIED** | `ssm:GetParameter` on `/clouddeploy/*` |
| **Application S3 Bucket** | **DENIED** | **DENIED** | `s3:GetObject`, `PutObject`, `DeleteObject` |
| **RDS Database Access** | **DENIED** | **DENIED** | Network ingress via Security Group |

### Why This Separation Matters
1. **Build Job Isolation**: Even if a malicious build dependency compromised the `build-and-push` runner, the runner cannot trigger commands on the EC2 host or read production database credentials.
2. **Deploy Job Isolation**: The `deploy` runner cannot push arbitrary or tampered Docker images to ECR, nor can it inspect database passwords or application files stored in S3.
3. **Zero Secret Leakage**: Neither CI/CD role has permissions to read `/clouddeploy/*` SecureString parameters from AWS Systems Manager Parameter Store. Secrets are retrieved exclusively on the EC2 host at runtime by `fetch-secrets.sh`.

---

## 4. Container Registry Strategy: Amazon ECR

### Architectural Evaluation: ECR vs. Building on EC2

| Dimension | Building Directly on EC2 | Amazon ECR Private Repositories |
|---|---|---|
| **Host Resource Impact** | **Severe Risk**: Compiling Java 17 / Maven and building Node.js / Vite bundles on a `t3.small` (2 GB RAM) or `t3.micro` (1 GB RAM) instance exhausts memory, consumes burst credits, and causes production outage / 504 Gateway Timeouts. | **Zero Host Impact**: Compilation and container packaging execute on GitHub Actions hosted runners (4 vCPUs, 16 GB RAM). EC2 merely pulls pre-compiled images. |
| **Separation of Concerns** | Violates CI/CD best practices by running build tools and compilers directly on the production host. | Strictly decouples the artifact packaging pipeline from production compute. |
| **Rollback Speed** | Rollback requires recompiling old code or keeping full historical local build trees. | Rollback is instant: previous images are locally cached or re-pulled by tag in seconds. |
| **Cost Profile** | $0.00 direct registry cost. | **Usage-Based**: 500 MB/month included in AWS Free Tier for 12 months; $0.10 per GB-month thereafter (~$0.05/month for Alpine images). **$0.00 data transfer** from ECR to EC2 in the same AWS Region (`us-east-1`). |

### ECR Lifecycle Management
To prevent unbounded image accumulation and storage costs, both `clouddeploy-backend` and `clouddeploy-frontend` repositories enforce an automated CloudFormation lifecycle policy:
```json
{
  "rules": [
    {
      "rulePriority": 1,
      "description": "Retain the last 10 immutable production images",
      "selection": {
        "tagStatus": "any",
        "countType": "imageCountMoreThan",
        "countNumber": 10
      },
      "action": {
        "type": "expire"
      }
    }
  ]
}
```

---

## 5. Deployment Mechanism: AWS Systems Manager Run Command

### Why SSM Run Command over SSH?
- **Port 22 Closed**: In accordance with Phase 10 security invariants, TCP port 22 is disabled in the EC2 Security Group.
- **No SSH Key Management**: Eliminates creating, distributing, and rotating SSH private keys. No private keys are stored in GitHub Secrets.
- **Audited Execution**: Every command invocation, target instance, dispatch timestamp, standard output, and standard error is immutably logged in AWS Systems Manager history.
- **Granular IAM Scoping**: The `GitHubActionsDeployRole` is constrained by IAM conditions:
  - Can only execute the specific document: `arn:aws:ssm:${AWS::Region}:*:document/AWS-RunShellScript`.
  - Can only target EC2 instances tagged with `aws:ResourceTag/Name: clouddeploy-ec2-host`.

---

## 6. Multi-Point Health Check Gate & Automated Rollback

### Deployment Safety Sequence

```
1. Validate Arguments
   └─ Ensure BACKEND_TAG and FRONTEND_TAG are full immutable Git SHAs (never 'latest').

2. Read Previous State
   └─ Load /opt/clouddeploy/.current_deploy to identify current known-good SHA tags.

3. Authenticate & Pull Images
   └─ aws ecr get-login-password | docker login
   └─ docker pull <ECR>/clouddeploy-backend:<SHA>
   └─ docker pull <ECR>/clouddeploy-frontend:<SHA>
   └─ If pull fails: Abort immediately. Running containers are untouched. Exit 1.

4. Graceful Container Replacement
   └─ BACKEND_IMAGE=<...> FRONTEND_IMAGE=<...> docker compose -f docker-compose.prod.yml up -d --remove-orphans

5. Multi-Point Health Gate (12 attempts, 5s interval, 60s timeout)
   ├── Check 1: Nginx web root responds on Port 80 (http://127.0.0.1:80/)
   ├── Check 2: Reverse proxy passes /api/health returning 'UP' (http://127.0.0.1:80/api/health)
   └── Check 3: Backend container health status is 'healthy' (docker inspect)

6. Resolution Paths:
   ├── SUCCESS:
   │   ├── Persist new SHA tags to /opt/clouddeploy/.current_deploy
   │   ├── docker image prune -f
   │   └── Exit 0
   │
   └── FAILURE:
       ├── Log failure alert and capture diagnostics (docker compose ps, tail 100 logs)
       ├── Check if PREV_BACKEND_TAG exists in .current_deploy
       ├── If previous tag exists:
       │   ├── Rollback: Redeploy previous known-good containers with previous SHA
       │   ├── Confirm rollback health
       │   └── Exit 1 (Fail GitHub Actions job)
       └── If no previous tag exists:
           ├── Alert: No previous known-good deployment state available
           └── Exit 1
```

---

## 7. GitHub Repository Setup Runbook

### 1. Deploy CI/CD CloudFormation Stack
```bash
aws cloudformation deploy \
  --template-file infra/cloudformation/clouddeploy-cicd.yml \
  --stack-name clouddeploy-cicd \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides \
      EnvironmentName="clouddeploy-prod" \
      GitHubOrg="your-github-username-or-org" \
      GitHubRepo="CloudDeploy" \
      EC2TagValue="clouddeploy-ec2-host"
```

### 2. Configure GitHub Repository Variables
In GitHub under **Settings > Secrets and variables > Actions > Variables**, add:
- `AWS_REGION`: `us-east-1`
- `ENVIRONMENT_NAME`: `clouddeploy-prod`
- `EC2_TAG_VALUE`: `clouddeploy-ec2-host`
- `AWS_BUILD_ROLE_ARN`: `arn:aws:iam::<ACCOUNT_ID>:role/clouddeploy-prod-github-build-role-us-east-1`
- `AWS_DEPLOY_ROLE_ARN`: `arn:aws:iam::<ACCOUNT_ID>:role/clouddeploy-prod-github-deploy-role-us-east-1`

### 3. Create Protected GitHub Environment
In GitHub under **Settings > Environments**:
- Create an environment named **`production`**.
- Configure Deployment Protection Rules:
  - Add required reviewers (optional).
  - Restrict deployment branches to `main` only.

---

## 8. Verification Boundaries & Truthful Status

| Verification Activity | Status | Details |
|---|---|---|
| Workflow YAML Syntax Verification | **VERIFIED** | Statically verified via YAML parser |
| CloudFormation Template Validation | **VERIFIED** | Statically verified via YAML parser |
| Shell Script Syntax Verification | **VERIFIED** | Passed `bash -n` static syntax verification |
| Docker Compose Configuration | **VERIFIED** | Validated `${BACKEND_IMAGE}` & `${FRONTEND_IMAGE}` bindings |
| Backend Automated Integration Tests | **VERIFIED** | 126 tests passed, 0 failures, 0 errors |
| Frontend Production Build | **VERIFIED** | Vite production bundle built successfully |
| Live GitHub Actions Workflow Execution | **NOT PERFORMED** | No live GitHub remote repository connected |
| Live AWS OIDC Token Exchange | **NOT PERFORMED** | No live AWS credentials or cloud execution |
| Live ECR Image Push & Pull | **NOT PERFORMED** | ECR templates authored; live push not executed |
| Live SSM Run Command Execution | **NOT PERFORMED** | Requires running EC2 instance |
| Live EC2 Automated Rollback Test | **NOT PERFORMED** | Logic verified in `deploy.sh`; live test pending staging |
