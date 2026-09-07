# Database Architecture & Schema Documentation

## 1. Overview
CloudDeploy AI uses a relational schema designed around core domain models: **Users**, **Applications**, and **Deployments**. The data layer is managed with Spring Data JPA and Hibernate ORM, supporting MySQL in production/local environments and in-memory H2 for deterministic, isolated integration testing.

---

## 2. Entity-Relationship Diagram

```
+--------------------+            +-----------------------+            +-----------------------+
|       users        | 1        * |     applications      | 1        * |      deployments      |
+--------------------+------------+-----------------------+------------+-----------------------+
| id (PK, BIGINT)    |            | id (PK, BIGINT)       |            | id (PK, BIGINT)       |
| name (VARCHAR)     |            | name (VARCHAR(100))   |            | application_id (FK)   |
| email (VARCHAR, UQ)|            | description (TEXT)    |            | version (VARCHAR(50)) |
| password (VARCHAR) |            | repository_url (TEXT) |            | commit_hash (VARCHAR) |
| role (VARCHAR)     |            | deployment_status (EN)|            | status (ENUM)         |
| created_at (DATETM)|            | user_id (FK, BIGINT)  |            | deployment_msg (TEXT) |
| updated_at (DATETM)|            | created_at (DATETIME) |            | deployed_at (DATETIME)|
+--------------------+            | updated_at (DATETIME) |            +-----------------------+
                                  +-----------------------+
```

---

## 3. Schema Definitions

### 3.1. `users` Table
Stores authenticated user accounts, roles, and hashed credentials.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT` | `PRIMARY KEY AUTO_INCREMENT` | Unique identifier |
| `name` | `VARCHAR(255)` | `NOT NULL` | Full name of the user |
| `email` | `VARCHAR(255)` | `NOT NULL UNIQUE` | Authentication identity |
| `password` | `VARCHAR(255)` | `NOT NULL` | BCrypt-hashed password (cost factor 10) |
| `role` | `VARCHAR(50)` | `NOT NULL` | `USER` or `ADMIN` |
| `created_at` | `DATETIME(6)` | `NOT NULL` | Account registration timestamp |
| `updated_at` | `DATETIME(6)` | `NULL` | Profile update timestamp |

### 3.2. `applications` Table
Represents application services tracked by users.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT` | `PRIMARY KEY AUTO_INCREMENT` | Unique identifier |
| `name` | `VARCHAR(100)` | `NOT NULL` | Application name (2–100 chars) |
| `description` | `VARCHAR(500)` | `NULL` | Service description or purpose |
| `repository_url`| `VARCHAR(255)` | `NULL` | Git repository (HTTP/HTTPS or SSH) |
| `deployment_status` | `VARCHAR(50)` | `NOT NULL` | Current state: `PENDING`, `RUNNING`, `SUCCESS`, `FAILED`, `OFFLINE` |
| `user_id` | `BIGINT` | `NOT NULL, FK -> users(id)` | Application owner |
| `created_at` | `DATETIME(6)` | `NOT NULL` | Creation timestamp |
| `updated_at` | `DATETIME(6)` | `NULL` | Modification timestamp |

### 3.3. `deployments` Table
Maintains an immutable historical record of deployment events for an application.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT` | `PRIMARY KEY AUTO_INCREMENT` | Unique identifier |
| `application_id`| `BIGINT` | `NOT NULL, FK -> applications(id)` | Associated application |
| `version` | `VARCHAR(50)` | `NOT NULL` | Release version (e.g. `v1.0.0`) |
| `commit_hash` | `VARCHAR(40)` | `NULL` | Git commit SHA |
| `status` | `VARCHAR(50)` | `NOT NULL` | Deployment state: `PENDING`, `RUNNING`, `SUCCESS`, `FAILED` |
| `deployment_message` | `VARCHAR(1000)` | `NULL` | Deployment logs, release notes |
| `deployed_at` | `DATETIME(6)` | `NOT NULL` | Time deployment was recorded |

---

## 4. Cascading & Dependent Deletion
To prevent orphaned deployment records, the JPA relationship between `Application` and `Deployment` is mapped as:

```java
@OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Deployment> deployments = new ArrayList<>();
```

When an application is deleted:
1. Hibernate automatically deletes all associated `deployments` records where `application_id = application.id`.
2. The `applications` record itself is removed.
3. Test suites and fixtures respect this deletion order (`deployments` -> `applications` -> `users`) to ensure relational integrity constraints are never violated.

---

## 5. Indexes & Performance Considerations
- **Foreign Key Indexing**: `applications(user_id)` and `deployments(application_id)` are indexed to ensure efficient ownership filtering and parent-child lookups.
- **Timestamp Ordering**: Indices on `applications(updated_at DESC)` and `deployments(deployed_at DESC)` enable fast top-N recent activity retrieval for dashboard queries.
- **User Email Unique Index**: `users(email)` is backed by a unique index for \(O(1)\) authentication lookup.

---

## 6. Environment-Based Configuration
Database credentials and dialects are entirely externalized via environment variables:
```properties
spring.datasource.url=jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:clouddeploy}?createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&useSSL=false
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:password}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.properties.hibernate.dialect=${DB_DIALECT:org.hibernate.dialect.MySQLDialect}
```
For integration tests, `src/test/resources/application-test.properties` overrides these properties to utilize an in-memory H2 instance (`jdbc:h2:mem:testdb`), allowing complete test suite execution without requiring an external MySQL daemon.
