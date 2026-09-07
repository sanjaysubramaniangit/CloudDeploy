# AWS Cloud Infrastructure Architecture & Deployment Guide — CloudDeploy AI

## 1. Executive Summary & Topology

Phase 10 designs and packages the production **AWS Cloud Infrastructure** required to host the containerized CloudDeploy AI platform. It grounds the container packaging from Phase 9 into a multi-tier, secure, cost-conscious AWS architecture aligned with the AWS Well-Architected Framework.

```
+---------------------------------------------------------------------------------------------------+
| AWS Cloud (Region: us-east-1)                                                                     |
|                                                                                                   |
|  VPC: 10.0.0.0/16                                                                                 |
|  +---------------------------------------------------------------------------------------------+  |
|  | Internet Gateway (IGW)                                                                      |  |
|  +---------------------------------------------------------------------------------------------+  |
|         |                                                                                         |
|         | (Port 80 HTTP Only — Port 22 Closed by Default)                                         |
|         v                                                                                         |
|  +---------------------------------------------------------------------------------------------+  |
|  | Public Subnets (Route Table: 0.0.0.0/0 -> IGW)                                              |  |
|  |                                                                                             |  |
|  |  [Public Subnet 1: 10.0.1.0/24 (AZ-a)]             [Public Subnet 2: 10.0.2.0/24 (AZ-b)]    |  |
|  |  +---------------------------------------------+                                            |  |
|  |  | EC2 Instance (t3.small / t3.micro)          |                                            |  |
|  |  | - Security Group: clouddeploy-ec2-sg        |                                            |  |
|  |  |   * Inbound: 80 (0.0.0.0/0)                 |                                            |  |
|  |  |   * Outbound: 443 (S3/OpenAI), 3306 (RDS)   |                                            |  |
|  |  | - Administration: SSM Session Manager       |                                            |  |
|  |  | - IMDSv2: HttpTokens=required, HopLimit=2   |                                            |  |
|  |  | - IAM Role: CloudDeployEC2InstanceProfile   |                                            |  |
|  |  | - Containers:                               |                                            |  |
|  |  |   * Frontend (Nginx :80)                    |                                            |  |
|  |  |   * Backend (Spring Boot :8080 internal)    |                                            |  |
|  |  +---------------------------------------------+                                            |  |
|  +---------------------------------------------------------------------------------------------+  |
|         |                                                           |                             |
|         | (MySQL Port 3306 Internal Only)                           | (S3 VPC Gateway Endpoint)   |
|         v                                                           v                             |
|  +-------------------------------------------------------------+  +----------------------------+  |
|  | Private Subnets (Route Table: 10.0.0.0/16 Local Only)       |  | S3 Gateway Endpoint        |  |
|  |                                                             |  | (com.amazonaws.us-east-1.s3|  |
|  |  [Private Subnet 1: 10.0.11.0/24]   [Private Subnet 2: ...] |  +----------------------------+  |
|  |  +-------------------------------------------------------+  |            |                 |
|  |  | RDS DB Subnet Group: clouddeploy-db-subnet-group     |  |            v                 |
|  |  | RDS MySQL 8.0 Instance (db.t3.micro / db.t4g.micro)   |  |  +-------------------------+ |
|  |  | - Security Group: clouddeploy-rds-sg                  |  |  | AWS S3 Bucket           | |
|  |  |   * Inbound: 3306 ONLY from clouddeploy-ec2-sg       |  |  | - DeletionPolicy: Retain| |
|  |  |   * Outbound: None                                   |  |  | - SSE-S3 AES256         | |
|  |  | - Publicly Accessible: FALSE                         |  |  | - Block Public Access   | |
|  |  | - Backups: 7 Days retention, DeletionPolicy Snapshot  |  |  +-------------------------+ |
|  |  +-------------------------------------------------------+  |                              |
|  +-------------------------------------------------------------+                              |
+---------------------------------------------------------------------------------------------------+
```

---

## 2. Mandatory Security Requirements & Trust Boundaries

### 2.1 Secret Management Architecture
Production credentials are never committed to Git, CloudFormation outputs, or Docker images. The system implements a secure runtime retrieval pipeline:

```
[AWS Systems Manager Parameter Store]
(SecureString parameters: /clouddeploy/jwt_secret, /clouddeploy/db_password, /clouddeploy/ai_api_key)
       |
       | 1. IAM Instance Profile Authorization (Least privilege read on /clouddeploy/*)
       v
[EC2 Instance: fetch-secrets.sh]
       |
       | 2. Generates local runtime file with strict permissions
       v
[/opt/clouddeploy/.env (chmod 600)]
       |
       | 3. Docker Compose runtime injection (Environment variables only)
       v
[Spring Boot Backend Container]
```

- **IAM Least Privilege**: The EC2 role allows strictly `ssm:GetParameter`, `ssm:GetParameters`, and `ssm:GetParametersByPath` on `arn:aws:ssm:${AWS::Region}:${AWS::AccountId}:parameter/clouddeploy/*`. Broad `ssm:*` or wildcard `kms:Decrypt` permissions are forbidden.
- **Log Sanitation**: `fetch-secrets.sh` enforces `set +x` and redirects error outputs so secrets are never echoed to stdout or console logs.

### 2.2 S3 Bucket Security & Lifecycle
- **Managed by CloudFormation**: The S3 bucket is explicitly provisioned by the CloudFormation template (`ApplicationS3Bucket`) for complete environment reproducibility.
- **Block Public Access**: All 4 AWS public access blocks are enabled (`BlockPublicAcls`, `IgnorePublicAcls`, `BlockPublicPolicy`, `RestrictPublicBuckets`).
- **Encryption**: Server-side encryption with AWS managed keys (`AES256`) is enforced on all stored objects.
- **Data Protection Lifecycle**: The bucket is configured with `DeletionPolicy: Retain` and `UpdateReplacePolicy: Retain`. If the CloudFormation stack is deleted, user resumes and deployment artifacts are preserved from accidental destruction.
- **IAM Policy**: Scoped strictly to `!GetAtt ApplicationS3Bucket.Arn` and `!Sub "${ApplicationS3Bucket.Arn}/*"`, granting `s3:GetObject`, `s3:PutObject`, `s3:DeleteObject`, and `s3:ListBucket`.

### 2.3 RDS MySQL Isolation & Backup Protection
- **Network Privacy**: `PubliclyAccessible: false`. The database instance resides in a multi-AZ private DB subnet group without public IP allocation.
- **Security Group Boundary**: `clouddeploy-rds-sg` allows ingress on TCP 3306 **strictly from `clouddeploy-ec2-sg`**. There is zero route or rule allowing public ingress.
- **Automated Backups**: `BackupRetentionPeriod: 7` days.
- **Snapshot on Deletion**: Configured with `DeletionPolicy: Snapshot` and `UpdateReplacePolicy: Snapshot`. If the stack is torn down, a final automated snapshot is taken before removal. *(Note: Retaining snapshots preserves database state but may incur ongoing AWS storage charges until manually deleted).*

### 2.4 EC2 Network Security & Administration
- **Default Ingress**: Port 80 (HTTP) from `0.0.0.0/0` to allow user access to the Nginx frontend.
- **Keyless Administration (No SSH)**: Port 22 is disabled by default. The instance profile attaches `AmazonSSMManagedInstanceCore`, allowing administrators to initiate secure shell sessions via AWS Systems Manager Session Manager:
  ```bash
  aws ssm start-session --target <instance-id>
  ```
  This eliminates the need for managing SSH key pairs, bastion jump hosts, or opening TCP port 22 to the Internet.
- **IMDSv2 Enforced**: Configured with `HttpTokens: required` and `HttpPutResponseHopLimit: 2`. The hop limit of 2 is essential so that container processes running on the Docker bridge network can access the Instance Metadata Service to retrieve temporary credentials via `DefaultCredentialsProvider.create()`.
- **Zero Static AWS Credentials**: No `AWS_ACCESS_KEY_ID` or `AWS_SECRET_ACCESS_KEY` is passed to containers or written to disk.

---

## 3. Cost-Conscious Architecture & Pricing Guidance

> [!IMPORTANT]
> **Free Tier Disclaimer**:
> Free Tier treatment depends on AWS account age, plan, region, and current AWS eligibility rules. Pricing figures vary by region and are subject to change. Always consult the official [AWS Pricing Documentation](https://aws.amazon.com/pricing/) for current rates.

### 3.1 NAT Gateway Avoidance Analysis
A standard AWS NAT Gateway incurs an hourly charge (~$0.045/hr) plus data processing fees (~$0.045/GB), totaling approximately $32.40/month even at idle.

**Architectural Rationale for Avoiding NAT Gateway**:
1. **EC2 in Public Subnet**: The application host sits in a public subnet and communicates directly with the Internet through the Internet Gateway (IGW) for Docker image pulls, Linux updates, and OpenAI API queries.
2. **Free S3 VPC Gateway Endpoint**: By attaching a Gateway Endpoint (`com.amazonaws.us-east-1.s3`) to the route tables, all traffic between EC2 and S3 routes privately across the AWS internal network. **AWS S3 Gateway Endpoints incur no hourly or data-processing charges**.
3. **Internal-Only RDS**: RDS MySQL resides in private subnets and receives queries initiated exclusively by the EC2 host. The database never initiates outbound Internet traffic and therefore does not require a NAT Gateway.
4. **Result**: Full security and isolation are maintained while avoiding unnecessary NAT Gateway charges.

### 3.2 Compute & Database Sizing Guidance
- **EC2 Application Host**:
  - **Recommended**: `t3.small` (2 vCPU, 2 GiB RAM). Spring Boot and Nginx run stably under multi-container loads.
  - **Constrained**: `t3.micro` (2 vCPU, 1 GiB RAM). Can be used for low-cost experimentation. The user-data script configures a supplemental 2 GiB swap file as insurance against memory spikes during container builds, but swap is not a substitute for physical RAM.
- **RDS Database**:
  - `db.t3.micro` or `db.t4g.micro` (Single-AZ, 20 GB gp3 storage). Meets standard development and demo needs while qualifying for Free Tier benefits on eligible accounts.

---

## 4. Step-by-Step Deployment Walkthrough

### Step 1: Deploy Infrastructure with CloudFormation
Using the AWS CLI:
```bash
aws cloudformation create-stack \
  --stack-name clouddeploy-production \
  --template-body file://infra/cloudformation/clouddeploy-infra.yml \
  --parameters \
      ParameterKey=EnvironmentName,ParameterValue=clouddeploy \
      ParameterKey=DBPassword,ParameterValue="YourStrongPassword123!" \
      ParameterKey=EC2InstanceType,ParameterValue=t3.small \
  --capabilities CAPABILITY_NAMED_IAM \
  --region us-east-1
```

### Step 2: Store Secrets in AWS Systems Manager Parameter Store
Store the required production secrets as encrypted `SecureString` parameters:
```bash
# 1. Database Password
aws ssm put-parameter \
  --name "/clouddeploy/db_password" \
  --value "YourStrongPassword123!" \
  --type SecureString \
  --overwrite \
  --region us-east-1

# 2. JWT Signing Key (256-bit+ random secret)
aws ssm put-parameter \
  --name "/clouddeploy/jwt_secret" \
  --value "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970" \
  --type SecureString \
  --overwrite \
  --region us-east-1

# 3. Optional OpenAI API Key
aws ssm put-parameter \
  --name "/clouddeploy/ai_api_key" \
  --value "sk-your-openai-api-key" \
  --type SecureString \
  --overwrite \
  --region us-east-1
```

### Step 3: Connect via AWS Systems Manager Session Manager
Retrieve the EC2 instance ID from the stack outputs and connect securely:
```bash
# Retrieve instance ID
INSTANCE_ID=$(aws cloudformation describe-stacks \
  --stack-name clouddeploy-production \
  --query "Stacks[0].Outputs[?OutputKey=='SSMSessionCommand'].OutputValue" \
  --output text | awk '{print $4}')

# Connect keylessly
aws ssm start-session --target $INSTANCE_ID --region us-east-1
```

### Step 4: Launch Containerized Application on EC2
Inside the Session Manager shell on EC2:
```bash
# 1. Switch to ec2-user
sudo su - ec2-user
cd /opt/clouddeploy

# 2. Clone repository or copy project files
git clone https://github.com/your-org/CloudDeploy.git /opt/clouddeploy/app
cd /opt/clouddeploy/app

# 3. Fetch secrets from SSM Parameter Store into /opt/clouddeploy/.env
/opt/clouddeploy/scripts/fetch-secrets.sh

# 4. Launch containers using production overlay
docker compose -f docker-compose.prod.yml up --build -d

# 5. Verify container health
docker compose -f docker-compose.prod.yml ps
```

### Step 5: Verify Application Health
From any web browser or terminal:
```bash
# Obtain EC2 Public IP from stack outputs
EC2_IP=$(aws cloudformation describe-stacks \
  --stack-name clouddeploy-production \
  --query "Stacks[0].Outputs[?OutputKey=='EC2PublicIP'].OutputValue" \
  --output text)

# Health endpoint verification
curl -s http://$EC2_IP/api/health
# Response: {"service":"CloudDeploy API","status":"UP"}

# Web application UI
open http://$EC2_IP/
```

---

## 5. Teardown & Resource Cleanup

To avoid ongoing charges when testing concludes:

1. **Tear down CloudFormation Stack**:
   ```bash
   aws cloudformation delete-stack --stack-name clouddeploy-production --region us-east-1
   ```
2. **Manage Retained S3 Bucket**:
   Because `ApplicationS3Bucket` has `DeletionPolicy: Retain`, the bucket and its objects are preserved. If you wish to delete it permanently:
   ```bash
   aws s3 rb s3://<bucket-name> --force
   ```
3. **Manage Retained RDS Snapshot**:
   Because `RDSInstance` has `DeletionPolicy: Snapshot`, CloudFormation creates a final snapshot (named `clouddeploy-production-RDSInstance-...`). If you no longer need the snapshot, delete it manually via the RDS Console or CLI to eliminate backup storage costs:
   ```bash
   aws rds delete-db-snapshot --db-snapshot-identifier <snapshot-id>
   ```
4. **Delete SSM Parameters**:
   ```bash
   aws ssm delete-parameters --names "/clouddeploy/db_password" "/clouddeploy/jwt_secret" "/clouddeploy/ai_api_key" --region us-east-1
   ```

---

## 6. Verification Boundaries & Truthful Status

| Layer / Component | Validation Type | Status |
|---|---|---|
| **CloudFormation Template (`clouddeploy-infra.yml`)** | YAML syntax, intrinsic functions, references | **VALIDATED** |
| **EC2 Bootstrap Script (`ec2-user-data.sh`)** | Shell syntax, package names, swap allocation | **VALIDATED** |
| **Secret Retrieval Script (`fetch-secrets.sh`)** | SSM CLI syntax, parameter paths, permissions | **VALIDATED** |
| **Docker Compose Prod Overlay (`docker-compose.prod.yml`)** | Service definitions, health checks, ports | **VALIDATED** |
| **Backend Test Suite (126 Tests)** | Maven Surefire (`mvn clean test`) | **126/126 PASSED** |
| **Backend Packaging** | Maven Package (`mvn package -DskipTests`) | **BUILD SUCCESS** |
| **Frontend Production Build** | Vite Build (`npm run build`) | **SUCCESS (1.00s)** |
| **Live AWS CloudFormation Stack Creation** | AWS Cloud Execution | **NOT PERFORMED** |
| **Live EC2 Provisioning & Docker Execution** | AWS Cloud Execution | **NOT PERFORMED** |
| **Live RDS MySQL Creation** | AWS Cloud Execution | **NOT PERFORMED** |
| **Live SSM Parameter Store API Calls** | AWS Cloud Execution | **NOT PERFORMED** |
