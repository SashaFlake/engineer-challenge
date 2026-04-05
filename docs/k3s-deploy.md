# Деплой в k3s

## Схема

```
git tag v1.x.x  →  GitHub Actions
                    ├── build Docker image
                    ├── push → ghcr.io/sashaflake/auth-service:<tag>
                    └── helm upgrade --install → k3s (адрес из KUBECONFIG_PROD)
                                                  └── NodePort :30081
```

## Требуемые GitHub Secrets

Настроить в **Settings → Environments → prod**:

| Secret | Описание |
|--------|----------|
| `KUBECONFIG_PROD` | kubeconfig для подключения к k3s (содержит адрес кластера) |
| `JWT_SECRET_PROD` | Секрет для подписи JWT токенов |
| `CORS_ALLOWED_HOSTS` | Разрешённые хосты для CORS, например `http://<IP>:30081` |

## Получить kubeconfig с k3s-ноды

```bash
# На сервере с k3s
sudo cat /etc/rancher/k3s/k3s.yaml
```

Заменить `server: https://127.0.0.1:6443` на `server: https://<PUBLIC_IP>:6443`,
вставить содержимое в GitHub Secret `KUBECONFIG_PROD`.

## Запуск деплоя

Деплой запускается **только по тегу**:

```bash
git tag v1.0.0
git push origin v1.0.0
```

## Ручной деплой

```bash
export KUBECONFIG=/path/to/k3s.yaml

helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

helm upgrade --install auth-service ./helm/auth-service \
  -f helm/auth-service/ip/values-ip.yaml \
  --set image.tag=sha-<commit_sha> \
  --set secrets.JWT_SECRET=<your_secret> \
  --set env.CORS_ALLOWED_HOSTS=http://<IP>:30081 \
  --namespace auth \
  --create-namespace \
  --wait
```

## Проверка

```bash
kubectl get pods -n auth
kubectl get svc -n auth
curl http://<IP>:30081/metrics
```
