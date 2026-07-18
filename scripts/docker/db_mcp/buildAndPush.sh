#!/bin/bash
set -euo pipefail

PROFILE="ebsco"  # AWS CLI profile name — change if needed
REGION="us-west-2"
ACCOUNT_ID="732722833398"
IMAGE_NAME="db-mcp"
ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"

docker machine start  # Podman is used here; if you use Docker, remove this line

aws ecr get-login-password --region "${REGION}" --profile "${PROFILE}" \
  | docker login --username AWS --password-stdin "${ECR_REGISTRY}"

docker build --platform linux/amd64 -t "${IMAGE_NAME}" .

docker tag "${IMAGE_NAME}:latest" "${ECR_REGISTRY}/${IMAGE_NAME}:latest"

docker push "${ECR_REGISTRY}/${IMAGE_NAME}:latest"
