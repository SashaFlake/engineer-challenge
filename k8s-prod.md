```mermaid
graph TD
    Internet["🌍 Internet"]

    subgraph Cloud["Cloud Provider"]
        LB["☁️ Cloud Load Balancer\nExternal IP / DNS"]
    end

    subgraph K8s["Kubernetes Cluster"]
        subgraph IngressNS["namespace: ingress-nginx"]
            IngressCtrl["🔀 NGINX Ingress Controller\n───────────────\n• TLS termination cert-manager\n• Edge rate limiting per IP\n• Routing by host/path\n• limit-rps: 10 annotation"]
        end

        subgraph AppNS["namespace: engineer-challenge"]
            Ingress["📋 Ingress resource\n/auth/* → auth-service\n/metrics → internal only"]

            subgraph AuthCtx["Auth Bounded Context"]
                AuthSvc["🔗 auth-service ClusterIP"]
                AuthPods["⚙️ App Pods x2 Kotlin/gRPC\n───────────────\nCommand side write\nQuery side read\nBusiness rate limiting\nper user/token"]
            end

            subgraph Observability["Observability"]
                PromSvc["📊 Prometheus"]
                GrafanaSvc["📈 Grafana"]
            end

            subgraph Storage["Storage"]
                PG[("🗄️ Postgres StatefulSet")]
                Redis[("⚡ Redis\nRate limit counters")]
            end
        end

        HelmChart["📦 Helm Chart ./helm\nValues per environment"]
    end

    Internet --> LB
    LB --> IngressCtrl
    IngressCtrl --> Ingress
    Ingress --> AuthSvc
    AuthSvc --> AuthPods
    AuthPods --> PG
    AuthPods -->|"rate limit state"| Redis
    PromSvc -->|"scrape"| AuthPods
    GrafanaSvc -->|"datasource"| PromSvc
    HelmChart -.->|"deploys"| AppNS
    ```