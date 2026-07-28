data "aws_caller_identity" "current" {}

# ---------------------------------------------------------------------------
# ECR Pull-through Cache for registry.k8s.io
# ---------------------------------------------------------------------------
# This is a global, account-level resource — not tied to any EKS cluster.
# It allows kubelet to pull registry.k8s.io images via ECR, caching them
# automatically and reducing external dependency on the upstream registry.
#
# Once applied, images are accessed at:
#   <account>.dkr.ecr.<region>.amazonaws.com/ecr-pullthrough/k8s/<image-path>
#
# See: https://docs.aws.amazon.com/AmazonECR/latest/userguide/pull-through-cache.html
# ---------------------------------------------------------------------------

resource "aws_ecr_pull_through_cache_rule" "registry_k8s_io" {
  ecr_repository_prefix = "ecr-pullthrough/k8s"
  upstream_registry_url = "registry.k8s.io"
}
