# Деплой в k3s

## Схема

```
git tag v1.x.x  →  GitHub Actions
                    ├── build Docker image
                    ├── push → ghcr.io/sashaflake/auth-service:<tag>
                    └── helm upgrade --install → k3s (213.171.30.170)
                                                  └── NodePort :30081
```

## Эндпоинты после деплоя

| Путь | URL |
|------|-----|
| GraphQL API | http://213.171.30.170:30081/graphql |
| GraphiQL | http://213.171.30.170:30081/graphiql |
| Metrics | http://213.171.30.170:30081/metrics |

## Требования: GitHub Secrets

Настроить в **Settings → Environments → prod**:

| Secret | Описание |
|--------|----------|
| `KUBECONFIG_PROD` | kubeconfig для подключения к k3s |
| `JWT_SECRET_PROD` | Секрет для подписи JWT токенов |

## Получить kubeconfig с k3s-ноды

```bash
# На сервере 213.171.30.170
sudo cat /etc/rancher/k3s/k3s.yaml
```

Заменить `server: https://127.0.0.1:6443` на `server: https://213.171.30.170:6443`, вставить в GitHub Secret `KUBECONFIG_PROD`.

## Запуск деплоя

Деплой запускается **только по тегу**:

```bash
git tag v1.0.0
git push origin v1.0.0
```

## Ручной деплой (если нужен)

```bash
export KUBECONFIG=/path/to/k3s.yaml

helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

helm upgrade --install auth-service ./helm/auth-service \
  -f helm/auth-service/ip/values-ip.yaml \
  --set image.tag=sha-<commit_sha> \
  --set secrets.JWT_SECRET=<your_secret> \
  --namespace auth \
  --create-namespace \
  --wait
```

## Проверка

```bash
kubectl get pods -n auth
kubectl get svc -n auth
curl http://213.171.30.170:30081/metrics
```
