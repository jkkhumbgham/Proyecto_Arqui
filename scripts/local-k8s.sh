#!/usr/bin/env bash
# local-k8s.sh — Despliega el stack completo en minikube para demo/validación local
# Uso: bash scripts/local-k8s.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "=== PUJ Learning Platform — Deploy local en minikube ==="
echo ""

# ── 1. Prerrequisitos ──────────────────────────────────────────────────────────
check_cmd() {
  if ! command -v "$1" &>/dev/null; then
    echo "✗ '$1' no encontrado. Instálalo antes de continuar."
    case "$1" in
      minikube) echo "  https://minikube.sigs.k8s.io/docs/start/" ;;
      kubectl)  echo "  https://kubernetes.io/docs/tasks/tools/" ;;
      docker)   echo "  https://docs.docker.com/get-docker/" ;;
    esac
    exit 1
  fi
}
check_cmd minikube
check_cmd kubectl
check_cmd docker

if [[ ! -f "$ROOT/jwt_private.pem" || ! -f "$ROOT/jwt_public.pem" ]]; then
  echo "✗ jwt_private.pem / jwt_public.pem no encontrados."
  echo "  Ejecuta primero: bash scripts/setup-env.sh"
  exit 1
fi

# ── 2. Iniciar minikube ────────────────────────────────────────────────────────
if minikube status --format '{{.Host}}' 2>/dev/null | grep -q "Running"; then
  echo "  Minikube ya está corriendo — omitiendo inicio"
else
  echo "Iniciando minikube (4 CPUs, 6 GB RAM, driver=docker)..."
  minikube start --cpus=4 --memory=6144 --driver=docker
fi

echo "Habilitando addon ingress (nginx)..."
minikube addons enable ingress 2>/dev/null || true

# ── 3. Construir imágenes dentro del daemon Docker de minikube ─────────────────
echo ""
echo "Configurando Docker para construir dentro de minikube..."
eval "$(minikube docker-env)"

echo "Construyendo imágenes (los Dockerfiles hacen el build internamente)..."
echo "  Primera ejecución puede tardar 5-10 min — Maven y .NET SDK corren dentro de Docker."
echo ""

for svc in user-service course-service assessment-service collaboration-service email-service; do
  echo "  → puj/$svc:local"
  docker build -q -t "puj/$svc:local" -f "$ROOT/services/$svc/Dockerfile" "$ROOT"
done

echo "  → puj/analytics-service:local"
docker build -q -t "puj/analytics-service:local" "$ROOT/services/analytics-service"

echo "  → puj/web-ui:local"
docker build -q -t "puj/web-ui:local" -f "$ROOT/frontend/web-ui/Dockerfile" "$ROOT"

echo ""
echo "  Imágenes construidas:"
docker images --format "  {{.Repository}}:{{.Tag}}" | grep "^  puj/"

# ── 4. Namespace y secreto con valores locales ─────────────────────────────────
echo ""
echo "Creando namespace y secretos..."
kubectl apply -f "$ROOT/infra/k8s/namespace.yaml"

kubectl create secret generic platform-secrets \
  --namespace puj-platform \
  --from-literal=DB_USER=puj_admin \
  --from-literal=DB_PASSWORD=puj_secret \
  --from-literal=REDIS_PASSWORD=redis_secret \
  --from-literal=RABBITMQ_USER=puj_rabbit \
  --from-literal=RABBITMQ_PASSWORD=rabbit_secret \
  --from-literal=JWT_PRIVATE_KEY="$(cat "$ROOT/jwt_private.pem")" \
  --from-literal=JWT_PUBLIC_KEY="$(cat "$ROOT/jwt_public.pem")" \
  --from-literal=AWS_ACCESS_KEY_ID=local-dummy \
  --from-literal=AWS_SECRET_ACCESS_KEY=local-dummy \
  --from-literal=SMTP_HOST=mailhog \
  --from-literal=SMTP_PORT=1025 \
  --from-literal=SMTP_USER="" \
  --from-literal=SMTP_PASSWORD="" \
  --dry-run=client -o yaml | kubectl apply -f -

# ── 5. Aplicar overlay local ──────────────────────────────────────────────────
echo "Aplicando manifiestos (overlay minikube)..."
kubectl apply -k "$ROOT/infra/overlays/local/"

# ── 6. Esperar a que los pods arranquen ───────────────────────────────────────
echo ""
echo "Esperando pods de infraestructura..."
kubectl rollout status deployment/postgres  -n puj-platform --timeout=120s
kubectl rollout status deployment/redis     -n puj-platform --timeout=60s
kubectl rollout status deployment/rabbitmq  -n puj-platform --timeout=60s
kubectl rollout status deployment/mailhog   -n puj-platform --timeout=30s

echo "Esperando servicios de negocio (WildFly tarda ~90s en arrancar)..."
for svc in user-service course-service assessment-service collaboration-service \
           analytics-service email-service web-ui; do
  kubectl rollout status deployment/$svc -n puj-platform --timeout=300s
done

# ── 7. Configurar /etc/hosts ──────────────────────────────────────────────────
MINIKUBE_IP=$(minikube ip)

if grep -q "platform.local" /etc/hosts 2>/dev/null; then
  echo ""
  echo "  Actualizando platform.local en /etc/hosts..."
  sudo sed -i "s/.*platform\.local/$MINIKUBE_IP  platform.local/" /etc/hosts
else
  echo ""
  echo "  Añadiendo platform.local a /etc/hosts (requiere sudo)..."
  echo "$MINIKUBE_IP  platform.local" | sudo tee -a /etc/hosts > /dev/null
fi

# ── 8. Resumen ────────────────────────────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════════════════"
echo "  Stack desplegado en minikube  (IP: $MINIKUBE_IP)"
echo ""
echo "  UI web:"
echo "    http://platform.local"
echo ""
echo "  MailHog (correos capturados) — en otra terminal:"
echo "    kubectl port-forward svc/mailhog 8025:8025 -n puj-platform"
echo "    → http://localhost:8025"
echo ""
echo "  RabbitMQ management — en otra terminal:"
echo "    kubectl port-forward svc/rabbitmq 15672:15672 -n puj-platform"
echo "    → http://localhost:15672  (puj_rabbit / rabbit_secret)"
echo ""
echo "  Estado de pods:"
echo "    kubectl get pods -n puj-platform"
echo ""
echo "  Para destruir todo:"
echo "    kubectl delete namespace puj-platform"
echo "    minikube stop"
echo "══════════════════════════════════════════════════════════"
