# AI Cloud Troubleshooting Assistant — CloudDeploy AI

## 1. Overview & System Scope

Phase 8 introduces the **AI Cloud Troubleshooting Assistant**, a context-aware Site Reliability Engineering (SRE) diagnostic tool designed to help developers and DevOps engineers debug complex deployment failures, infrastructure misconfigurations, and runtime errors.

The assistant grounds its analysis in:
1. **Server-derived Application & Deployment Metadata** (repository URL, deployment version, commit hash, deployment status, error/release message).
2. **User-Provided Diagnostic Logs & Stack Traces** (container stdout/stderr, pod logs, cloud-init output, exception traces).

---

## 2. Explicit Architectural Boundaries & Limitations

To ensure production truthfulness, the system enforces the following strict boundaries:

> [!IMPORTANT]
> **No Live Cloud / Telemetry Daemon**: The current implementation does **not** connect to live AWS CloudWatch, EC2 APIs, Kubernetes API servers, Docker daemons, or GitHub API commit histories.
> Diagnostic guidance is synthesized strictly from stored metadata and user-provided log snippets.

> [!CAUTION]
> **Display-Only Advisory Commands**: Suggested CLI commands (e.g., `kubectl logs`, `journalctl`, `aws s3 ls`) are generated as **advisory text only**. The CloudDeploy backend **never executes commands** on the host, container, or cloud provider (`Runtime.getRuntime().exec` and `ProcessBuilder` are forbidden). Users must inspect, verify, and run commands manually in their trusted terminals.

> [!NOTE]
> **Commit Hash Grounding**: The system utilizes the recorded `commitHash` (if present) for context grounding. It does not invent or hallucinate full git histories or fake branch trees.

---

## 3. Core Architectural Invariants

### 3.1 Multi-Tenant Ownership & Relationship Validation
- **Application & Deployment Ownership**: A user can only bind troubleshooting sessions to applications and deployments they own (or with ADMIN role). Cross-tenant access is rejected with `403 Forbidden`.
- **Relationship Integrity**: If both an `applicationId` and a `deploymentId` are provided, the backend enforces that `deployment.application.id == application.id`. Mismatches are rejected with `400 Bad Request`.

### 3.2 Immutable Session Context
Once a troubleshooting session is initialized with or without an application/deployment, its context is **immutable**:
- Subsequent chat turns within the same session inherit the session's existing context.
- Attempting to pass a conflicting `applicationId` or `deploymentId` in a later turn of an existing session is rejected with `400 Bad Request`.

### 3.3 Privacy & Credential Sanitization
Before user queries and log snippets are packaged into the LLM prompt, they are scrubbed by `SensitiveDataFilterService`. The following patterns are automatically sanitized:
- AWS Access Keys (`AKIA...`) and Secret Keys
- Bearer tokens and GitHub Personal Access Tokens (`ghp_...`)
- Passwords and secret parameters (`password=...`, `secret=...`)
- Private RSA/SSH keys (`-----BEGIN PRIVATE KEY-----`)
- Credit card and SSN patterns

### 3.4 Command Safety Validation
Before persisting an assistant response, all suggested commands are validated:
- Maximum 10 commands per response.
- Each command must be non-null, non-blank, and $\le 500$ characters.

### 3.5 Atomic Transactional Persistence & Failure Audit Logging
To prevent orphaned state while ensuring complete audit observability:
- The AI provider invocation occurs **outside** the database transaction.
- If the AI call fails or throws an exception, an `AIInteraction` failure record is committed immediately.
- If the AI call succeeds, the session update, user message, assistant message, and `AIInteraction` success record are committed **atomically** in a single transaction.

---

## 4. Domain Entities & Database Schema

### 4.1 `troubleshooting_sessions` Table
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PRIMARY KEY, AUTO_INCREMENT | Unique session identifier |
| `user_id` | BIGINT | NOT NULL, FOREIGN KEY (`users.id`) | Thread owner |
| `application_id` | BIGINT | NULLABLE, FOREIGN KEY (`applications.id`) | Optional grounded application |
| `deployment_id` | BIGINT | NULLABLE, FOREIGN KEY (`deployments.id`) | Optional grounded deployment |
| `title` | VARCHAR(255) | NOT NULL | Deterministically derived session title |
| `created_at` | TIMESTAMP | NOT NULL | Thread creation timestamp |
| `updated_at` | TIMESTAMP | NOT NULL | Last message timestamp |

### 4.2 `troubleshooting_messages` Table
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PRIMARY KEY, AUTO_INCREMENT | Message identifier |
| `session_id` | BIGINT | NOT NULL, FOREIGN KEY (`troubleshooting_sessions.id`) | Parent thread (cascade on delete) |
| `role` | VARCHAR(20) | NOT NULL (`USER`, `ASSISTANT`, `SYSTEM`) | Message sender role |
| `content` | TEXT | NOT NULL | Message body / analysis |
| `severity` | VARCHAR(20) | NULLABLE (`CRITICAL`, `HIGH`, `MEDIUM`, `LOW`, `INFO`) | Assessed severity level |
| `root_cause` | TEXT | NULLABLE | Identified root cause summary |
| `remediation_steps` | TEXT | NULLABLE (JSON string array) | Step-by-step resolution guide |
| `suggested_commands` | TEXT | NULLABLE (JSON string array) | Advisory CLI diagnostic commands |
| `created_at` | TIMESTAMP | NOT NULL | Timestamp |

---

## 5. REST API Specification

All endpoints require JWT Bearer authentication (`Authorization: Bearer <token>`).

### 5.1 Send Troubleshooting Turn
`POST /api/ai/assistant/chat`

**Request Payload**:
```json
{
  "query": "Kubernetes pod is crashing with exit code 137 on startup.",
  "logSnippet": "java.lang.OutOfMemoryError: Java heap space\nKilled",
  "applicationId": 1,
  "deploymentId": 2,
  "sessionId": null
}
```

**Validation Rules**:
- `query`: Required, between 3 and 4,000 characters.
- `logSnippet`: Optional, maximum 8,000 characters.
- `applicationId` / `deploymentId`: Optional, must belong to authenticated user and be mutually consistent.
- `sessionId`: Optional. If omitted, a new session is created.

**Success Response (HTTP 200)**:
```json
{
  "sessionId": 42,
  "sessionTitle": "Kubernetes pod is crashing with exit code 137 on startup.",
  "applicationId": 1,
  "applicationName": "Payment Gateway Service",
  "deploymentId": 2,
  "deploymentVersion": "v1.4.2",
  "assistantMessage": {
    "id": 105,
    "role": "ASSISTANT",
    "content": "The pod termination with exit code 137 indicates an OOMKilled event...",
    "severity": "CRITICAL",
    "rootCause": "Container memory limit exceeded JVM heap allocation.",
    "remediationSteps": [
      "Check pod memory limits in Kubernetes deployment manifest.",
      "Add -XX:MaxRAMPercentage=75.0 to JVM container options.",
      "Increase container memory limit to at least 1Gi."
    ],
    "suggestedCommands": [
      "kubectl describe pod <pod-name> -n production",
      "kubectl logs <pod-name> --previous",
      "kubectl top pod <pod-name>"
    ],
    "createdAt": "2026-09-07T12:00:00Z"
  }
}
```

**Error Responses**:
- `400 Bad Request`: Validation failure, mismatched deployment/application, or attempted context modification.
- `401 Unauthorized`: Missing or invalid JWT.
- `403 Forbidden`: Application or deployment owned by another tenant.
- `503 Service Unavailable`: AI provider unconfigured (`aiProvider.isConfigured() == false`).

### 5.2 List Past Troubleshooting Sessions
`GET /api/ai/assistant/sessions`

Returns lightweight session summaries sorted by `updatedAt` descending.

**Response (HTTP 200)**:
```json
[
  {
    "id": 42,
    "title": "Kubernetes pod is crashing with exit code 137 on startup.",
    "applicationId": 1,
    "applicationName": "Payment Gateway Service",
    "deploymentId": 2,
    "deploymentVersion": "v1.4.2",
    "messageCount": 4,
    "createdAt": "2026-09-07T12:00:00Z",
    "updatedAt": "2026-09-07T12:05:00Z"
  }
]
```

### 5.3 Retrieve Full Session Details
`GET /api/ai/assistant/sessions/{id}`

Returns the full conversation thread including all user messages and assistant diagnostic cards.

### 5.4 Delete Troubleshooting Session
`DELETE /api/ai/assistant/sessions/{id}`

Deletes the session and cascades deletion of all associated messages. Returns `204 No Content`.

---

## 6. Controlled 503 Fallback Behavior

When `AI_PROVIDER` is absent or unconfigured:
- Non-AI endpoints operate normally.
- `POST /api/ai/assistant/chat` returns a clean HTTP 503 response with:
  ```json
  {
    "message": "AI cloud troubleshooting assistant is unavailable because the AI provider is not configured. Configure the AI provider to enable cloud troubleshooting assistance."
  }
  ```
- The React frontend displays an amber alert banner informing the user that the AI provider is not configured.
