# System Architecture Documentation

## 1. Architectural Overview

CloudDeploy AI follows a layered, decoupled architecture designed for enterprise scalability, security, and testability.

```
+-------------------------------------------------------------------------+
|                              PRESENTATION                               |
|       React 18 + Vite SPA | React Router 6 | Custom SaaS Design System   |
|       Context API (Auth & Toast) | Axios HTTP Client + JWT Interceptors |
+-------------------------------------------------------------------------+
                                     │  HTTPS / JSON
                                     ▼
+-------------------------------------------------------------------------+
|                            SPRING BOOT BACKEND                          |
|                                                                         |
|  [Security Filter Chain]                                                |
|  └── JwtAuthenticationFilter -> JwtService -> UserDetailsService        |
|                                                                         |
|  [REST Controllers]                                                     |
|  ├── AuthController          (/api/auth/**)                             |
|  ├── ApplicationController   (/api/applications/**)                     |
|  ├── DeploymentController    (/api/deployments/**)                      |
|  ├── DashboardController     (/api/dashboard)                           |
|  └── HealthController        (/api/health)                              |
|                                                                         |
|  [Global Exception Handler]                                             |
|  └── Intercepts Validation, BadCredentials, 404, 403, and 500 errors     |
|                                                                         |
|  [Service Layer (Business Logic & Authorization)]                       |
|  ├── AuthService             (BCrypt, Token Issuance)                   |
|  ├── ApplicationService      (CRUD, Ownership Validation)               |
|  ├── DeploymentService       (Deployment History, Status Sync)          |
|  └── DashboardService        (Real SQL Aggregations)                    |
|                                                                         |
|  [Data Access Layer (Spring Data JPA)]                                  |
|  ├── UserRepository                                                     |
|  ├── ApplicationRepository                                              |
|  └── DeploymentRepository                                               |
+-------------------------------------------------------------------------+
                                     │  JDBC
                                     ▼
+-------------------------------------------------------------------------+
|                               PERSISTENCE                               |
|        MySQL (Production/Local)  |  H2 In-Memory (Test Isolation)       |
+-------------------------------------------------------------------------+
```

---

## 2. Layer Responsibilities

### 2.1. Presentation Layer (React + Vite)
- **State & Context**:
  - `AuthContext`: Manages authenticated user state, login credentials, and session token storage.
  - `ToastContext`: Displays non-blocking, accessible notifications.
- **Routing & Guards**: `AppLayout` acts as a route guard verifying authentication token validity; unauthorized navigation automatically redirects to `/login`.
- **Atomic UI**: High-reusability components (`Button`, `Card`, `Input`, `StatusBadge`, `ConfirmDialog`, `EmptyState`, `ErrorState`).

### 2.2. Security & Filter Chain
- **Stateless Session**: Configured with `SessionCreationPolicy.STATELESS`.
- **JWT Authentication**: `JwtAuthenticationFilter` intercepts requests, parses `Authorization: Bearer <token>`, validates cryptographic signature and expiration, and populates Spring's `SecurityContextHolder`.
- **CORS Configuration**: Explicit origin whitelist with credential and method policies.

### 2.3. Controller Layer
- Thin controllers responsible only for HTTP request parsing, DTO binding, and status code negotiation (`200 OK`, `201 CREATED`, `204 NO_CONTENT`).
- Input validation enforced via `@Valid` using Jakarta Bean Validation constraints.

### 2.4. Service Layer (Business Logic & Ownership)
- **Encapsulation**: All business validation, ownership checks, and entity mutations reside in `@Service` classes.
- **Ownership Verification**: Extracts the caller's identity directly from `SecurityContextHolder` / `Authentication.getName()`. Compares `application.getOwner().getId()` to the authenticated caller:
  - If identical: operation proceeds.
  - If user possesses `ROLE_ADMIN`: operation proceeds.
  - Otherwise: throws `AccessDeniedCustomException`, resulting in an HTTP `403 Forbidden`.
- **Status Synchronization**: When a `Deployment` record is created, the parent `Application.deploymentStatus` is automatically updated to reflect the latest release state (`SUCCESS`, `RUNNING`, `FAILED`, `PENDING`).

### 2.5. Data Transfer Objects (DTO)
- Internal JPA Entities are never leaked directly across REST endpoints.
- Decouples client presentation contracts from database column names and prevent accidental lazy-loading serialization loops.

---

## 3. Deployment Evolution: Records vs Automation
- **Phase 3 (Current)**: Supports **Deployment History Records** — manual logging and auditing of release metadata (version, commit hash, status, release notes) linked to an application.
- **Phase 4+ (Planned)**: Integration with CI/CD pipelines (GitHub Actions) and AWS infrastructure automation (EC2, S3, RDS, CloudWatch).
