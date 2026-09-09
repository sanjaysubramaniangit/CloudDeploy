#!/bin/bash
# ==============================================================================
# CloudDeploy AI — Runtime Secret Retrieval from AWS SSM Parameter Store
# Generates /opt/clouddeploy/.env with strict permissions (600)
# ==============================================================================
set -euo pipefail

# Suppress command tracing to guarantee secrets are never printed in logs
set +x

# Target runtime environment file
ENV_FILE="/opt/clouddeploy/.env"
AWS_REGION="${AWS_REGION:-us-east-1}"

# Preserve existing DB_HOST and AWS_S3_BUCKET from .env if not explicitly set in caller environment
if [ -z "${DB_HOST:-}" ] && [ -f "$ENV_FILE" ]; then
    DB_HOST=$(grep "^DB_HOST=" "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- || true)
fi
if [ -z "${AWS_S3_BUCKET:-}" ] && [ -f "$ENV_FILE" ]; then
    AWS_S3_BUCKET=$(grep "^AWS_S3_BUCKET=" "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- || true)
fi

DB_HOST="${DB_HOST:?Error: DB_HOST (RDS endpoint) must be set}"
DB_USERNAME="${DB_USERNAME:-clouddeploy}"
DB_NAME="${DB_NAME:-clouddeploy}"
DB_PORT="${DB_PORT:-3306}"
AWS_S3_BUCKET="${AWS_S3_BUCKET:?Error: AWS_S3_BUCKET must be set}"

echo "Connecting to AWS SSM Parameter Store in ${AWS_REGION}..."

# 1. Fetch DB Password from SSM Parameter Store (/clouddeploy/db_password)
DB_PASS=$(aws ssm get-parameter \
    --name "/clouddeploy/db_password" \
    --with-decryption \
    --region "$AWS_REGION" \
    --query "Parameter.Value" \
    --output text 2>/dev/null || true)

if [ -z "$DB_PASS" ]; then
    echo "ERROR: Failed to retrieve /clouddeploy/db_password from SSM Parameter Store." >&2
    exit 1
fi

# 2. Fetch JWT Secret from SSM Parameter Store (/clouddeploy/jwt_secret)
JWT_SEC=$(aws ssm get-parameter \
    --name "/clouddeploy/jwt_secret" \
    --with-decryption \
    --region "$AWS_REGION" \
    --query "Parameter.Value" \
    --output text 2>/dev/null || true)

if [ -z "$JWT_SEC" ]; then
    echo "ERROR: Failed to retrieve /clouddeploy/jwt_secret from SSM Parameter Store." >&2
    exit 1
fi

# 3. Fetch optional AI API Key from SSM Parameter Store (/clouddeploy/ai_api_key)
AI_KEY=$(aws ssm get-parameter \
    --name "/clouddeploy/ai_api_key" \
    --with-decryption \
    --region "$AWS_REGION" \
    --query "Parameter.Value" \
    --output text 2>/dev/null || true)

AI_PROVIDER="openai"
if [ -z "$AI_KEY" ]; then
    AI_PROVIDER=""
    echo "INFO: /clouddeploy/ai_api_key not configured; AI features will operate in graceful 503 fallback mode."
fi

# 4. Write runtime .env file atomically with strict 600 permissions
TMP_ENV="${ENV_FILE}.tmp.$$"
touch "$TMP_ENV"
chmod 600 "$TMP_ENV"

cat << EOF > "$TMP_ENV"
# ==============================================================================
# CloudDeploy AI — Auto-generated runtime environment
# Generated at: $(date -u +"%Y-%m-%dT%H:%M:%SZ")
# Permissions: 600
# DO NOT COMMIT OR EXPOSE THIS FILE
# ==============================================================================
DB_HOST=${DB_HOST}
DB_PORT=${DB_PORT}
DB_NAME=${DB_NAME}
DB_USERNAME=${DB_USERNAME}
DB_PASSWORD=${DB_PASS}
DB_DIALECT=org.hibernate.dialect.MySQLDialect

JWT_SECRET=${JWT_SEC}
JWT_EXPIRATION=86400000

AWS_REGION=${AWS_REGION}
AWS_S3_BUCKET=${AWS_S3_BUCKET}
AWS_S3_URL_DURATION=15

AI_PROVIDER=${AI_PROVIDER}
AI_API_KEY=${AI_KEY}
AI_MODEL=gpt-4o-mini
AI_BASE_URL=https://api.openai.com/v1
AI_TIMEOUT_SECONDS=30
EOF

chmod 600 "$TMP_ENV"
mv -f "$TMP_ENV" "$ENV_FILE"
echo "Runtime environment successfully generated at ${ENV_FILE} (permissions: 600)."
