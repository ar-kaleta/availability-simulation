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

### 5. Set Up Cloud SQL for PostgreSQL

```bash
# Create the PostgreSQL instance (takes ~5 minutes)
gcloud sql instances create postgres-db \
  --database-version=POSTGRES_16 \
  --tier=db-f1-micro \
  --region=us-central1 \
  --root-password=changeme-strong-password \
  --no-assign-ip \
  --network=$(gcloud compute networks list --format="value(selfLink)" --filter="name=default")

# Create the database
gcloud sql databases create availability --instance=postgres-db

# Create a dedicated user
gcloud sql users set-password myuser --instance=postgres-db --password=changeme-user-password
```

The deployment uses the **Cloud SQL Auth Proxy** as a sidecar container, so the app connects via `127.0.0.1:5432`. The proxy authenticates with a dedicated service account key (not `--auto-iam-authn` alone), because GKE clusters created with restricted OAuth scopes cannot use Application Default Credentials for Cloud SQL.

**Create a service account for the Cloud SQL Auth Proxy:**

```bash
# Create a dedicated service account
gcloud iam service-accounts create cloud-sql-proxy-sa \
  --display-name="Cloud SQL Auth Proxy"

# Grant Cloud SQL client access
gcloud projects add-iam-policy-binding $(gcloud config get-value project) \
  --member="serviceAccount:cloud-sql-proxy-sa@$(gcloud config get-value project).iam.gserviceaccount.com" \
  --role="roles/cloudsql.client"

# Generate a key and create the Kubernetes secret
gcloud iam service-accounts keys create /tmp/cloud-sql-proxy-key.json \
  --iam-account=cloud-sql-proxy-sa@$(gcloud config get-value project).iam.gserviceaccount.com

kubectl create secret generic cloud-sql-proxy-credentials \
  --from-file=credentials.json=/tmp/cloud-sql-proxy-key.json \
  -n default

# Clean up the local key file
rm /tmp/cloud-sql-proxy-key.json
```

**Option B: External PostgreSQL** — Use your existing PostgreSQL instance and update the secret and Cloud SQL Auth Proxy args accordingly.

### 6. Set Up Kafka

**Option A: Confluent Cloud (managed)**
- Create a cluster at https://confluent.cloud
- Note the bootstrap server URL and credentials
- Update `k8s/configmap.yaml` with the bootstrap server

**Option B: Strimzi on GKE**

Install the Strimzi Cluster Operator via Helm (recommended) or by downloading the release ZIP, then create a Kafka cluster.

**Helm method (simplest):**

> **Got a previous failed install?** If you see `INSTALLATION FAILED: unable to continue with install: ... exists`, clean up **all** Strimzi resources first:
> ```bash
> helm uninstall strimzi -n kafka 2>/dev/null
> helm uninstall strimzi-cluster-operator -n kafka 2>/dev/null
>
> # Delete ALL Strimzi cluster-scoped resources by name (catches everything, including new roles)
> kubectl get clusterroles -o name | grep strimzi | xargs -r kubectl delete --ignore-not-found
> kubectl get clusterrolebindings -o name | grep strimzi | xargs -r kubectl delete --ignore-not-found
>
> # Delete all Strimzi namespace-scoped resources
> kubectl delete all,serviceaccount,secret,configmap,role,rolebinding -l strimzi.io/cl=operator -n kafka --ignore-not-found 2>/dev/null || \
>   kubectl delete all,serviceaccount,secret,configmap,role,rolebinding -l app=strimzi -n kafka --ignore-not-found
> ```
>
> Strimzi creates many cluster-scoped resources (ClusterRoles/ClusterRoleBindings) that live outside the `kafka` namespace. Helm refuses to install when it finds existing resources without its ownership labels, so **all** of them must be deleted before reinstalling. Using `grep strimzi | xargs kubectl delete` dynamically finds and removes every Strimzi resource by name, so no new ClusterRole slips through.

```bash
# Install the Strimzi Cluster Operator from the OCI registry
kubectl create namespace kafka --dry-run=client -o yaml | kubectl apply -f -
helm install strimzi-cluster-operator oci://quay.io/strimzi-helm/strimzi-kafka-operator \
  -n kafka \
  --set watchAllNamespaces=true

# Wait for the operator to be ready (CRDs are installed by the chart)
kubectl rollout status deployment/strimzi-cluster-operator -n kafka

# Create a Kafka NodePool (KRaft mode, combined controller + broker roles)
cat <<EOF | kubectl apply -f -
apiVersion: kafka.strimzi.io/v1
kind: KafkaNodePool
metadata:
  name: my-kafka-pool-a
  namespace: kafka
  labels:
    strimzi.io/cluster: my-kafka
spec:
  replicas: 3
  roles:
    - controller
    - broker
  storage:
    type: persistent-claim
    size: 100Gi
    deleteClaim: false
EOF

# Create the Kafka cluster with KRaft enabled
cat <<EOF | kubectl apply -f -
apiVersion: kafka.strimzi.io/v1
kind: Kafka
metadata:
  name: my-kafka
  namespace: kafka
  annotations:
    strimzi.io/kraft: enabled
    strimzi.io/node-pools: enabled
spec:
  kafka:
    version: 4.2.0
    metadataVersion: "4.2"
    listeners:
      - name: plain
        port: 9092
        type: internal
        tls: false
    config:
      offsets.topic.replication.factor: 3
      transaction.state.log.replication.factor: 3
      transaction.state.log.min.isr: 2
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

# Wait for the operator to be ready
kubectl rollout status deployment/strimzi-cluster-operator -n kafka

# Create a Kafka NodePool (KRaft mode, combined controller + broker roles)
cat <<EOF | kubectl apply -f -
apiVersion: kafka.strimzi.io/v1
kind: KafkaNodePool
metadata:
  name: my-kafka-pool-a
  namespace: kafka
  labels:
    strimzi.io/cluster: my-kafka
spec:
  replicas: 3
  roles:
    - controller
    - broker
  storage:
    type: persistent-claim
    size: 100Gi
    deleteClaim: false
EOF

# Create the Kafka cluster with KRaft enabled
cat <<EOF | kubectl apply -f -
apiVersion: kafka.strimzi.io/v1
kind: Kafka
metadata:
  name: my-kafka
  namespace: kafka
  annotations:
    strimzi.io/kraft: enabled
    strimzi.io/node-pools: enabled
spec:
  kafka:
    version: 4.2.0
    metadataVersion: "4.2"
    listeners:
      - name: plain
        port: 9092
        type: internal
        tls: false
    config:
      offsets.topic.replication.factor: 3
      transaction.state.log.replication.factor: 3
      transaction.state.log.min.isr: 2
  entityOperator:
    topicOperator: {}
    userOperator: {}
EOF
```

> **Note:** Strimzi 1.x uses KRaft mode only (ZooKeeper was removed in Apache Kafka 4.x). The `KafkaNodePool` resource defines nodes with both `controller` and `broker` roles, and the `Kafka` resource requires `strimzi.io/kraft: enabled` and `strimzi.io/node-pools: enabled` annotations.

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

With the Cloud SQL Auth Proxy sidecar, the app connects via `127.0.0.1:5432`. Create the secret with your Cloud SQL credentials:

```bash
kubectl create secret generic availability-simulation-secret \
  --from-literal=SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:5432/availability' \
  --from-literal=SPRING_DATASOURCE_USERNAME='myuser' \
  --from-literal=SPRING_DATASOURCE_PASSWORD='changeme-user-password' \
  -n default
```

Make sure to replace `changeme-user-password` with the password you set when creating the Cloud SQL user.

**For first deployment only:** Set `ddl-auto=update` so the app creates the `flight` table automatically:

```bash
kubectl set env deployment/availability-simulation SPRING_JPA_HIBERNATE_DDL_AUTO=update
```

After the schema is created, you can switch back to `validate` for production safety.

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

# For first deployment, set ddl-auto=update to create the schema automatically
kubectl set env deployment/availability-simulation SPRING_JPA_HIBERNATE_DDL_AUTO=update

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
