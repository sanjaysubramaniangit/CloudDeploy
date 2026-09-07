# AWS S3 File Storage Architecture & Security Guide

## 1. Overview
In **Phase 4**, CloudDeploy AI introduces secure AWS S3-backed file and artifact storage for applications. Users can upload configuration manifests, deployment scripts, build logs, and release artifacts directly associated with their application services.

The storage architecture is designed around security, isolation, and graceful failure handling:
- **Storage Abstraction**: Core services interact with the `StorageService` interface, isolating AWS SDK details within `S3StorageService`.
- **Private S3 Objects**: Buckets and objects are strictly private with public access blocked at the bucket level.
- **Short-Lived Pre-Signed URLs**: Objects are accessed via time-limited pre-signed GET URLs (default 15 minutes), preventing direct or permanent URL leakage.
- **Strict Server-Side Ownership**: Every upload, list, download, and delete request verifies application ownership via JWT identity on the backend.

---

## 2. Storage Flow Architecture

```
[ Client / Browser ]
        │
        │ 1. POST /api/applications/{id}/files (multipart/form-data)
        ▼
[ Spring Boot Controller & Service ]
        │
        │ 2. Verify Application Ownership (JWT SecurityContext)
        │ 3. Validate File (Size < 10MB, safe filename, blocked extensions)
        │ 4. Generate S3 Key: applications/{appId}/files/{uuid}-{sanitizedFilename}
        │
        ├───► [ S3StorageService (AWS SDK v2) ] ───► PutObjectRequest (Private) ───► [ AWS S3 Bucket ]
        │
        └───► [ StoredFileRepository (MySQL/JPA) ] ───► INSERT into stored_files
```

### Pre-Signed Download Workflow
```
[ Client / Browser ]
        │
        │ 1. GET /api/files/{id}/access-url
        ▼
[ Spring Boot Controller & Service ]
        │
        │ 2. Verify User owns parent application
        │ 3. S3Presigner.presignGetObject (Duration = 15 mins)
        ▼
[ Client / Browser ] ◄─── Returns short-lived URL with temporary HMAC signature
        │
        │ 4. Direct GET to AWS S3 using pre-signed URL (browser download)
        ▼
[ AWS S3 Private Bucket ]
```

---

## 3. S3 Object Key Strategy

To guarantee namespace isolation, eliminate cross-tenant collisions, and prevent path traversal, S3 keys follow a standardized pattern:

```
applications/{applicationId}/files/{uuid}-{sanitizedFilename}
```

- **`applicationId`**: Enforces logical tenancy boundaries in the storage bucket.
- **`uuid`**: Ensures that uploading files with identical names does not overwrite previous revisions.
- **`sanitizedFilename`**: Strips path separators (`/`, `\`, `..`), control characters, and spaces, leaving only safe alphanumeric characters, dots, dashes, and underscores.

---

## 4. File Validation & Security Hardening

### 4.1. File Size Limits
- Spring multipart request ceiling configured at **10MB** (`spring.servlet.multipart.max-file-size=10MB`).
- Programmatic service-level check rejects empty files and files exceeding 10MB before reading input streams.

### 4.2. Dangerous Extension Blocklist
MIME types supplied by client browsers are inherently untrusted. The backend enforces an explicit blocklist of executable and executable-adjacent extensions (case-insensitive):
```
.exe, .sh, .bat, .cmd, .jar, .jsp, .dll, .so, .com, .vbs, .msi
```
Attempts to upload files with these extensions are immediately rejected with an HTTP `400 Bad Request` (`File Validation Error`).

### 4.3. Path Traversal & Filename Sanitization
Filenames containing `..`, `/`, or `\` are rejected immediately to mitigate directory escape attacks. Filenames are subsequently sanitized using Spring's `StringUtils.getFilename()` and regular expression replacement (`[^a-zA-Z0-9._-]`).

---

## 5. Multi-Tenant Authorization & IDOR Protection
- **No Client Trust**: The API does not accept `uploadedBy` or `userId` in request payloads. The authenticated caller's identity is extracted from `SecurityContextHolder.getContext().getAuthentication().getName()`.
- **Hierarchical Access Verification**:
  - `GET /api/applications/{id}/files`: Verifies caller owns application `{id}`.
  - `POST /api/applications/{id}/files`: Verifies caller owns application `{id}`.
  - `DELETE /api/files/{id}`: Looks up the `StoredFile` metadata, retrieves parent `application.owner`, and verifies caller ownership.
  - `GET /api/files/{id}/access-url`: Verifies caller owns parent application before calling the S3 presigner.
- **Role Policy**: Users with `ROLE_ADMIN` retain global management capabilities across applications in accordance with system design.

---

## 6. Delete Consistency & Cascading Lifecycle

When a file is deleted:
1. Ownership is verified.
2. The object is deleted from AWS S3 via `storageService.deleteFile(s3Key)`.
3. The metadata row in `stored_files` is removed.

> [!IMPORTANT]
> If S3 deletion encounters an exception, the database record is **preserved** and an error is returned. This prevents database and object store divergence.
>
> When an **Application** is deleted, JPA metadata cascade (`@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)`) cleanly removes all `stored_files` database records. For automated batch S3 object cleanup in future phases, a background event listener or AWS S3 Lifecycle Rule will purge abandoned prefixes.

---

## 7. AWS Credential Handling & Environment Setup

### 7.1. Credential Resolution Chain
CloudDeploy AI relies on the official **AWS SDK v2 `DefaultCredentialsProvider`**:
1. Environment variables (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`).
2. Java system properties (`aws.accessKeyId`, `aws.secretAccessKey`).
3. Local AWS credentials profile (`~/.aws/credentials` created via `aws configure`).
4. Container credentials (ECS / EKS task roles).
5. **EC2 Instance Metadata Service (IMDSv2)** IAM instance profiles (production target).

### 7.2. Required Application Configuration
```properties
aws.region=${AWS_REGION:us-east-1}
aws.s3.bucket=${AWS_S3_BUCKET:}
aws.s3.presigned-url-duration-minutes=${AWS_S3_URL_DURATION:15}
```

### 7.3. Missing Configuration & Graceful Degradation
If `AWS_S3_BUCKET` is omitted:
- Core platform capabilities (Authentication, Application CRUD, Deployment tracking, Dashboard, Health) continue running completely unaffected.
- Only file-storage actions report an HTTP `503 Service Unavailable`:
  `"AWS S3 storage is not configured. Please set AWS_REGION and AWS_S3_BUCKET."`
