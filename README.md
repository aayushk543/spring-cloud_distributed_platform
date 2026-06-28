# Distributed Platform

A production-grade **distributed systems platform** built with Spring Boot 3, demonstrating microservices architecture, event-driven communication, and container orchestration.

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                   API Gateway (:8080)                │
│           JWT Auth • Rate Limiting • Routing         │
└────────────────────┬────────────────────────────────┘
                     │
    ┌────────────────┼────────────────────┐
    │                │                    │
┌───▼───┐     ┌─────▼─────┐       ┌─────▼──────┐
│ Auth  │     │ Job Queue │       │   Chat     │
│ :8081 │     │   :8082   │       │   :8083    │
└───────┘     └───────────┘       └────────────┘
                                        │
              ┌─────────────────────────┘
              │ Redis Pub/Sub + WebSocket
              │
┌─────────────▼──────────────────────────────────┐
│              Saga Pattern (Kafka)                │
│                                                  │
│  ┌──────────┐  ┌──────────┐  ┌─────────────┐  │
│  │  Order   │→ │ Inventory│→ │  Payment    │  │
│  │  :8084   │  │  :8086   │  │   :8085     │  │
│  └──────────┘  └──────────┘  └─────────────┘  │
│                      ↓                         │
│              ┌──────────────┐                  │
│              │ Notification │                  │
│              │    :8087     │                  │
│              └──────────────┘                  │
└─────────────────────────────────────────────────┘

┌───────────────── Infrastructure ─────────────────┐
│  PostgreSQL │ Redis │ Kafka/Zookeeper │ ELK Stack │
│  Eureka     │ Docker Compose │ Kubernetes │ k6    │
└──────────────────────────────────────────────────┘
```

## Services

| Service | Port | Stack | Description |
|---------|------|-------|-------------|
| **Discovery Server** | 8761 | Eureka | Service registry & discovery |
| **API Gateway** | 8080 | Spring Cloud Gateway | JWT validation, rate limiting (Redis), routing |
| **Auth Service** | 8081 | Spring Security, JWT | Registration, login, token validation |
| **Job Queue Service** | 8082 | Redis, Kafka | Priority queue, retry with backoff, DLQ |
| **Chat Service** | 8083 | WebSocket, Redis Pub/Sub, Kafka | Real-time chat for 10k+ users |
| **Order Service** | 8084 | Kafka, Saga | Saga orchestrator for checkout flow |
| **Payment Service** | 8085 | Kafka | Saga participant, simulated processing |
| **Inventory Service** | 8086 | Kafka, JPA @Version | Saga participant, optimistic locking |
| **Notification Service** | 8087 | Kafka | Event-driven notification consumer |

## Key Concepts Demonstrated

### 1. Job Queue with Redis + Kafka
- **Priority scheduling** via Redis Sorted Sets (ZADD/ZPOPMIN)
- **Exponential backoff retry** with configurable max retries
- **Dead Letter Queue** for permanently failed jobs
- **Kafka event streaming** for job lifecycle events

### 2. API Gateway + Auth
- **JWT authentication** with centralized validation at gateway
- **Redis-based rate limiting** (token bucket via Spring Cloud Gateway)
- **Service discovery** via Eureka for dynamic routing
- **CORS** and **security** configuration

### 3. Real-Time Chat (10k+ Concurrent Users)
- **WebSocket/STOMP** for client connections
- **Redis Pub/Sub** for cross-instance message broadcast
- **Kafka persistence** for chat history
- **Presence tracking** via Redis SET per room
- K8s HPA scales chat pods to 10 replicas under load

### 4. Log Aggregation (ELK)
- Services → Kafka topic `application-logs`
- Logstash consumes from Kafka → indexes into Elasticsearch
- Kibana dashboards for visualization
- Supports **millions of logs/day**

### 5. Event-Driven Checkout (Saga Pattern)
- **Orchestrator-based Saga** in Order Service
- Flow: `Order Created → Inventory Reserve → Payment Process → Notification`
- **Compensation**: Payment fails → Inventory Release → Notification
- **Idempotency keys** prevent duplicate orders
- **Saga audit log** for debugging/recovery

##  Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 17+ (for local dev)
- k6 (for load testing)

### Run Everything
```bash
# Start all services
docker-compose up --build -d

# Verify services are registered
open http://localhost:8761   # Eureka Dashboard

# Open the Web UI
open web-ui/index.html

# Open Kibana for logs
open http://localhost:5601
```

### Run Load Tests
```bash
# Test rate limiting
k6 run load-tests/gateway-rate-limit-test.js

# Test job queue throughput
k6 run load-tests/job-queue-test.js

# Test checkout saga
k6 run load-tests/checkout-saga-test.js

# Test WebSocket chat
k6 run load-tests/chat-websocket-test.js
```

##  Kubernetes Deployment

```bash
# Create namespace and config
kubectl apply -f k8s/namespace.yml
kubectl apply -f k8s/configmap.yml
kubectl apply -f k8s/secrets.yml

# Deploy infrastructure
kubectl apply -f k8s/infrastructure/

# Deploy services
kubectl apply -f k8s/services/

# Configure networking
kubectl apply -f k8s/ingress.yml
kubectl apply -f k8s/network-policy.yml
kubectl apply -f k8s/hpa.yml
```

### K8s Features
- **HPA**: Chat service auto-scales 2→10 pods at 70% CPU
- **Network Policies**: Deny-all default, allow gateway ingress + intra-namespace
- **StatefulSets**: Postgres, Kafka, Elasticsearch with PVCs
- **Resource Limits**: CPU/memory requests and limits on all pods
- **Health Checks**: Readiness + liveness probes on all services

##  Project Structure

```
distributed-platform/
├── discovery-server/       # Eureka service registry
├── api-gateway/            # Spring Cloud Gateway + JWT + rate limiting
├── auth-service/           # Authentication service
├── job-queue-service/      # Priority job queue (Redis + Kafka)
├── chat-service/           # Real-time chat (WebSocket + Redis Pub/Sub)
├── order-service/          # Saga orchestrator
├── payment-service/        # Saga participant
├── inventory-service/      # Saga participant with optimistic locking
├── notification-service/   # Event-driven notifications
├── init-db/                # PostgreSQL database init scripts
├── elk/                    # Logstash pipeline configuration
├── k8s/                    # Kubernetes manifests
│   ├── infrastructure/     # Redis, Postgres, Kafka, ES, Zookeeper
│   ├── services/           # All microservice deployments
│   ├── hpa.yml             # Horizontal Pod Autoscaler
│   ├── ingress.yml         # Nginx Ingress
│   └── network-policy.yml  # Network policies
├── load-tests/             # k6 performance test scripts
├── web-ui/                 # Single-page dashboard
├── docker-compose.yml      # Full stack compose
└── README.md
```

## 🧪Testing

| Test Suite | What It Validates | Target |
|------------|-------------------|--------|
| `gateway-rate-limit-test.js` | Rate limiting kicks in at threshold | p95 < 500ms |
| `job-queue-test.js` | Job throughput with priorities & retries | 500+ jobs, p95 < 1s |
| `checkout-saga-test.js` | End-to-end saga with compensation | >50% completion rate |
| `chat-websocket-test.js` | WebSocket connections & message delivery | 500 VUs, p95 < 500ms |

## Tech Stack

- **Language**: Java 17
- **Framework**: Spring Boot 3.2, Spring Cloud 2023.0
- **Messaging**: Apache Kafka (Confluent 7.5)
- **Cache/PubSub**: Redis 7
- **Database**: PostgreSQL 16
- **Service Discovery**: Netflix Eureka
- **Gateway**: Spring Cloud Gateway
- **Logging**: ELK Stack (Elasticsearch 8.11, Logstash, Kibana)
- **Containers**: Docker, Kubernetes
- **Load Testing**: k6
- **Frontend**: Vanilla HTML/CSS/JS + SockJS/STOMP
