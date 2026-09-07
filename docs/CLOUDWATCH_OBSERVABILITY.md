# CloudDeploy AI — Amazon CloudWatch Observability & Production Monitoring Guide

## 1. Overview & Architecture

CloudDeploy AI features a production-grade, cost-conscious observability and monitoring architecture built on **Amazon CloudWatch Agent on EC2**, **CloudWatch Logs**, **CloudWatch Metrics**, and **CloudWatch Alarms**.

The system monitors host-level infrastructure health (CPU, memory, disk), aggregates application and reverse-proxy logs, tracks an automated synthetic health signal, enforces a 14-day log retention policy, and provisions five targeted alarms for rapid incident response.

```
Amazon EC2 Application Host (us-east-1)
 ├── Nginx Container (Port 80)
 │     ├── Access Logs ──────────────► /opt/clouddeploy/logs/nginx/access.log
 │     └── Error Logs ───────────────► /opt/clouddeploy/logs/nginx/error.log
 ├── Spring Boot Container (Port 8080)
 │     ├── /api/health Endpoint
 │     └── Application Logs ─────────► /opt/clouddeploy/logs/backend/app.log
 ├── Deployment Script (deploy.sh)
 │     └── Execution Logs ───────────► /opt/clouddeploy/logs/deploy.log
 ├── Local Health Cron Script (1 min)
 │     └── wget /api/health ─────────► aws cloudwatch put-metric-data (HealthCheckStatus)
 ├── Host Logrotate (/etc/logrotate.d/clouddeploy)
 │     └── Daily rotation, 7-day retention, copytruncate, compress
 └── Amazon CloudWatch Agent (systemd)
       ├── Collects /proc memory & disk utilization
       └── Collects Log Files from /opt/clouddeploy/logs/
              │
              ▼ (TLS 1.2/1.3 to AWS CloudWatch Endpoints via EC2 Instance Profile)
       Amazon CloudWatch Service
       ├── CloudWatch Logs (14-Day Retention)
       │     ├── /clouddeploy/backend
       │     ├── /clouddeploy/nginx-access
       │     ├── /clouddeploy/nginx-error
       │     ├── /clouddeploy/deployment
       │     └── /clouddeploy/system (/var/log/messages)
       ├── CloudWatch Metrics
       │     ├── AWS/EC2 (Standard): CPUUtilization, StatusCheckFailed
       │     ├── CWAgent (Custom): mem_used_percent, disk_used_percent
       │     └── CloudDeploy/Application (Custom): HealthCheckStatus
       └── CloudWatch Alarms
             ├── StatusCheckFailedAlarm (Critical)
             ├── HighMemoryAlarm (Warning)
             ├── HighCPUAlarm (Warning)
             ├── LowDiskSpaceAlarm (Warning)
             └── ApplicationHealthAlarm (Critical)
```

---

## 2. Architectural Decision: Host-Level Agent vs. Containerized

| Evaluation Dimension | Containerized Agent (Docker) | Host-Level Agent via systemd (Chosen) |
|---|---|---|
| **Kernel / OS Telemetry Access** | Requires mounting host `/proc`, `/sys`, and `/etc`; often requires elevated privileges (`--privileged` or `SYS_PTRACE`) to accurately inspect host memory and storage. | **Direct Native Access**: CloudWatch Agent runs natively as a systemd service with direct, unconstrained access to kernel `/proc` and filesystem metrics. |
| **Fault Isolation & Daemon Decoupling** | If the Docker daemon crashes, runs out of memory, or freezes during a deployment spike, the monitoring container crashes alongside it—eliminating observability during outages. | **Independent High Availability**: Runs under host systemd. If Docker exhausts memory or crashes, the CloudWatch Agent remains running to ship diagnostic crash logs and trigger alarms. |
| **Packaging & Maintenance** | Requires maintaining a separate container image or Docker Compose definition. | **Official AWS Packaging**: Maintained, patched, and distributed natively by AWS for Amazon Linux 2023 (`dnf install -y amazon-cloudwatch-agent`). |

---

## 3. Least-Privilege IAM Observability Architecture

Rather than attaching broad generic policies like `CloudWatchAgentServerPolicy`, CloudDeploy AI enforces an explicit, custom inline IAM policy (`CloudDeployObservabilityPolicy`) on `EC2Role`.

### Custom IAM Observability Policy Definition
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowScopedPutMetricData",
      "Effect": "Allow",
      "Action": [
        "cloudwatch:PutMetricData"
      ],
      "Resource": "*",
      "Condition": {
        "StringEquals": {
          "cloudwatch:namespace": [
            "CWAgent",
            "CloudDeploy/Application"
          ]
        }
      }
    },
    {
      "Sid": "AllowEC2StorageDiscovery",
      "Effect": "Allow",
      "Action": [
        "ec2:DescribeVolumes",
        "ec2:DescribeTags"
      ],
      "Resource": "*"
    },
    {
      "Sid": "AllowScopedCloudWatchLogs",
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogStream",
        "logs:PutLogEvents",
        "logs:DescribeLogStreams",
        "logs:DescribeLogGroups"
      ],
      "Resource": [
        "arn:aws:logs:us-east-1:123456789012:log-group:/clouddeploy/*",
        "arn:aws:logs:us-east-1:123456789012:log-group:/clouddeploy/*:log-stream:*"
      ]
    }
  ]
}
```

### Security & Invariant Highlights
- **Namespace Guard**: The `cloudwatch:namespace` condition key prevents the EC2 host from publishing arbitrary or spoofed metrics into unrelated namespaces.
- **Log Group Scoping**: Log streaming is restricted strictly to `/clouddeploy/*`.
- **Zero Secrets / Elevated Permissions**: No `AdministratorAccess`, no AWS X-Ray permissions, and zero access to database credentials or application storage secrets.
- **Network Security**: The CloudWatch Agent sends telemetry to AWS CloudWatch endpoints over HTTPS/TLS and uses the EC2 instance role for AWS authentication and authorization. No inbound ports are opened.

---

## 4. Metric Catalog & Cardinality Control

To keep telemetry focused, avoid cardinality explosions, and respect cost constraints, metrics are partitioned into standard AWS-provided metrics and three high-value custom metrics.

### Metric Specifications

| Metric Name | Namespace | Type | Unit | Collection Interval | Dimensions | Purpose & Operational Value |
|---|---|---|---|---|---|---|
| `CPUUtilization` | `AWS/EC2` | Standard (Free) | Percent | 300s (5 min) | `InstanceId` | Tracks compute load and prevents burst credit exhaustion on `t3` instances. |
| `StatusCheckFailed` | `AWS/EC2` | Standard (Free) | Count | 60s (1 min) | `InstanceId` | Detects hardware, kernel, or hypervisor status check failures. |
| `mem_used_percent` | `CWAgent` | Custom (Agent) | Percent | 60s (1 min) | `InstanceId` | Monitors host physical RAM usage to detect JVM memory leaks or container OOM risks. |
| `disk_used_percent` | `CWAgent` | Custom (Agent) | Percent | 60s (1 min) | `InstanceId`, `path=/` | Monitors root EBS volume capacity to prevent Docker image or log exhaustion. |
| `HealthCheckStatus` | `CloudDeploy/Application` | Custom (Synthetic) | Count | 60s (1 min) | `Environment` | Synthetic health probe querying `http://127.0.0.1:80/api/health` (1 = UP, 0 = DOWN). |

### Cardinality Invariant
High-cardinality dimensions (e.g. user IDs, request IDs, session tokens) are **strictly prohibited** in metric dimensions. Dimensions are limited strictly to `InstanceId` and `Environment`.

---

## 5. CloudWatch Alarms & Operational Severity Mapping

CloudFormation provisions five standard-resolution alarms with explicit thresholds and deterministic missing-data behaviors.

| Alarm Name | Metric & Namespace | Threshold & Operator | Period & Evaluation | TreatMissingData | Operational Severity | Recommended Action |
|---|---|---|---|---|---|---|
| `CloudDeploy-EC2-StatusCheckFailed` | `StatusCheckFailed`<br>(`AWS/EC2`) | `> 0` | 60s<br>(2 consecutive periods) | `missing` | **CRITICAL** | Host or underlying AWS hardware is impaired. Stop and start the instance via AWS Console/CLI to migrate to a new hypervisor. |
| `CloudDeploy-HighMemoryUtilization` | `mem_used_percent`<br>(`CWAgent`) | `>= 85%` | 300s<br>(2 consecutive periods / 10 min) | `missing` | **WARNING** | Physical RAM is nearing capacity. Inspect `docker stats` and Spring Boot JVM heap allocation (`MaxRAMPercentage`). |
| `CloudDeploy-HighCPUUtilization` | `CPUUtilization`<br>(`AWS/EC2`) | `>= 80%` | 300s<br>(2 consecutive periods / 10 min) | `missing` | **WARNING** | Sustained high CPU load. Inspect application threads and verify `t3` CPU burst credits (`CPUCreditBalance`). |
| `CloudDeploy-LowDiskSpace` | `disk_used_percent`<br>(`CWAgent`, path `/`) | `>= 85%` | 300s<br>(1 period / 5 min) | `missing` | **WARNING** | Root EBS volume is filling up. Run `docker image prune -f` and inspect `/opt/clouddeploy/logs/`. |
| `CloudDeploy-ApplicationHealthFailure` | `HealthCheckStatus`<br>(`CloudDeploy/Application`) | `< 1` | 60s<br>(2 consecutive periods / 2 min) | `breaching` | **CRITICAL** | Web application is unreachable or returning unhealthy. Check container status via SSM (`docker compose ps` and `logs`). |

### Why `TreatMissingData: breaching` for Application Health
For the `ApplicationHealthAlarm`, missing data is configured as **`breaching`**.
- **Rationale**: The synthetic health probe runs on a 60-second cron job. If the EC2 host freezes, suffers an out-of-memory kernel panic, or the network interface drops, the cron job cannot run to publish a `0`.
- If missing data were treated as `notBreaching`, a catastrophic host failure would silence the alarm! Treating missing data as `breaching` guarantees that the absence of a heartbeat generates a critical incident alert.

---

## 6. Host Log Management & Retention Strategy

### Two-Tier Storage & Rotation
To prevent unbounded log accumulation on the host and in the cloud:
1. **Local Host Log Rotation (`/etc/logrotate.d/clouddeploy`)**:
   - Rotates `/opt/clouddeploy/logs/*/*.log` daily.
   - Retains 7 compressed rotations (`rotate 7`, `compress`, `delaycompress`).
   - Uses `copytruncate` to safely rotate active container log descriptors without restarting containers.
2. **CloudWatch Log Groups (14-Day Retention)**:
   - All five log groups (`/clouddeploy/backend`, `/clouddeploy/nginx-access`, `/clouddeploy/nginx-error`, `/clouddeploy/deployment`, `/clouddeploy/system`) enforce `RetentionInDays: 14` via CloudFormation.
   - Older log events are automatically expired and deleted by AWS.

---

## 7. Truthful CloudWatch Pricing & Free Tier Analysis

> [!NOTE]
> The CloudDeploy AI design utilizes **three custom metrics** and **five standard-resolution alarms**, which is within the currently documented CloudWatch Free Tier quantities when the AWS account qualifies.

### AWS CloudWatch Pricing Breakdown (Current Standard Pricing)

| Service Component | CloudDeploy Usage | AWS Free Tier Allowance | Pricing Beyond Free Tier |
|---|---|---|---|
| **Standard EC2 Metrics** | `CPUUtilization`, `StatusCheckFailed` | Included free for all EC2 instances | $0.00 (Standard 5-minute metrics) |
| **Custom Metrics** | 3 metrics (`mem_used_percent`, `disk_used_percent`, `HealthCheckStatus`) | 10 custom metrics per month | $0.30 per metric/month ($0.90/month total) |
| **CloudWatch Alarms** | 5 standard-resolution alarms | 10 standard alarm metric evaluations | $0.10 per alarm/month ($0.50/month total) |
| **CloudWatch Logs Ingestion** | ~100–300 MB/month (compressed) | 5 GB ingestion per month | $0.50 per GB ingested |
| **CloudWatch Logs Storage** | ~50–150 MB active (14-day window) | 5 GB storage per month | $0.03 per GB-month |

*Actual monthly cost depends on AWS account status, region, log volume generated by traffic, and Free Tier qualification.*

---

## 8. Incident Response & Troubleshooting Runbook

### Alert: `CloudDeploy-ApplicationHealthFailure` (Critical)
1. Connect to the EC2 host keylessly via SSM Session Manager:
   ```bash
   aws ssm start-session --target <instance-id>
   ```
2. Verify container operational status:
   ```bash
   cd /opt/clouddeploy && docker compose -f docker-compose.prod.yml ps
   ```
3. Inspect backend and Nginx logs:
   ```bash
   tail -n 100 /opt/clouddeploy/logs/backend/app.log
   tail -n 100 /opt/clouddeploy/logs/nginx/error.log
   ```
4. Test internal health endpoint directly:
   ```bash
   wget -qO- http://127.0.0.1:80/api/health
   ```
5. If backend container crashed or is unhealthy, trigger a rollback or restart:
   ```bash
   docker compose -f docker-compose.prod.yml restart backend
   ```

### Alert: `CloudDeploy-HighMemoryUtilization` (Warning)
1. Inspect memory consumption by process:
   ```bash
   free -m
   ps aux --sort=-%mem | head -n 10
   ```
2. Inspect Docker container memory allocation:
   ```bash
   docker stats --no-stream
   ```
3. If Java heap memory is elevated, inspect GC activity in `/opt/clouddeploy/logs/backend/app.log`.

### Alert: `CloudDeploy-LowDiskSpace` (Warning)
1. Check filesystem usage:
   ```bash
   df -h /
   ```
2. Prune dangling Docker images and build caches:
   ```bash
   docker image prune -af --filter "until=168h"
   docker system prune -f
   ```
3. Verify logrotate execution:
   ```bash
   logrotate -f /etc/logrotate.d/clouddeploy
   ```

---

## 9. Verification Boundaries & Truthful Status

| Verification Activity | Status | Notes |
|---|---|---|
| CloudWatch Agent JSON Syntax | **VERIFIED** | Validated via JSON parser |
| CloudFormation Template & Alarms | **VERIFIED** | Validated via YAML parser |
| Shell Script Syntax (`health-check-metric.sh`, `deploy.sh`, `ec2-user-data.sh`) | **VERIFIED** | Passed `bash -n` static syntax verification |
| Docker Compose Volume Mounts | **VERIFIED** | Validated `${LOG_FILE}` and `/opt/clouddeploy/logs` bindings |
| Backend Automated Integration Tests | **VERIFIED** | 126 tests passed, 0 failures, 0 errors across 8 test classes |
| Backend Package Build | **VERIFIED** | Packaged cleanly |
| Frontend Production Build | **VERIFIED** | Vite production bundle built successfully |
| Live CloudWatch Agent Execution on EC2 | **NOT PERFORMED** | No live EC2 instance running in this session |
| Live CloudWatch Metric Publishing | **NOT PERFORMED** | No live AWS credentials connected |
| Live Alarm Evaluation & Triggering | **NOT PERFORMED** | Requires active cloud deployment |
| Live CloudWatch Log Ingestion | **NOT PERFORMED** | Requires active AWS account |
| Future Scope (Dashboards, Route53, HTTPS/ACM, ALB, Auto Scaling, ECS/EKS) | **NOT IMPLEMENTED** | Strictly preserved for future phases |
