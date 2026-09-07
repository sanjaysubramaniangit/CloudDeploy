#!/bin/bash
# ==============================================================================
# CloudDeploy AI — Authoritative Production Deployment & Rollback Script
# Executed on EC2 host via AWS Systems Manager (SSM) Run Command.
# Guarantees immutable SHA deployments, multi-point health check, and safe rollback.
# ==============================================================================
set -euo pipefail

# Suppress shell tracing to guarantee secrets are never printed in logs
set +x

# ------------------------------------------------------------------------------
# 1. Argument Validation: Immutable SHAs Required (No "latest")
# ------------------------------------------------------------------------------
if [ $# -lt 2 ]; then
    echo "ERROR: Missing required arguments." >&2
    echo "Usage: $0 <BACKEND_SHA_TAG> <FRONTEND_SHA_TAG>" >&2
    exit 1
fi

TARGET_BACKEND_TAG="$1"
TARGET_FRONTEND_TAG="$2"

if [ -z "$TARGET_BACKEND_TAG" ] || [ -z "$TARGET_FRONTEND_TAG" ]; then
    echo "ERROR: Target image tags cannot be empty." >&2
    exit 1
fi

if [ "$TARGET_BACKEND_TAG" = "latest" ] || [ "$TARGET_FRONTEND_TAG" = "latest" ]; then
    echo "ERROR: Production deployment MUST use immutable Git commit SHAs, never 'latest'." >&2
    exit 1
fi

# ------------------------------------------------------------------------------
# 2. Configuration & Environment Detection
# ------------------------------------------------------------------------------
APP_DIR="/opt/clouddeploy"
STATE_FILE="${APP_DIR}/.current_deploy"
ENV_FILE="${APP_DIR}/.env"
LOG_DIR="${APP_DIR}/logs"
mkdir -p "$LOG_DIR"
DEPLOY_LOG="${LOG_DIR}/deploy.log"

# Tee all execution output to host deploy.log while preserving stdout for SSM
exec > >(tee -a "$DEPLOY_LOG") 2>&1

AWS_REGION="${AWS_REGION:-us-east-1}"
ENV_NAME="${ENVIRONMENT_NAME:-clouddeploy-prod}"

# Resolve AWS Account ID from IMDSv2 metadata if not explicitly provided
if [ -z "${AWS_ACCOUNT_ID:-}" ]; then
    IMDS_TOKEN=$(curl -s -S -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 60" 2>/dev/null || true)
    if [ -n "$IMDS_TOKEN" ]; then
        AWS_ACCOUNT_ID=$(curl -s -S -H "X-aws-ec2-metadata-token: $IMDS_TOKEN" http://169.254.169.254/latest/dynamic/instance-identity/document 2>/dev/null | grep -o '"accountId"[^,]*' | cut -d'"' -f4 || true)
    fi
fi

AWS_ACCOUNT_ID="${AWS_ACCOUNT_ID:-123456789012}"
ECR_REGISTRY="${ECR_REGISTRY:-${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com}"
BACKEND_REPO="${ENV_NAME}-backend"
FRONTEND_REPO="${ENV_NAME}-frontend"

TARGET_BACKEND_IMAGE="${ECR_REGISTRY}/${BACKEND_REPO}:${TARGET_BACKEND_TAG}"
TARGET_FRONTEND_IMAGE="${ECR_REGISTRY}/${FRONTEND_REPO}:${TARGET_FRONTEND_TAG}"

echo "=============================================================================="
echo " CloudDeploy AI — Production Deployment Started"
echo " Timestamp: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo " Target Backend Tag:  ${TARGET_BACKEND_TAG}"
echo " Target Frontend Tag: ${TARGET_FRONTEND_TAG}"
echo " ECR Registry:        ${ECR_REGISTRY}"
echo "=============================================================================="

# Ensure runtime .env exists (pull secrets if missing)
if [ ! -f "$ENV_FILE" ]; then
    echo "Runtime environment file not found at ${ENV_FILE}. Invoking fetch-secrets.sh..."
    if [ -x "${APP_DIR}/infra/scripts/fetch-secrets.sh" ]; then
        "${APP_DIR}/infra/scripts/fetch-secrets.sh"
    else
        echo "ERROR: Cannot resolve runtime credentials. ${APP_DIR}/infra/scripts/fetch-secrets.sh is missing or not executable." >&2
        exit 1
    fi
fi

# ------------------------------------------------------------------------------
# 3. Read & Validate Previous Deployment State
# ------------------------------------------------------------------------------
PREV_BACKEND_TAG=""
PREV_FRONTEND_TAG=""

if [ -f "$STATE_FILE" ]; then
    echo "Reading current deployment state from ${STATE_FILE}..."
    PREV_BACKEND_TAG=$(grep "^CURRENT_BACKEND_TAG=" "$STATE_FILE" | cut -d'=' -f2- || true)
    PREV_FRONTEND_TAG=$(grep "^CURRENT_FRONTEND_TAG=" "$STATE_FILE" | cut -d'=' -f2- || true)
    echo "  Previous Backend Tag:  ${PREV_BACKEND_TAG:-none}"
    echo "  Previous Frontend Tag: ${PREV_FRONTEND_TAG:-none}"
fi

# ------------------------------------------------------------------------------
# 4. Authenticate Docker with Amazon ECR & Pull Target Images
# ------------------------------------------------------------------------------
echo "Authenticating Docker with Amazon ECR in ${AWS_REGION}..."
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$ECR_REGISTRY"

echo "Pulling target backend image: ${TARGET_BACKEND_IMAGE}..."
if ! docker pull "$TARGET_BACKEND_IMAGE"; then
    echo "ERROR: Failed to pull backend image from ECR. Aborting deployment without modifying running containers." >&2
    exit 1
fi

echo "Pulling target frontend image: ${TARGET_FRONTEND_IMAGE}..."
if ! docker pull "$TARGET_FRONTEND_IMAGE"; then
    echo "ERROR: Failed to pull frontend image from ECR. Aborting deployment without modifying running containers." >&2
    exit 1
fi

# ------------------------------------------------------------------------------
# 5. Gracefully Deploy Target Containers via Docker Compose
# ------------------------------------------------------------------------------
echo "Deploying target containers via Docker Compose..."
cd "$APP_DIR"

export BACKEND_IMAGE="$TARGET_BACKEND_IMAGE"
export FRONTEND_IMAGE="$TARGET_FRONTEND_IMAGE"

docker compose -f docker-compose.prod.yml up -d --remove-orphans

# ------------------------------------------------------------------------------
# 6. Multi-Point Health Check Gate
# ------------------------------------------------------------------------------
echo "Beginning multi-point health check verification (timeout: 60 seconds)..."

HEALTH_PASSED=false
MAX_ATTEMPTS=12
ATTEMPT=1

while [ $ATTEMPT -le $MAX_ATTEMPTS ]; do
    echo "  Health check attempt ${ATTEMPT}/${MAX_ATTEMPTS}..."

    # Check 1: Nginx web root responds on Port 80
    FRONTEND_OK=false
    if wget -q -O /dev/null "http://127.0.0.1:80/" 2>/dev/null; then
        FRONTEND_OK=true
    fi

    # Check 2: Backend health endpoint reachable through Nginx reverse proxy
    PROXY_HEALTH_OK=false
    HEALTH_RESP=$(wget -qO- "http://127.0.0.1:80/api/health" 2>/dev/null || true)
    if echo "$HEALTH_RESP" | grep -q "UP"; then
        PROXY_HEALTH_OK=true
    fi

    # Check 3: Backend container healthy
    BACKEND_CONTAINER_HEALTH=$(docker inspect --format='{{json .State.Health.Status}}' clouddeploy-backend 2>/dev/null || echo '"unknown"')

    if [ "$FRONTEND_OK" = true ] && [ "$PROXY_HEALTH_OK" = true ] && [ "$BACKEND_CONTAINER_HEALTH" = '"healthy"' ]; then
        echo "  Verification SUCCESS: Frontend OK, Nginx proxy /api/health returned UP, Backend container healthy."
        HEALTH_PASSED=true
        break
    fi

    sleep 5
    ATTEMPT=$((ATTEMPT + 1))
done

# ------------------------------------------------------------------------------
# 7. Rollback on Failure vs Success Path
# ------------------------------------------------------------------------------
if [ "$HEALTH_PASSED" != true ]; then
    echo "=============================================================================="
    echo " ALERT: DEPLOYMENT HEALTH CHECK FAILED AFTER 60 SECONDS!"
    echo " Target Backend Tag:  ${TARGET_BACKEND_TAG}"
    echo " Target Frontend Tag: ${TARGET_FRONTEND_TAG}"
    echo "=============================================================================="

    # Capture failure diagnostics to stdout (no secrets dumped)
    echo "--- Container Service Status ---"
    docker compose -f docker-compose.prod.yml ps || true

    echo "--- Recent Backend Container Logs (tail 100) ---"
    docker compose -f docker-compose.prod.yml logs --tail=100 backend || true

    echo "--- Recent Frontend Container Logs (tail 100) ---"
    docker compose -f docker-compose.prod.yml logs --tail=100 frontend || true

    # Rollback execution
    if [ -n "$PREV_BACKEND_TAG" ] && [ -n "$PREV_FRONTEND_TAG" ]; then
        echo "Initiating automatic rollback to previous known-good deployment: ${PREV_BACKEND_TAG}..."
        export BACKEND_IMAGE="${ECR_REGISTRY}/${BACKEND_REPO}:${PREV_BACKEND_TAG}"
        export FRONTEND_IMAGE="${ECR_REGISTRY}/${FRONTEND_REPO}:${PREV_FRONTEND_TAG}"

        docker compose -f docker-compose.prod.yml up -d --remove-orphans

        # Verify rollback health
        sleep 10
        ROLLBACK_HEALTH=$(wget -qO- "http://127.0.0.1:80/api/health" 2>/dev/null || true)
        if echo "$ROLLBACK_HEALTH" | grep -q "UP"; then
            echo "Rollback SUCCESSFUL: Previous known-good version (${PREV_BACKEND_TAG}) restored and healthy."
        else
            echo "CRITICAL: Rollback containers also failed health check. Preserving previous state." >&2
        fi
    else
        echo "CRITICAL: No previous known-good deployment tags recorded in ${STATE_FILE}. Cannot safely roll back." >&2
    fi

    echo "Deployment FAILED. Exiting with non-zero code to notify GitHub Actions."
    exit 1
fi

# ------------------------------------------------------------------------------
# 8. Success: Commit State & Prune Dangling Images
# ------------------------------------------------------------------------------
echo "Persisting new known-good deployment state to ${STATE_FILE}..."
cat << EOF > "$STATE_FILE"
CURRENT_BACKEND_TAG=${TARGET_BACKEND_TAG}
CURRENT_FRONTEND_TAG=${TARGET_FRONTEND_TAG}
PREVIOUS_BACKEND_TAG=${PREV_BACKEND_TAG}
PREVIOUS_FRONTEND_TAG=${PREV_FRONTEND_TAG}
DEPLOYED_AT=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
STATUS=SUCCESS
EOF
chmod 640 "$STATE_FILE"

echo "Pruning dangling Docker images..."
docker image prune -f || true

echo "=============================================================================="
echo " Deployment COMPLETED SUCCESSFULLY"
echo " Active Backend Tag:  ${TARGET_BACKEND_TAG}"
echo " Active Frontend Tag: ${TARGET_FRONTEND_TAG}"
echo " Deployed At:         $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "=============================================================================="
exit 0
