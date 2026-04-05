# ─── Namespace ───────────────────────────────────────────────────────────────────────────

resource "kubernetes_namespace" "auth" {
  depends_on = [null_resource.helm_deps]

  metadata {
    name = var.namespace
    labels = {
      "app.kubernetes.io/managed-by" = "terraform"
    }
  }
}

# ─── auth-service ───────────────────────────────────────────────────────────────────
# Без домена: ingress отключён, сервис доступен по NodePort 30081.
# Базовые значения — в helm/auth-service/prod/values-prod.yaml.
# Секреты и динамические переменные передаются через set/set_sensitive.

resource "helm_release" "auth_service" {
  depends_on = [
    kubernetes_namespace.auth,
    helm_release.ingress_nginx,
    null_resource.helm_deps,
  ]
  name             = "auth-service"
  chart            = "${path.module}/../helm/auth-service"
  namespace        = var.namespace
  create_namespace = false

  values = [
    file("${path.module}/../helm/auth-service/prod/values-prod.yaml")
  ]

  set {
    name  = "image.repository"
    value = var.app_image_repository
  }
  set {
    name  = "image.tag"
    value = var.app_image_tag
  }
  set_sensitive {
    name  = "secrets.JWT_SECRET"
    value = var.jwt_secret
  }
  set {
    name  = "env.CORS_ALLOWED_HOSTS"
    value = var.cors_allowed_hosts
  }
  set {
    name  = "env.DRAGONFLY_HOST"
    value = "auth-service-dragonfly-master.${var.namespace}.svc.cluster.local"
  }
}
