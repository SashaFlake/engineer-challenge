# ─── SSH ──────────────────────────────────────────────────────────────────────
variable "master_ip" {
  type        = string
  description = "Публичный IP мастер-ноды (Cloud.ru VM1)"
}

variable "worker_ip" {
  type        = string
  description = "Публичный IP воркер-ноды (Cloud.ru VM2)"
}

variable "master_private_ip" {
  type        = string
  description = "Приватный IP мастер-ноды (для k3s agent join)"
}

variable "ssh_user" {
  type        = string
  default     = "ubuntu"
  description = "SSH пользователь на VM"
}

variable "ssh_private_key_path" {
  type        = string
  default     = "~/.ssh/id_rsa"
  description = "Путь до приватного SSH ключа"
}

# ─── App ──────────────────────────────────────────────────────────────────────
variable "jwt_secret" {
  type        = string
  sensitive   = true
  description = "JWT signing secret. Передавать через TF_VAR_jwt_secret или CI/CD, не хранить в tfvars"
}

variable "app_image_tag" {
  type        = string
  default     = "latest"
  description = "Docker image tag для auth-service"
}

variable "app_image_repository" {
  type        = string
  default     = "ghcr.io/sashaflake/auth-service"
  description = "Docker image repository"
}

variable "app_hostname" {
  type        = string
  default     = "auth.example.com"
  description = "Hostname для Ingress (должен резолвиться на master_ip или worker_ip)"
}

variable "grafana_hostname" {
  type        = string
  default     = "grafana.example.com"
  description = "Hostname для Grafana Ingress"
}

variable "grafana_admin_password" {
  type        = string
  sensitive   = true
  description = "Пароль администратора Grafana. Передавать через TF_VAR_grafana_admin_password или CI/CD"
}

variable "letsencrypt_email" {
  type        = string
  description = "Email для Let's Encrypt уведомлений об истечении сертификата"
}

variable "cors_allowed_hosts" {
  type        = string
  default     = "http://localhost:3000"
  description = "CORS разрешённые origins для auth-service"
}

variable "namespace" {
  type        = string
  default     = "auth"
  description = "Kubernetes namespace для auth-service"
}
