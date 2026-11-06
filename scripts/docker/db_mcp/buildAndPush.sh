#!/bin/bash
set -euo pipefail

PROFILE="ebsco"  # AWS CLI profile name — change if needed
REGION="us-west-2"
ACCOUNT_ID="732722833398"
IMAGE_NAME="db-mcp"
ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"

podman machine start 2>/dev/null || true  # start Podman VM; no-op if already running

aws ecr get-login-password --region "${REGION}" --profile "${PROFILE}" \
  | podman login --username AWS --password-stdin "${ECR_REGISTRY}"

podman build --platform linux/amd64 -t "${IMAGE_NAME}" .

podman tag "${IMAGE_NAME}:latest" "${ECR_REGISTRY}/${IMAGE_NAME}:latest"

podman push "${ECR_REGISTRY}/${IMAGE_NAME}:latest"
