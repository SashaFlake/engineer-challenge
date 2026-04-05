# Dragonfly — отдельный деплой

Dragonfly деплоится один раз вручную и не пересоздаётся при каждом релизе.

## Установка

```bash
# Подключиться к кластеру
export KUBECONFIG=/path/to/k3s.yaml

# Создать namespace если не существует
kubectl create namespace auth --dry-run=client -o yaml | kubectl apply -f -

# Задеплоить Dragonfly через OCI
helm upgrade --install dragonfly \
  oci://registry-1.docker.io/bitnamicharts/redis \
  --set architecture=standalone \
  --set auth.enabled=false \
  --set master.persistence.enabled=false \
  --set replica.replicaCount=0 \
  --namespace auth \
  --wait
```

## Проверка

```bash
kubectl get pods -n auth
# dragonfly-master-0   1/1   Running

kubectl exec -n auth -it dragonfly-master-0 -- redis-cli ping
# PONG
```

## Hostname для auth-service

После установки Dragonfly доступен внутри кластера по адресу:
```
dragonfly-master.auth.svc.cluster.local:6379
```
Это значение уже прописано в `helm/auth-service/ip/values-ip.yaml`.
