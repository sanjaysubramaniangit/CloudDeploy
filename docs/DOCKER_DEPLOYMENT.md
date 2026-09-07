# Production Docker & Deployment Packaging — CloudDeploy AI

## 1. Overview & Architecture

Phase 9 packages CloudDeploy AI into a production-grade, containerized deployment topology using Docker and Docker Compose. It provides complete environment isolation, reproducible packaging, health-checked dependency ordering, and zero-leak credential management.

### 1.1 Multi-Container Network Topology

```
+-----------------------------------------------------------------------+
| Host / Internet                                                       |
+-----------------------------------------------------------------------+
                                  |
                           Port 80 (HTTP)
                                  v
+-----------------------------------------------------------------------+
| Docker Network: clouddeploy-net                                       |
|                                                                       |
|  +-----------------------------------------------------------------+  |
|  | Frontend Service (clouddeploy-frontend)                         |  |
|  | - Image: nginx:1.25-alpine                                      |  |
|  | - Port: 80 (Host mapped 80:80)                                  |  |
|  | - SPA Client-Side Routing: try_files $uri $uri/ /index.html     |  |
|  | - Reverse Proxy: /api/ -> http://backend:8080/api/              |  |
|  +-----------------------------------------------------------------+  |
|                                 |                                     |
|                       Internal HTTP (:8080)                           |
|                                 v                                     |
|  +-----------------------------------------------------------------+  |
|  | Backend Service (clouddeploy-backend)                           |  |
|  | - Base: eclipse-temurin:17-jre-alpine                           |  |
|  | - Non-Root User: appuser (UID 1001)                             |  |
|  | - Port: 8080 (Internal only — not published to host)            |  |
|  | - Healthcheck: GET /api/health via BusyBox wget                 |  |
|  +-----------------------------------------------------------------+  |
|                                 |                                     |
|                       Internal MySQL (:3306)                          |
|                                 v                                     |
|  +-----------------------------------------------------------------+  |
|  | Database Service (clouddeploy-mysql)                            |  |
|  | - Image: mysql:8.0                                              |  |
|  | - Port: 3306 (Internal only — not published to host)            |  |
|  | - Character Set: UTF-8 (utf8mb4 / utf8mb4_unicode_ci)           |  |
|  | - Persistent Volume: mysql_data -> /var/lib/mysql               |  |
|  | - Healthcheck: mysqladmin ping via internal client              |  |
|  +-----------------------------------------------------------------+  |
|                                                                       |
+-----------------------------------------------------------------------+
```

---

## 2. Mandatory Security & Architectural Decisions

### 2.1 Zero Hardcoded Secrets
- `docker-compose.yml` uses mandatory variable substitution syntax (`${VAR:?error}`):
  - `JWT_SECRET: ${JWT_SECRET:?JWT_SECRET must be provided}`
  - `MYSQL_PASSWORD: ${MYSQL_PASSWORD:?MYSQL_PASSWORD must be provided}`
  - `MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD must be provided}`
- If any required secret is missing from `.env`, Docker Compose refuses to start, preventing insecure default credentials.
- `.env.docker.example` contains non-functional documentation placeholders only.

### 2.2 Internal-Only MySQL (No Host Exposure)
- Port `3306` is deliberately **not** exposed to the host machine.
- MySQL accepts connections strictly from containers attached to the internal `clouddeploy-net` bridge network.
- This isolates the database from host port scans, external brute-force attempts, and unauthorized direct access.

### 2.3 Non-Root Container Privileges
- The Spring Boot backend container creates a dedicated system user and group (`appuser:appgroup`, UID/GID 1001) with `/sbin/nologin`.
- The application process runs as `USER appuser`, adhering to the principle of least privilege.

### 2.4 Build Context Isolation (`.dockerignore`)
- Both `backend/.dockerignore` and `frontend/.dockerignore` strictly exclude local `.env` files, credentials, `node_modules/`, Maven `target/`, compilation artifacts, Git metadata, and IDE settings.
- Secrets are never baked into Docker image layers.

### 2.5 Nginx Hardening & Security Headers
- `server_tokens off;` suppresses the Nginx version banner in response headers and default error pages.
- Standard defensive headers are applied:
  - `X-Content-Type-Options "nosniff" always;`
  - `X-Frame-Options "SAMEORIGIN" always;`
- Deprecated headers (`X-XSS-Protection`) and untested CSP headers are omitted to preserve full compatibility with the React/Vite SaaS application.

---

## 3. Health Checks & Dependency Ordering

Docker Compose enforces strict startup ordering using health status conditions:

| Service | Health Check Command | Dependency Condition |
|---|---|---|
| **`mysql`** | `mysqladmin ping -h localhost -u clouddeploy -p$$MYSQL_PASSWORD` | Independent (Starts first) |
| **`backend`** | `wget -qO- http://127.0.0.1:8080/api/health \| grep -q "UP"` | `depends_on: mysql: condition: service_healthy` |
| **`frontend`** | `wget -qO- http://127.0.0.1:80/` | `depends_on: backend: condition: service_healthy` |

> [!NOTE]
> `depends_on` with `condition: service_healthy` guarantees that Spring Boot will not attempt to connect to MySQL until the database engine has initialized and passed its ping check.

---

## 4. Nginx Reverse Proxy & Routing

The frontend Nginx container performs two critical roles:

1. **SPA Client-Side Routing**:
   ```nginx
   location / {
       try_files $uri $uri/ /index.html;
   }
   ```
   Ensures that direct browser access or page refreshes on React routes (`/dashboard`, `/applications`, `/resume-ai`, `/job-match`, `/interview-prep`, `/assistant`) are routed to `index.html` without returning HTTP 404.

2. **Transparent API Reverse Proxy**:
   ```nginx
   location /api/ {
       proxy_pass http://backend:8080/api/;
       proxy_http_version 1.1;
       proxy_set_header Host $host;
       proxy_set_header X-Real-IP $remote_addr;
       proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
       proxy_set_header X-Forwarded-Proto $scheme;
       client_max_body_size 12M;
   }
   ```
   - Requests to `/api/health` map directly to `http://backend:8080/api/health`.
   - Requests to `/api/auth/login` map directly to `http://backend:8080/api/auth/login`.
   - Subpaths are preserved without stripping or duplication.
   - `client_max_body_size 12M;` accommodates 10MB file and resume uploads.

---

## 5. Quickstart & Deployment Instructions

### Prerequisites
- Docker Engine 20.10+
- Docker Compose v2.0+

### Step 1: Configure Environment
Copy the Docker environment template:
```bash
cp .env.docker.example .env
```
Edit `.env` and configure your secure passwords:
```env
MYSQL_PASSWORD=your_secure_db_password
MYSQL_ROOT_PASSWORD=your_secure_root_password
JWT_SECRET=your_256_bit_random_jwt_signing_key_here
```

### Step 2: Build and Start Containers
```bash
docker compose up --build -d
```

### Step 3: Verify Service Health
```bash
docker compose ps
```
Expected output:
```
NAME                   IMAGE                 STATUS                   PORTS
clouddeploy-mysql      mysql:8.0             Up (healthy)             3306/tcp
clouddeploy-backend    clouddeploy-backend   Up (healthy)             8080/tcp
clouddeploy-frontend   clouddeploy-frontend  Up (healthy)             0.0.0.0:80->80/tcp
```

### Step 4: Access Application
- **Frontend SaaS UI**: Open `http://localhost/` in your browser.
- **Backend Health Check**: Open `http://localhost/api/health` (returns `{"service":"CloudDeploy API","status":"UP"}`).

### Step 5: Teardown
To stop containers while preserving database volume:
```bash
docker compose down
```
To stop containers and delete database volume (complete reset):
```bash
docker compose down -v
```

---

## 6. Troubleshooting & FAQ

### MySQL Container Initialization
On the very first launch, MySQL initializes the system tables and creates the `clouddeploy` database. The backend health check will automatically wait for the MySQL health check to succeed before booting up.

### Port 80 Conflicts
If port 80 is already in use by another local web server (e.g. Apache, local Nginx), update the port mapping in `docker-compose.yml`:
```yaml
ports:
  - "8080:80"  # maps host 8080 to container 80
```
Then access the app at `http://localhost:8080/`.

---

## 7. Scope Boundaries

In accordance with project phasing:
- Phase 9 covers containerization and Docker deployment packaging only.
- Live cloud infrastructure (AWS EC2, RDS, VPC, IAM, CloudWatch, GitHub Actions CI/CD pipelines, SSL certificates) is explicitly deferred to subsequent phases.
