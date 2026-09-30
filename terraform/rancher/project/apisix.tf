# folio-apisix: Apache APISIX gateway as an alternative to folio-kong.
# All resources are gated on var.eureka && var.use_apisix.
# folio-apisix always uses the :latest tag from the folioci Docker Hub registry.
# Unlike folio-kong, no PostgreSQL database or Bitnami Helm chart is required.
# Ports: 9080 (proxy HTTP), 9443 (proxy HTTPS), 9180 (admin API).

resource "rancher2_secret" "apisix-credentials" {
  count        = var.eureka && var.use_apisix ? 1 : 0
  name         = "apisix-credentials"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.id
  data = {
    APISIX_ADMIN_KEY = base64encode("apisix-admin-secret")
  }
}

resource "kubernetes_deployment" "apisix" {
  count = var.eureka && var.use_apisix ? 1 : 0

  metadata {
    name      = "apisix-${var.rancher_project_name}"
    namespace = rancher2_namespace.this.id
    labels = {
      "app"                          = "apisix-${var.rancher_project_name}"
      "app.kubernetes.io/name"       = "apisix"
      "app.kubernetes.io/instance"   = "apisix-${var.rancher_project_name}"
      "app.kubernetes.io/managed-by" = "terraform"
    }
  }

  spec {
    replicas = 1

    selector {
      match_labels = {
        "app"                        = "apisix-${var.rancher_project_name}"
        "app.kubernetes.io/name"     = "apisix"
        "app.kubernetes.io/instance" = "apisix-${var.rancher_project_name}"
      }
    }

    template {
      metadata {
        labels = {
          "app"                        = "apisix-${var.rancher_project_name}"
          "app.kubernetes.io/name"     = "apisix"
          "app.kubernetes.io/instance" = "apisix-${var.rancher_project_name}"
        }
      }

      spec {
        container {
          name              = "apisix"
          image             = "folioci/folio-apisix:latest"
          image_pull_policy = "Always"

          port {
            name           = "proxy-http"
            container_port = 9080
            protocol       = "TCP"
          }

          port {
            name           = "proxy-https"
            container_port = 9443
            protocol       = "TCP"
          }

          port {
            name           = "admin-api"
            container_port = 9180
            protocol       = "TCP"
          }

          env {
            name = "APISIX_ADMIN_KEY"
            value_from {
              secret_key_ref {
                name = rancher2_secret.apisix-credentials[0].name
                key  = "APISIX_ADMIN_KEY"
              }
            }
          }

          resources {
            requests = {
              memory = "512Mi"
              cpu    = "250m"
            }
            limits = {
              memory = "1Gi"
              cpu    = "1"
            }
          }

          security_context {
            run_as_user                = 65532
            run_as_non_root            = true
            read_only_root_filesystem  = false
            allow_privilege_escalation = false
            capabilities {
              drop = ["ALL"]
            }
            seccomp_profile {
              type = "RuntimeDefault"
            }
          }
        }
      }
    }
  }

  lifecycle {
    ignore_changes = [metadata]
  }
}

# ClusterIP service for internal traffic to the APISIX proxy (port 9080).
# Named apisix-{namespace-id} to mirror the kong-{namespace-id} convention used
# in SIDECAR_FORWARD_UNKNOWN_REQUESTS_DESTINATION and eureka-edge OKAPI_HOST.
resource "kubernetes_service" "apisix_proxy" {
  count = var.eureka && var.use_apisix ? 1 : 0

  metadata {
    name      = "apisix-${rancher2_namespace.this.id}"
    namespace = rancher2_namespace.this.id
  }

  spec {
    selector = {
      "app"                        = "apisix-${var.rancher_project_name}"
      "app.kubernetes.io/name"     = "apisix"
      "app.kubernetes.io/instance" = "apisix-${var.rancher_project_name}"
    }

    port {
      name        = "http-proxy"
      port        = 9080
      target_port = 9080
      protocol    = "TCP"
    }

    type = "NodePort"
  }

  lifecycle {
    ignore_changes = [metadata]
  }
}

# ClusterIP service for internal traffic to the APISIX admin API (port 9180).
# Named apisix-admin-api-{namespace-id} to mirror kong-admin-api-{namespace-id}
# referenced by KONG_ADMIN_URL in eureka-common secret.
resource "kubernetes_service" "apisix_admin_api" {
  count = var.eureka && var.use_apisix ? 1 : 0

  metadata {
    name      = "apisix-admin-api-${rancher2_namespace.this.id}"
    namespace = rancher2_namespace.this.id
  }

  spec {
    selector = {
      "app"                        = "apisix-${var.rancher_project_name}"
      "app.kubernetes.io/name"     = "apisix"
      "app.kubernetes.io/instance" = "apisix-${var.rancher_project_name}"
    }

    port {
      port        = 80
      target_port = 9180
      protocol    = "TCP"
    }

    type = "ClusterIP"
  }

  lifecycle {
    ignore_changes = [metadata]
  }
}

# NodePort service to expose the APISIX admin API externally (for debugging/tooling).
resource "kubernetes_service" "apisix_admin_api_external" {
  count = var.eureka && var.use_apisix ? 1 : 0

  metadata {
    name      = "apisix-admin-api-external-${rancher2_namespace.this.id}"
    namespace = rancher2_namespace.this.id
  }

  spec {
    selector = {
      "app"                        = "apisix-${var.rancher_project_name}"
      "app.kubernetes.io/name"     = "apisix"
      "app.kubernetes.io/instance" = "apisix-${var.rancher_project_name}"
    }

    port {
      name        = "apisix-admin-api-external"
      port        = 80
      target_port = 9180
      protocol    = "TCP"
    }

    type = "NodePort"
  }

  lifecycle {
    ignore_changes = [metadata]
  }
}

# ALB ingress for the APISIX proxy, mirroring the kong helm_release ingress pattern.
# Exposes the primary apisix hostname plus the standard tenant-prefixed hostnames
# (ecs-, ecs2-, fs02-, fs03-) used by ECS and FS environments.
resource "kubernetes_ingress_v1" "apisix" {
  count = var.eureka && var.use_apisix ? 1 : 0

  metadata {
    name      = "apisix-${rancher2_namespace.this.id}"
    namespace = rancher2_namespace.this.id
    annotations = {
      "kubernetes.io/ingress.class"                = "alb"
      "alb.ingress.kubernetes.io/scheme"           = "internet-facing"
      "alb.ingress.kubernetes.io/group.name"       = local.group_name
      "alb.ingress.kubernetes.io/listen-ports"     = "[{\"HTTPS\":443}]"
      "alb.ingress.kubernetes.io/success-codes"    = "200-399"
      "alb.ingress.kubernetes.io/healthcheck-path" = "/apisix/status"
    }
  }

  spec {
    # Primary APISIX proxy hostname
    rule {
      host = local.apisix_url
      http {
        path {
          path      = "/*"
          path_type = "ImplementationSpecific"
          backend {
            service {
              name = "apisix-${rancher2_namespace.this.id}"
              port {
                number = 9080
              }
            }
          }
        }
      }
    }

    # ECS tenant prefix
    rule {
      host = join(".", [join("-", ["ecs", data.rancher2_cluster.this.name, var.rancher_project_name, "apisix"]), var.root_domain])
      http {
        path {
          path      = "/*"
          path_type = "ImplementationSpecific"
          backend {
            service {
              name = "apisix-${rancher2_namespace.this.id}"
              port {
                number = 9080
              }
            }
          }
        }
      }
    }

    # ECS2 tenant prefix
    rule {
      host = join(".", [join("-", ["ecs2", data.rancher2_cluster.this.name, var.rancher_project_name, "apisix"]), var.root_domain])
      http {
        path {
          path      = "/*"
          path_type = "ImplementationSpecific"
          backend {
            service {
              name = "apisix-${rancher2_namespace.this.id}"
              port {
                number = 9080
              }
            }
          }
        }
      }
    }

    # FS02 tenant prefix
    rule {
      host = join(".", [join("-", ["fs02", data.rancher2_cluster.this.name, var.rancher_project_name, "apisix"]), var.root_domain])
      http {
        path {
          path      = "/*"
          path_type = "ImplementationSpecific"
          backend {
            service {
              name = "apisix-${rancher2_namespace.this.id}"
              port {
                number = 9080
              }
            }
          }
        }
      }
    }

    # FS03 tenant prefix
    rule {
      host = join(".", [join("-", ["fs03", data.rancher2_cluster.this.name, var.rancher_project_name, "apisix"]), var.root_domain])
      http {
        path {
          path      = "/*"
          path_type = "ImplementationSpecific"
          backend {
            service {
              name = "apisix-${rancher2_namespace.this.id}"
              port {
                number = 9080
              }
            }
          }
        }
      }
    }
  }

  lifecycle {
    ignore_changes = [metadata]
  }
}

# ALB ingress for the APISIX admin API (external access for ops/debugging).
resource "kubernetes_ingress_v1" "apisix-admin-api" {
  count = var.eureka && var.use_apisix ? 1 : 0

  metadata {
    name      = "apisix-admin-api-${rancher2_namespace.this.id}"
    namespace = rancher2_namespace.this.id
    annotations = {
      "kubernetes.io/ingress.class"                = "alb"
      "alb.ingress.kubernetes.io/scheme"           = "internet-facing"
      "alb.ingress.kubernetes.io/group.name"       = local.group_name
      "alb.ingress.kubernetes.io/listen-ports"     = "[{\"HTTPS\":443}]"
      "alb.ingress.kubernetes.io/success-codes"    = "200-399"
      "alb.ingress.kubernetes.io/healthcheck-path" = "/apisix/status"
    }
  }

  spec {
    rule {
      host = join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "apisix-admin-api"]), var.root_domain])
      http {
        path {
          path      = "/*"
          path_type = "ImplementationSpecific"
          backend {
            service {
              name = "apisix-admin-api-external-${rancher2_namespace.this.id}"
              port {
                number = 80
              }
            }
          }
        }
      }
    }
  }

  lifecycle {
    ignore_changes = [metadata]
  }
}
