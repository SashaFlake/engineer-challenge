# ─── Locals & Providers ──────────────────────────────────────────────────────
# Helm и Kubernetes провайдеры настроены на kubeconfig, который генерируется k3s.tf

locals {
  kubeconfig_path = "${path.module}/kubeconfig.yaml"

  # Если grafana_hostname выглядит как IP (цифры + точки) — ингресс отключаем.
  # Kubernetes не принимает IP-адрес в spec.rules[0].host — только DNS-имена.
  # В таком случае Grafana доступна по NodePort: http://<master_ip>:32000
  grafana_ingress_enabled = length(regexall("^[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+$", var.grafana_hostname)) == 0
}

provider "helm" {
  kubernetes {
    config_path = local.kubeconfig_path
  }
}

provider "kubernetes" {
  config_path = local.kubeconfig_path
}
