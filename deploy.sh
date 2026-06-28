#!/bin/bash
# =====================================================
# Distributed Platform - Kubernetes Deployment Script
# =====================================================
set -e

NAMESPACE="distributed-platform"
echo "🚀 Deploying Distributed Platform to Kubernetes..."

# Step 1: Namespace & Config
echo "📦 Creating namespace and configuration..."
kubectl apply -f k8s/namespace.yml
kubectl apply -f k8s/configmap.yml
kubectl apply -f k8s/secrets.yml

# Step 2: Infrastructure
echo "🔧 Deploying infrastructure (Postgres, Kafka, Redis, Elasticsearch)..."
kubectl apply -f k8s/infrastructure/zookeeper.yml
kubectl apply -f k8s/infrastructure/redis.yml
kubectl apply -f k8s/infrastructure/postgres.yml

echo "⏳ Waiting for Zookeeper to be ready..."
kubectl -n $NAMESPACE wait --for=condition=ready pod -l app=zookeeper --timeout=120s

kubectl apply -f k8s/infrastructure/kafka.yml
kubectl apply -f k8s/infrastructure/elasticsearch.yml

echo "⏳ Waiting for infrastructure to be ready..."
kubectl -n $NAMESPACE wait --for=condition=ready pod -l app=kafka --timeout=120s
kubectl -n $NAMESPACE wait --for=condition=ready pod -l app=redis --timeout=60s

# Step 3: Build and push Docker images (for local/minikube)
echo "🐳 Building Docker images..."
SERVICES="discovery-server api-gateway auth-service job-queue-service chat-service order-service payment-service inventory-service notification-service"

for svc in $SERVICES; do
  echo "  Building $svc..."
  docker build -t distributed-platform/$svc:latest ./$svc
done

# Step 4: Deploy microservices
echo "🎯 Deploying microservices..."
kubectl apply -f k8s/services/discovery-server.yml

echo "⏳ Waiting for Discovery Server..."
kubectl -n $NAMESPACE wait --for=condition=ready pod -l app=discovery-server --timeout=180s

kubectl apply -f k8s/services/auth-service.yml
kubectl apply -f k8s/services/api-gateway.yml
kubectl apply -f k8s/services/job-queue-service.yml
kubectl apply -f k8s/services/chat-service.yml
kubectl apply -f k8s/services/saga-services.yml

# Step 5: Networking
echo "🌐 Configuring networking..."
kubectl apply -f k8s/ingress.yml
kubectl apply -f k8s/network-policy.yml
kubectl apply -f k8s/hpa.yml

# Step 6: Verify
echo ""
echo "✅ Deployment complete! Checking status..."
echo ""
kubectl get pods -n $NAMESPACE
echo ""
kubectl get svc -n $NAMESPACE
echo ""
echo "📊 Dashboard URLs:"
echo "  Eureka:        http://localhost:8761"
echo "  API Gateway:   http://localhost:8080"
echo "  Kibana:        http://localhost:5601"
echo ""
echo "To port-forward the gateway:"
echo "  kubectl -n $NAMESPACE port-forward svc/api-gateway 8080:8080"
