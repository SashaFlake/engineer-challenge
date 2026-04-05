# Advanced Engineer Challenge — Auth Service

> Это решение инженерного челленджа. Оригинальное задание сохранено в [CHALLENGE.md](CHALLENGE.md).

## Стек

| Слой | Технология | Обоснование |
|---|---|---|
| Язык | Kotlin (JVM 21) | Выразительная система типов, value classes для DDD, нативный coroutines support (см. [ADR-001](ADR/ADR-001%20-%20%D0%92%D1%8B%D0%B1%D0%BE%D1%80%20%D1%81%D1%82%D0%B5%D0%BA%D0%B0%20%D0%B8%20%D0%BF%D0%BB%D0%B0%D1%82%D1%84%D0%BE%D1%80%D0%BC%D1%8B.md)) |
| Фреймворк | Ktor (async, lightweight) | Минимальный overhead, нет магии — все явно (см. [ADR-003](ADR/ADR-003-framework.md)) |
| Транспорт | GraphQL (graphql-kotlin) | Единый typed API, schema-first, introspection (см. [ADR-002](ADR/ADR-002-transport-protocol.md)) |
| Хранилище | DragonflyDB (Redis-compat) | In-memory, token storage, drop-in Redis замена (см. [ADR-004](ADR/ADR-004-persistence-layer.md)) |
| Reverse proxy | Nginx | TLS termination, rate-limiting на уровне сети |
| Observability | Prometheus + Grafana | Стандартные порты (9090 / 3000), pull-модель метрик |
| IaC | Docker Compose + Helm | Локальный стенд + K8s-чарты |

## Итерации разработки

Решение развивалось последовательно, чтобы не запускать инфраструктуру раньше, чем домен.

**Итерация 1 — In-memory адаптеры**

Порты `UserRepository`, `TokenRepository` реализованы через `ConcurrentHashMap`. Позволяет разрабатывать и тестировать доменную логику без Docker, доменные тесты запускаются без внешних зависимостей.

**Итерация 2 — DragonflyDB адаптеры**

Порты заменяются новыми адаптерами поверх DragonflyDB (Redis-протокол). Доменный модуль не меняется — подмена происходит исключительно в модуле `server/`. Инфраструктурные тесты используют Testcontainers для запуска реального DragonflyDB.

Такое разделение является следствием Ports & Adapters: домен не знает ничего о хранилище.

## Архитектура

### Request flow

```
Client → Nginx (80/443) → auth-backend (:8080) → DragonflyDB (:6379)
                 ↓
         nginx-exporter (:9113)
                 ↓
           Prometheus (:9090) ← dragonfly-exporter (:9121)
                 ↓                  ← cadvisor (:8081)
            Grafana (:3000)
```

```mermaid
graph LR
    Client -->|HTTP/HTTPS| Nginx
    Nginx -->|proxy_pass :8080| App[auth-backend]
    App -->|Redis protocol :6379| Dragonfly[(DragonflyDB)]
    App -->|/metrics| Prometheus
    Nginx -->|stub_status| NginxExporter[nginx-exporter]
    Dragonfly -->|redis metrics| DragonflyExporter[dragonfly-exporter]
    NginxExporter --> Prometheus
    DragonflyExporter --> Prometheus
    cAdvisor --> Prometheus
    Prometheus --> Grafana
```

### DDD — bounded context

Доменный модуль (`domain/`) содержит:
- **Aggregates**: `User`, `ResetToken` — инварианты хранятся внутри агрегатов
- **Value Objects**: `Email`, `Password` (bcrypt-хеш), `TokenId`
- **Domain Services**: `PasswordHasher`, `TokenGenerator`
- **Ports** (интерфейсы): `UserRepository`, `TokenRepository`, `RateLimiter`

Инфраструктурный модуль (`server/`) содержит **Adapters** — реализации портов поверх DragonflyDB.

### CQRS

| Command Side | Query Side |
|---|---|
| `RegisterUserCommand` | `GetUserByEmailQuery` |
| `LoginCommand` | `ValidateTokenQuery` |
| `RequestPasswordResetCommand` | — |
| `ConfirmPasswordResetCommand` | — |

Commands мутируют состояние через агрегаты и сохраняют через репозитории. Queries читают напрямую, без side-effects.

### Ключевые бизнес-инварианты

- Email уникален в рамках системы
- Пароль хранится только в виде bcrypt-хеша
- Reset-токен: одноразовый, TTL = 15 минут, повторная отправка блокируется rate limiter'ом
- Rate limiting: реализован через Nginx (`limit_req_zone` по IP, 20 req/s, burst 10); per-email лимиты через DragonflyDB — запланированы (см. [ADR-005](ADR/ADR-005-rate-limiting.md))

## Запуск

### Требования

- Docker ≥ 24
- Docker Compose ≥ 2.20

### Локально (Docker Compose)

```bash
git clone https://github.com/SashaFlake/engineer-challenge.git
cd engineer-challenge
docker compose up --build
```

| Сервис | URL |
|---|---|
| GraphQL API (через Nginx) | http://localhost/graphql |
| GraphQL Playground | http://localhost/graphiql |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin / admin) |
| cAdvisor | http://localhost:8081 |

### Kubernetes (Helm)

Helm-чарты находятся в директории [`helm/`](helm/). Чарт описывает `Deployment`, `Service`, `Ingress`, `ConfigMap`, `Secret`, `HPA`, `PodDisruptionBudget`, `NetworkPolicy`, `ServiceMonitor`, `livenessProbe`/`readinessProbe` — но не развёрнут в реальном кластере (см. ниже).

```bash
helm upgrade --install auth-service ./helm \
  --set app.jwtSecret="your-secret" \
  --namespace auth --create-namespace
```

## Тесты

Тесты покрывают **domain** (unit) и **infrastructure** (integration с реальным DragonflyDB через Testcontainers).

```bash
# Все тесты
./gradlew test

# Только доменные (без Docker)
./gradlew :domain:test

# Только инфраструктурные
./gradlew :server:test
```

Тестовые сценарии:
- Валидация инвариантов `Password`, `Email`, `ResetToken`
- Полный auth-флоу: register → login → reset-password
- Rate limiting: превышение лимита возвращает `429`

## Observability

После `docker compose up` доступны:

- **Prometheus** → http://localhost:9090 — метрики приложения, nginx, dragonfly, контейнеров
- **Grafana** → http://localhost:3000 — преднастроенные дашборды (provisioning в `ops/grafana/provisioning/`)

Метрики экспортируются на `/metrics` (формат Prometheus). Grafana подключается к Prometheus автоматически через provisioning.

## Architecture Decision Records

Ключевые архитектурные решения задокументированы в [`ADR/`](ADR/):

| # | Решение |
|---|---|
| [ADR-001](ADR/ADR-001%20-%20%D0%92%D1%8B%D0%B1%D0%BE%D1%80%20%D1%81%D1%82%D0%B5%D0%BA%D0%B0%20%D0%B8%20%D0%BF%D0%BB%D0%B0%D1%82%D1%84%D0%BE%D1%80%D0%BC%D1%8B.md) | Выбор стека и платформы (Kotlin + Ktor) |
| [ADR-002](ADR/ADR-002-transport-protocol.md) | Транспортный протокол (GraphQL vs REST vs gRPC) |
| [ADR-003](ADR/ADR-003-framework.md) | Выбор фреймворка (Ktor vs Spring Boot) |
| [ADR-004](ADR/ADR-004-persistence-layer.md) | Слой персистентности (DragonflyDB vs PostgreSQL) |
| [ADR-005](ADR/ADR-005-rate-limiting.md) | Rate limiting (Nginx — реализовано; DragonflyDB — запланировано) |

## Trade-offs

| Решение | Что выиграли | Что потеряли |
|---|---|---|
| DragonflyDB вместо PostgreSQL | Скорость, встроенный TTL для токенов | Нет ACID-транзакций между разными типами данных |
| GraphQL вместо REST | Типизированный контракт, introspection | Сложнее кэшировать, HTTP-кэш не применим напрямую |
| Ktor вместо Spring Boot | Минимальный overhead, явная конфигурация | Меньше готовых интеграций «из коробки» |
| Nginx для rate limiting (без DragonflyDB-слоя) | Edge-защита без кода в приложении | Нет защиты от брутфорса по email с разных IP (планируется добавить) |

## Что сейчас не готово к продакшну

Честное описание того, что ещё предстоит сделать до выхода в прод.

**Логи**

Сейчас логирования нет. Метрики есть, но нет никакого структуကрированного лога с correlation ID, нет контекကста опеကрации при ошибке. План: структуриကрованные логи чеကрез `logback` (JSON-фоကрмат) с полями `traceId`, `userId`, `operation`.

**Отпကравка email**

Сейчаကс нотификация пကри reset-password не реализована: токен генеကриကруетကся и сохကраняетကся, но не отпကравляетကся. План: поကрт `NotificationSender` уже выделен в домене; нужно ကреализовать адаптеကр чеကрез SMTP (пကростой ваကриант) или чеကрез децентကрализованный вызов чеကрез message broker.

**Клаကстеကр и production-деплой**

Helm-чаကрт напиကсан, но ကреального K8s-клаကстеကра нет — пကровеကрка проходила только чеကрез `helm template`. CI/CD еကсть (сбоကрка + теကсты), но GitOps-деплоя нет. План: поднять кластеကр (k3s или managed), пကоставить Argo CD, вынеကсти DragonflyDB в StatefulSet с PVC.

**Нагကрузочное теကстиကрование**

Неизвеကстно, ကсколько ကреальных RPS выдеကрживает ကсеကрвиကс и при каком latency. План: пကрогонять `k6` (или Gatling) по ကсценаကриям login/register/reset, зафикကсиကровать baseline и добавить в CI.

**Persistence без дуကрабильноကсти**

DragonflyDB ကработает в ကрежиме in-memory без апшного ကснапကшота/persistence. Пеကрезапуကск контейнеကра уничтожает вကсе данные: активные токены, учётные запиကси пользователей. План: включить RDB/AOF-пеကрကсиကстентноကсть в DragonflyDB либо вынеကсти на managed-ကсеကрвиကс ကс гаကрантированным persistence.

**Алеကрты**

Гကрафана еကсть, дашбоကрды еကсть, но алеကртов нет. План: добавить Alertmanager в docker-compose; наကстကроить минимальный набоကр: `error_rate > 1%` за 5 мин, `p99 latency > 500ms`, `dragonfly down`.

## Иကспользование ИИ

В пကроцеကсကсе ကработы иကспользовалကся Perplexity AI — в ကроли **engineering assistant**, а не decision-maker. Подကробнее — в [`.agents/perplexity.md`](.agents/perplexity.md).
