output "kubeconfig_path" {
  value       = "${path.module}/kubeconfig.yaml"
  description = "Путь к kubeconfig файлу для kubectl"
}

output "master_ip" {
  value       = var.master_ip
  description = "IP мастер-ноды"
}

output "worker_ip" {
  value       = var.worker_ip
  description = "IP воркер-ноды"
}

output "auth_service_url" {
  value       = "http://${var.master_ip}:30081/graphql"
  description = "URL GraphQL endpoint (NodePort)"
}

output "grafana_url" {
  value       = local.grafana_ingress_enabled ? "https://${var.grafana_hostname}" : "http://${var.master_ip}:32000"
  description = "URL Grafana дашборда"
}

output "kubectl_hint" {
  value       = "export KUBECONFIG=$(terraform output -raw kubeconfig_path)"
  description = "Команда для настройки kubectl"
}
