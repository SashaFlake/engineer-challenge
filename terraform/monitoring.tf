# ─── kube-prometheus-stack ───────────────────────────────────────────────────

resource "helm_release" "monitoring" {
  depends_on = [
    null_resource.helm_deps,
    helm_release.ingress_nginx,
    helm_release.cert_manager,
  ]
  name             = "kube-prometheus-stack"
  repository       = "https://prometheus-community.github.io/helm-charts"
  chart            = "kube-prometheus-stack"
  version          = "58.7.2"
  namespace        = "monitoring"
  create_namespace = true

  set {
    name  = "grafana.ingress.enabled"
    value = tostring(local.grafana_ingress_enabled)
  }
  set {
    name  = "grafana.ingress.ingressClassName"
    value = "nginx"
  }
  dynamic "set" {
    for_each = local.grafana_ingress_enabled ? [1] : []
    content {
      name  = "grafana.ingress.hosts[0]"
      value = var.grafana_hostname
    }
  }

  # NodePort только если ингресса нет (прямой доступ по IP)
  set {
    name  = "grafana.service.type"
    value = local.grafana_ingress_enabled ? "ClusterIP" : "NodePort"
  }
  dynamic "set" {
    for_each = local.grafana_ingress_enabled ? [] : [1]
    content {
      name  = "grafana.service.nodePort"
      value = "32000"
    }
  }

  set_sensitive {
    name  = "grafana.adminPassword"
    value = var.grafana_admin_password
  }
  values = [
    file("${path.module}/../ops/grafana/provisioning/dashboards.yaml")
  ]
  set {
    name  = "prometheus.prometheusSpec.podMonitorSelectorNilUsesHelmValues"
    value = "false"
  }
  set {
    name  = "prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues"
    value = "false"
  }
}

# ─── Grafana dashboards ConfigMap ────────────────────────────────────────────
# Лейбл grafana_dashboard=1 обязателен — sidecar grafana-sc-dashboard
# отслеживает ConfigMap именно по этому лейблу.
resource "kubernetes_config_map" "grafana_dashboards" {
  depends_on = [helm_release.monitoring]

  metadata {
    name      = "grafana-dashboards"
    namespace = "monitoring"
    labels = {
      grafana_dashboard = "1"
    }
  }

  data = {
    "auth-service.json" = file("${path.module}/../ops/grafana/provisioning/dashboards/auth-service.json")
    "dragonfly.json"    = file("${path.module}/../ops/grafana/provisioning/dashboards/dragonfly.json")
    "nginx.json"        = file("${path.module}/../ops/grafana/provisioning/dashboards/nginx.json")
  }
}
