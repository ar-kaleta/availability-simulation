# GKE Deployment Guide

## Prerequisites

- **Google Cloud SDK** (`gcloud`) installed and authenticated
- **kubectl** configured for your GKE cluster
- **Docker** (for local image builds)
- A **GKE cluster** with at least 2 nodes
- **Cloud SQL** (PostgreSQL) or an external PostgreSQL instance
- **Kafka** cluster (e.g., Confluent Cloud, Strimzi on GKE, or external)

## Architecture on GKE

```
┌─────────────────────────────────────────────────────┐
│                 GKE Cluster                         │
│                                                     │
│  ┌──────────────────────────────────────────────┐   │
│  │  Ingress (GCE)                               │   │
│  │  availability.example.com                    │   │
│  └────────────────────┬─────────────────────────┘   │
│                       │                             │
│  ┌────────────────────▼─────────────────────────┐   │
│  │  Service (ClusterIP, port 80)                │   │
│  └────────────────────┬─────────────────────────┘   │
│                       │                             │
│  ┌────────────────────▼─────────────────────────┐   │
│  │  Deployment (2-10 replicas, HPA)             │   │
│  │  availability-simulation                     │   │
│  │  - /actuator/health (liveness/readiness)     │   │
│  │  - /actuator/prometheus (metrics)            │   │
│  │  - /availability (REST API)                  │   │
│  └──────────────────────────────────────────────┘   │
│                                                     │
│  External Services:                                │
│  - Cloud SQL PostgreSQL (or external DB)           │
│  - Kafka cluster (Confluent Cloud / Strimzi)       │
└─────────────────────────────────────────────────────┘
```

## One-Time GCP Setup

### 1. Create a GCP Project

```bash
gcloud projects create availability-simulation --name="Availability Simulation"
gcloud config set project availability-simulation
```

### 2. Enable Required APIs

```bash
gcloud services enable container.googleapis.com \
  artifactregistry.googleapis.com \
  sqladmin.googleapis.com \
  compute.googleapis.com
```

### 3. Create a GKE Cluster

```bash
gcloud container clusters create availability-cluster \
  --zone us-central1-a \
  --num-nodes 2 \
  --machine-type e2-standard-2 \
  --enable-autoscaling \
  --min-nodes 1 \
  --max-nodes 5 \
  --enable-autoupgrade \
  --enable-autorepair
```

### 4. Create an Artifact Registry Repository

```bash
gcloud artifacts repositories create availability-simulation \
  --repository-format=docker \
  --location=us \
  --description="Docker images for availability-simulation"
```

### 5. Set Up PostgreSQL

**Option A: Cloud SQL (recommended for production)**

```bash
gcloud sql instances create postgres-db \
  --database-version=POSTGRES_15 \
  --tier=db-f1-micro \
  --region=us-central1 \
  --root-password=secret

gcloud sql databases create mydatabase --instance=postgres-db

# Create a non-root user
gcloud sql users create myuser --instance=postgres-db --password=secret
```

**Option B: External PostgreSQL** — Use your existing PostgreSQL instance and update the secret accordingly.

### 6. Set Up Kafka

**Option A: Confluent Cloud (managed)**
- Create a cluster at https://confluent.cloud
- Note the bootstrap server URL and credentials
- Update `k8s/configmap.yaml` with the bootstrap server

**Option B: Strimzi on GKE**

Install Strimzi via Helm (recommended) or by downloading the release ZIP.

**Helm method (simplest):**
```bash
# Add Strimzi Helm repository and install
helm repo add strimzi https://strimzi.io/charts/
helm repo update
helm install strimzi strimzi/helm3-strimzi-kafka-operator -n kafka --create-namespace

# Create a Kafka cluster (KRaft mode, no Zookeeper)
cat <<EOF | kubectl apply -f -
apiVersion: kafka.strimzi.io/v1beta2
kind: Kafka
metadata:
  name: my-kafka
  namespace: kafka
spec:
  kafka:
    replicas: 3
    listeners:
      - name: plain
        port: 9092
        type: internal
        tls: false
    config:
      offsets.topic.replication.factor: 3
      transaction.state.log.replication.factor: 3
      transaction.state.log.min.isr: 2
    storage:
      type: jbod
      volumes:
        - id: 0
          type: persistent-claim
          size: 100Gi
          deleteClaim: false
  entityOperator:
    topicOperator: {}
    userOperator: {}
EOF
```

**ZIP download method (alternative):**
```bash
# Download the latest Strimzi release
VERSION=$(curl -s https://api.github.com/repos/strimzi/strimzi-kafka-operator/releases/latest | grep '"tag_name"' | sed -E 's/.*"v?([^"]+)".*/\1/')
curl -L "https://github.com/strimzi/strimzi-kafka-operator/releases/download/${VERSION}/strimzi-${VERSION}.zip" -o strimzi-${VERSION}.zip
unzip strimzi-${VERSION}.zip
cd strimzi-${VERSION}

# Install the Cluster Operator
kubectl create namespace kafka
kubectl create -f install/cluster-operator/ -n kafka

# Create a Kafka cluster (same as above)
cat <<EOF | kubectl apply -f -
apiVersion: kafka.strimzi.io/v1beta2
kind: Kafka
metadata:
  name: my-kafka
  namespace: kafka
spec:
  kafka:
    replicas: 3
    listeners:
      - name: plain
        port: 9092
        type: internal
        tls: false
    config:
      offsets.topic.replication.factor: 3
      transaction.state.log.replication.factor: 3
      transaction.state.log.min.isr: 2
    storage:
      type: jbod
      volumes:
        - id: 0
          type: persistent-claim
          size: 100Gi
          deleteClaim: false
  entityOperator:
    topicOperator: {}
    userOperator: {}
EOF
```

After installation, the Kafka bootstrap server is available at:
```
my-kafka-kafka-bootstrap.kafka.svc.cluster.local:9092
```

## GitHub Secrets Setup

Add these secrets to your GitHub repository (`Settings → Secrets and variables → Actions`):

| Secret | Description | Example |
|--------|-------------|---------|
| `GKE_PROJECT_ID` | GCP project ID | `availability-simulation` |
| `GKE_SA_KEY` | Service account JSON key with GKE Admin + Artifact Writer roles | `{"type":"service_account",...}` |

### Create the Service Account

```bash
gcloud iam service-accounts create gke-deployer \
  --display-name="GKE Deployer for GitHub Actions"

gcloud projects add-iam-policy-binding availability-simulation \
  --member="serviceAccount:gke-deployer@availability-simulation.iam.gserviceaccount.com" \
  --role="roles/container.admin"

gcloud projects add-iam-policy-binding availability-simulation \
  --member="serviceAccount:gke-deployer@availability-simulation.iam.gserviceaccount.com" \
  --role="roles/artifactregistry.writer"

gcloud iam service-accounts keys create gke-sa-key.json \
  --iam-account=gke-deployer@availability-simulation.iam.gserviceaccount.com

# Copy the JSON key content to GitHub secret GKE_SA_KEY
cat gke-sa-key.json
```

## Configure Kubernetes Secrets

Update `k8s/secret.yaml` with your actual database credentials, or create the secret directly:

```bash
kubectl create secret generic availability-simulation-secret \
  --from-literal=SPRING_DATASOURCE_URL='jdbc:postgresql:///<your-db-host>:5432/mydatabase' \
  --from-literal=SPRING_DATASOURCE_USERNAME='myuser' \
  --from-literal=SPRING_DATASOURCE_PASSWORD='<your-password>' \
  -n default
```

For Cloud SQL, the URL format is:
```
jdbc:postgresql:///<CLOUD_SQL_CONNECTION_NAME>/mydatabase
```
with the Cloud SQL Socket Factory driver. For TCP access, use the private IP:
```
jdbc:postgresql://10.0.0.2:5432/mydatabase
```

## Configure Kafka Connection

Update `k8s/configmap.yaml` with your Kafka bootstrap server:

```bash
kubectl create configmap availability-simulation-config \
  --from-literal=SPRING_KAFKA_BOOTSTRAP_SERVERS='my-kafka-kafka-bootstrap.kafka.svc.cluster.local:9092' \
  --from-literal=SIMULATOR_INVENTORY_INTERVAL_MS='5000' \
  --from-literal=SIMULATOR_INVENTORY_BATCH_SIZE='5' \
  --from-literal=SIMULATOR_TRAFFIC_ENABLED='false' \
  -n default
```

## Deploy Manually

```bash
# Build and push the Docker image
docker build -t us-docker.pkg.dev/availability-simulation/availability-simulation/availability-simulation:latest .
docker push us-docker.pkg.dev/availability-simulation/availability-simulation/availability-simulation:latest

# Update the image reference in deployment.yaml
sed -i.bak "s|gcr.io/PROJECT_ID/availability-simulation:latest|us-docker.pkg.dev/availability-simulation/availability-simulation/availability-simulation:latest|g" k8s/deployment.yaml

# Apply all manifests
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/hpa.yaml
kubectl apply -f k8s/ingress.yaml

# Monitor rollout
kubectl rollout status deployment/availability-simulation
```

## CI/CD with GitHub Actions

Push to `main` branch to trigger the automated pipeline:

1. **Test** — Runs all unit and integration tests
2. **Build & Push** — Builds Docker image and pushes to Artifact Registry
3. **Deploy** — Applies Kubernetes manifests to GKE

## Monitoring

### View Logs

```bash
kubectl logs -l app=availability-simulation -f
```

### View Metrics

```bash
# Port-forward to the service
kubectl port-forward svc/availability-simulation 8080:80

# Access Prometheus metrics
curl http://localhost:8080/actuator/prometheus

# Access health check
curl http://localhost:8080/actuator/health
```

### GKE Dashboard

```bash
gcloud container clusters get-credentials availability-cluster --zone us-central1-a
kubectl get pods -l app=availability-simulation
kubectl top pods -l app=availability-simulation
```

## Scaling

The HPA is configured to scale between 2 and 10 replicas based on:
- **CPU utilization**: target 70%
- **Memory utilization**: target 80%

To adjust:

```bash
kubectl edit hpa availability-simulation-hpa
```

## Rollback

```bash
# Rollback to the previous revision
kubectl rollout undo deployment/availability-simulation

# Check rollout history
kubectl rollout history deployment/availability-simulation
```
