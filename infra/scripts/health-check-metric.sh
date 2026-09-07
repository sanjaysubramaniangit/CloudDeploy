#!/bin/bash
# ==============================================================================
# CloudDeploy AI — Synthetic Application Health Metric Publisher
# Evaluates http://127.0.0.1:80/api/health through Nginx and publishes
# HealthCheckStatus (1 = UP, 0 = DOWN) to CloudWatch namespace CloudDeploy/Application.
# Designed for execution via cron every 60 seconds on the EC2 host.
# ==============================================================================
set -euo pipefail

# Suppress shell tracing to guarantee secrets are never printed in logs
set +x

AWS_REGION="${AWS_REGION:-us-east-1}"
ENV_NAME="${ENVIRONMENT_NAME:-clouddeploy-prod}"
HEALTH_ENDPOINT="http://127.0.0.1:80/api/health"

# Query the local Nginx ingress with a 5-second timeout
STATUS_VAL=0
if HEALTH_RESP=$(wget -qO- --timeout=5 "$HEALTH_ENDPOINT" 2>/dev/null); then
    if echo "$HEALTH_RESP" | grep -q '"status":"UP"'; then
        STATUS_VAL=1
    fi
fi

# Publish custom metric to Amazon CloudWatch
aws cloudwatch put-metric-data \
    --namespace "CloudDeploy/Application" \
    --metric-name "HealthCheckStatus" \
    --value "$STATUS_VAL" \
    --unit "Count" \
    --dimensions Environment="$ENV_NAME" \
    --region "$AWS_REGION" > /dev/null 2>&1 || true
