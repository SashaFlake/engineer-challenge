# Terraform: k3s на Cloud.ru + Helm deploy

Разворачивает полный стек на двух VM Cloud.ru:
- **VM1 (master)** — k3s server: control plane + workloads
- **VM2 (worker)** — k3s agent: дополнительные workloads

## Что деплоится

| Компонент | Chart/Source | Namespace |
|---|---|---|
| k3s cluster | get.k3s.io | — |
| ingress-nginx | kubernetes/ingress-nginx 4.10.1 | ingress-nginx |
| cert-manager | jetstack/cert-manager v1.14.5 | cert-manager |
| Let’s Encrypt ClusterIssuer | letsencrypt.tf | cert-manager |
| kube-prometheus-stack | prometheus-community 58.7.2 | monitoring |
| grafana-dashboards ConfigMap | kubernetes_config_map | monitoring |
| auth-service | helm/auth-service (local) | auth |
| DragonflyDB | bitnami/redis alias (subchart) | auth |

## Требования

- Terraform ≥ 1.9
- Helm ≥ 3.14 (используется локально через SSH-туннель к kubeconfig)
- SSH-доступ к обеим VM (`~/.ssh/cloudruce`)
- Открытые порты на Cloud.ru VM:
  - `22` — SSH
  - `6443` — k3s API (между VM)
  - `30080`, `30443` — NodePort для ingress-nginx (публично)
  - `30081` — NodePort для auth-service (прямой доступ)
  - `32000` — NodePort для Grafana
  - `10250` — kubelet (между VM)

## Быстрый старт

```bash
# 1. Клонируй репозиторий
git clone https://github.com/SashaFlake/engineer-challenge
cd engineer-challenge/terraform

# 2. Настрой переменные (см. terraform.tfvars.example)
cp terraform.tfvars.example terraform.tfvars
# Заполни IP адреса VM и путь к SSH-ключу

# 3. Секреты — передавать через env, не хранить в tfvars
export TF_VAR_jwt_secret=$(openssl rand -base64 32)
export TF_VAR_grafana_admin_password="your-password"
export TF_VAR_letsencrypt_email="your@email.com"

# 4. Init + deploy
terraform init
terraform apply -auto-approve
```

## Структура файлов

| Файл | Что делает |
|---|---|
| `k3s.tf` | Установка k3s на master/worker по SSH, загрузка бинарника, копирование kubeconfig |
| `providers.tf` | Настройка helm/kubernetes провайдеров через SSH-туннель к kubeconfig |
| `versions.tf` | Версии провайдеров и Terraform; remote backend закомментирован |
| `variables.tf` | Все переменные: SSH-ключи, IP-адреса, секреты (sensitive) |
| `helm.tf` | SSH-туннель для helm/kubernetes провайдера |
| `helm-deps.tf` | `null_resource` для `helm dependency update` перед деплоем |
| `ingress-nginx.tf` | Helm-релиз ingress-nginx, NodePort 30080/30443 |
| `cert-manager.tf` | Helm-релиз cert-manager |
| `letsencrypt.tf` | ClusterIssuer для Let’s Encrypt (staging + prod) |
| `monitoring.tf` | Helm-релиз kube-prometheus-stack + `kubernetes_config_map` grafana-dashboards |
| `auth-service.tf` | `kubernetes_namespace` auth + Helm-релиз auth-service |
| `outputs.tf` | Выводы: URL Grafana, URL auth-service |
| `terraform.tfvars.example` | Пример значений (IP, пути, несенситивные переменные) |

## Порядок создания ресурсов

```
VM1: k3s server install
  └── fetch kubeconfig + node-token
        └── copy key + token to VM2
              └── VM2: k3s agent join
                    └── helm dependency update
                          ├── ingress-nginx (NodePort 30080/30443)
                          ├── cert-manager + ClusterIssuer letsencrypt
                          ├── kube-prometheus-stack + grafana-dashboards ConfigMap
                          └── kubernetes_namespace auth
                                └── auth-service + DragonflyDB subchart
```

## Секреты

Сенситивные переменные **не хранятся** в `terraform.tfvars` — передаются через `TF_VAR_*`:

| Переменная | Описание |
|---|---|
| `TF_VAR_jwt_secret` | JWT signing secret, попадает в Kubernetes Secret |
| `TF_VAR_grafana_admin_password` | Пароль admin для Grafana |
| `TF_VAR_letsencrypt_email` | Email для уведомлений Let’s Encrypt |

`JWT_SECRET` передаётся через `set_sensitive` в Helm release — в state-файле значение зашифровано.

Для production используй remote backend (S3-совместимое хранилище Cloud.ru) — настройки закомментированы в `versions.tf`.

## Доступ после деплоя

| Сервис | URL |
|---|---|
| GraphQL API | http://213.171.30.170:30080/graphql |
| GraphQL Playground | http://213.171.30.170:30080/graphiql |
| auth-service напрямую | http://213.171.30.170:30081/graphql |
| Grafana | http://213.171.30.170:32000 (admin / `TF_VAR_grafana_admin_password`) |

```bash
# Проверить состояние кластера
ssh -i ~/.ssh/cloudruce user1@213.171.30.170 \
  "sudo k3s kubectl get pods -A"

# Пароль Grafana из secret
ssh -i ~/.ssh/cloudruce user1@213.171.30.170 \
  "sudo k3s kubectl get secret -n monitoring kube-prometheus-stack-grafana \
   -o jsonpath='{.data.admin-password}' | base64 -d && echo"
```

## Импорт существующих ресурсов

Если ресурсы были созданы вручную до `terraform apply`:

```bash
# namespace auth уже существует
terraform import kubernetes_namespace.auth auth

# ConfigMap grafana-dashboards уже создан вручную
terraform import kubernetes_config_map.grafana_dashboards monitoring/grafana-dashboards
```

## Observability

После деплоя Prometheus автоматически собирает метрики через ServiceMonitor:
- **auth-service** — JVM, HTTP, custom метрики (путь `/metrics`, порт 8080)
- **DragonflyDB** — redis_exporter sidecar (порт 9121)

Дашборды в Grafana подключаются через ConfigMap `grafana-dashboards` с лейблом `grafana_dashboard=1` —
sidecar `grafana-sc-dashboard` подхватывает их без перезапуска Grafana.

Дашборды JSON находятся в `ops/grafana/provisioning/dashboards/`.
