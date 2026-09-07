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

# 5. Create deployment directory structure
APP_DIR="/opt/clouddeploy"
mkdir -p "${APP_DIR}/scripts"
chown -R ec2-user:ec2-user "$APP_DIR"

echo "========================================================"
echo "CloudDeploy AI host provisioning completed successfully!"
echo "Docker version: $(docker --version)"
echo "Docker Compose version: $(docker compose version)"
echo "========================================================"
