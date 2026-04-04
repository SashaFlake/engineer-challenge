```mermaid

graph TD
    Client["🖥️ Client\n(Browser / Postman / gRPC client)"]

    subgraph DockerCompose["Docker Compose Network: observability"]
        Nginx["🔀 Nginx\nReverse Proxy\n───────────────\n• Rate limiting per IP\n  10 rps general\n  1 rps /auth/login\n  1 rps /auth/register\n• TLS termination optional\n• Routing to app"]

        App["⚙️ App Kotlin\n:8080\n───────────────\n• gRPC / GraphQL API\n• Auth domain DDD/CQRS\n• JWT issue & validation\n• Business rate limiting\n  per user / per token"]

        Prometheus["📊 Prometheus\n:9090\nScrapes /metrics"]
        Grafana["📈 Grafana\n:3000\nDashboards"]
        Cadvisor["🐳 cAdvisor\n:8081\nContainer metrics"]
    end

    DB[("🗄️ DB\nPostgres/Redis")]

    Client -->|"HTTP/gRPC :80"| Nginx
    Nginx -->|"proxy_pass :8080"| App
    App -->|"queries"| DB
    Prometheus -->|"scrape"| App
    Prometheus -->|"scrape"| Cadvisor
    Grafana -->|"datasource"| Prometheus
   ```