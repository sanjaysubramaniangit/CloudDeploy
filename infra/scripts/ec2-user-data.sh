#!/bin/bash
# ==============================================================================
# CloudDeploy AI — EC2 Host Bootstrap (User Data Script)
# Target OS: Amazon Linux 2023 (x86_64)
# ==============================================================================
set -euo pipefail

echo "========================================================"
echo "Starting CloudDeploy AI host provisioning..."
echo "========================================================"

# 1. Update system packages
dnf update -y

# 2. Configure supplemental 2 GiB swap file (insurance for build spikes)
SWAP_FILE="/swapfile"
if [ ! -f "$SWAP_FILE" ]; then
    echo "Configuring 2 GiB supplemental swap file..."
    fallocate -l 2G "$SWAP_FILE"
    chmod 600 "$SWAP_FILE"
    mkswap "$SWAP_FILE"
    swapon "$SWAP_FILE"
    echo "$SWAP_FILE swap swap defaults 0 0" >> /etc/fstab
    echo "Swap configured successfully."
else
    echo "Swap file already exists, skipping creation."
fi

# 3. Install Docker and Git
echo "Installing Docker and Git..."
dnf install -y docker git
systemctl enable --now docker
usermod -aG docker ec2-user

# 4. Install Docker Compose plugin
DOCKER_CONFIG_DIR="/usr/local/lib/docker/cli-plugins"
mkdir -p "$DOCKER_CONFIG_DIR"
COMPOSE_VERSION="v2.24.5"
curl -SL "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-x86_64" \
    -o "${DOCKER_CONFIG_DIR}/docker-compose"
chmod +x "${DOCKER_CONFIG_DIR}/docker-compose"

# 5. Create deployment and log directory structure with non-root permissions
APP_DIR="/opt/clouddeploy"
mkdir -p "${APP_DIR}/scripts"
mkdir -p "${APP_DIR}/logs/backend"
mkdir -p "${APP_DIR}/logs/nginx"
chown -R ec2-user:ec2-user "$APP_DIR"
# Ensure Spring Boot non-root appuser (UID 1001) can write to mounted logs
chown -R 1001:1001 "${APP_DIR}/logs/backend"
chmod -R 775 "${APP_DIR}/logs"

# 6. Configure host-level log rotation to protect root EBS volume
cat << 'EOF' > /etc/logrotate.d/clouddeploy
/opt/clouddeploy/logs/*/*.log /opt/clouddeploy/logs/*.log {
    daily
    rotate 7
    missingok
    notifempty
    compress
    delaycompress
    copytruncate
}
EOF
chmod 644 /etc/logrotate.d/clouddeploy

# 7. Install and start Amazon CloudWatch Agent
echo "Installing Amazon CloudWatch Agent..."
dnf install -y amazon-cloudwatch-agent

# CloudWatch Agent configuration is provisioned by CloudFormation UserData
if [ -f "${APP_DIR}/infra/cloudwatch/amazon-cloudwatch-agent.json" ]; then
    mkdir -p /opt/aws/amazon-cloudwatch-agent/etc
    cp "${APP_DIR}/infra/cloudwatch/amazon-cloudwatch-agent.json" /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json
    /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
        -a fetch-config -m ec2 -s -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json
    systemctl enable amazon-cloudwatch-agent
fi

# 8. Register synthetic health check metric cron (every 60 seconds)
CRON_JOB="* * * * * /opt/clouddeploy/infra/scripts/health-check-metric.sh >/dev/null 2>&1"
(crontab -l 2>/dev/null | grep -Fv "health-check-metric.sh" ; echo "$CRON_JOB") | crontab -

echo "========================================================"
echo "CloudDeploy AI host provisioning completed successfully!"
echo "Docker version: $(docker --version)"
echo "Docker Compose version: $(docker compose version)"
echo "CloudWatch Agent status: $(systemctl is-active amazon-cloudwatch-agent || echo 'pending-config')"
echo "========================================================"

