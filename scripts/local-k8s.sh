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
  echo "Iniciando minikube (4 CPUs, 4 GB RAM, driver=docker)..."
  minikube start --cpus=4 --memory=4096 --driver=docker
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
  docker build -t "puj/$svc:local" -f "$ROOT/services/$svc/Dockerfile" "$ROOT"
done

echo "  → puj/analytics-service:local"
docker build -t "puj/analytics-service:local" "$ROOT/services/analytics-service"

echo "  → puj/web-ui:local"
docker build -t "puj/web-ui:local" -f "$ROOT/frontend/web-ui/Dockerfile" "$ROOT"

echo ""
echo "  Imágenes construidas:"
docker images --format "  {{.Repository}}:{{.Tag}}" | grep "puj/" || true

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
echo "Esperando pods de infraestructura (postgres, redis, rabbitmq, mailhog)..."
for infra in postgres redis rabbitmq mailhog; do
  kubectl rollout status deployment/$infra -n puj-platform --timeout=180s || \
    echo "  ⚠ $infra tardó más de 3 min — continúa en background"
done

echo ""
echo "Esperando servicios de negocio (WildFly tarda ~90s, .NET ~30s)..."
echo "  Progreso cada 15s — Ctrl+C para saltar la espera y seguir:"
echo ""

SERVICES="user-service course-service assessment-service collaboration-service analytics-service email-service web-ui"
MAX_WAIT=600
INTERVAL=15
elapsed=0

while [ $elapsed -lt $MAX_WAIT ]; do
  all_ready=true
  line="  "
  for svc in $SERVICES; do
    ready=$(kubectl get deployment/$svc -n puj-platform \
            -o jsonpath='{.status.readyReplicas}' 2>/dev/null)
    ready=${ready:-0}
    desired=$(kubectl get deployment/$svc -n puj-platform \
              -o jsonpath='{.spec.replicas}' 2>/dev/null)
    desired=${desired:-1}
    if [ "$ready" -ge "$desired" ] 2>/dev/null && [ "$ready" != "0" ]; then
      line="$line ✓$svc"
    else
      line="$line ⏳$svc($ready/$desired)"
      all_ready=false
    fi
  done
  echo "$line"

  if $all_ready; then
    echo ""
    echo "  ✅ Todos los servicios listos!"
    break
  fi

  sleep $INTERVAL
  elapsed=$((elapsed + INTERVAL))
done

if [ $elapsed -ge $MAX_WAIT ]; then
  echo ""
  echo "  ⚠ 10 min de espera agotados — algunos pods siguen iniciando en background."
  echo "  Revisa con: kubectl get pods -n puj-platform"
fi

# ── 7. Configurar /etc/hosts (opcional — no falla si no hay sudo) ─────────────
MINIKUBE_IP=$(minikube ip)
HOSTS_OK=false

if grep -q "platform.local" /etc/hosts 2>/dev/null; then
  if sudo sed -i "s/.*platform\.local/$MINIKUBE_IP  platform.local/" /etc/hosts 2>/dev/null; then
    HOSTS_OK=true
  fi
else
  if echo "$MINIKUBE_IP  platform.local" | sudo tee -a /etc/hosts > /dev/null 2>&1; then
    HOSTS_OK=true
  fi
fi

# ── 8. Seed de datos de demostración ─────────────────────────────────────────
echo ""
echo "Cargando datos de demostración..."

# Port-forwards temporales para el seed
kubectl port-forward svc/user-service          8081:8080 -n puj-platform &>/dev/null &
PF1=$!
kubectl port-forward svc/course-service        8082:8080 -n puj-platform &>/dev/null &
PF2=$!
kubectl port-forward svc/assessment-service    8083:8080 -n puj-platform &>/dev/null &
PF3=$!
kubectl port-forward svc/collaboration-service 8084:8080 -n puj-platform &>/dev/null &
PF4=$!
kubectl port-forward svc/analytics-service     8085:8080 -n puj-platform &>/dev/null &
PF5=$!

# Esperar hasta que todos los servicios respondan (máx 90 s c/u).
# analytics-service usa WaitUntilStarted=true → su /health solo responde
# cuando MassTransit ya está conectado a RabbitMQ. Por eso lo esperamos
# ANTES de correr el seed: garantiza que los eventos USER_REGISTERED y
# COURSE_ENROLLED lleguen al consumer desde el primer momento.
echo "  Esperando que los servicios estén listos para el seed..."
wait_http() {
  local port=$1 path=$2 label=$3
  for i in $(seq 1 30); do
    if curl -sf "http://localhost:$port$path" > /dev/null 2>&1; then
      echo "  ✓ $label (puerto $port) listo"
      return 0
    fi
    sleep 3
  done
  echo "  ⚠ $label (puerto $port) no respondió tras 90 s"
}

wait_http 8081 "/api/v1/health" "user-service"
wait_http 8082 "/api/v1/health" "course-service"
wait_http 8083 "/api/v1/health" "assessment-service"
wait_http 8084 "/api/v1/health" "collaboration-service"
wait_http 8085 "/health"        "analytics-service (MassTransit)"

bash "$ROOT/scripts/seed-data.sh" --k8s || echo "  ⚠ Seed falló o los datos ya existían — continúa"

# Cerrar port-forwards del seed
kill $PF1 $PF2 $PF3 $PF4 $PF5 2>/dev/null || true

# ── 10. Resumen ───────────────────────────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════════════════"
echo "  Stack desplegado en minikube  (IP: $MINIKUBE_IP)"
echo ""
if $HOSTS_OK; then
  echo "  UI web:"
  echo "    http://platform.local"
else
  echo "  UI web (opción A — agrega esto a /etc/hosts y usa la URL):"
  echo "    $MINIKUBE_IP  platform.local"
  echo "    → http://platform.local"
  echo ""
  echo "  UI web (opción B — port-forward, sin tocar /etc/hosts):"
  echo "    kubectl port-forward svc/web-ui 8080:8080 -n puj-platform"
  echo "    → http://localhost:8080"
fi
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
