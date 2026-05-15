#!/usr/bin/env bash
# ecr-push.sh — Build y push de todas las imágenes a ECR
# Requiere: REGISTRY en .env, aws cli autenticado, Docker corriendo
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT/.env"

# Cargar variables del .env
set -a; source "$ENV_FILE"; set +a

if [ -z "${REGISTRY:-}" ]; then
  echo "ERROR: REGISTRY no está definido en .env"
  echo "  Ejemplo: REGISTRY=123456789.dkr.ecr.us-east-1.amazonaws.com"
  exit 1
fi

# Extraer región del registry de ECR (si aplica)
AWS_REGION=$(echo "$REGISTRY" | grep -oP '(?<=\.ecr\.)[^.]+(?=\.amazonaws)' || true)

# ── Login a ECR ───────────────────────────────────────────────────────────
if [ -n "$AWS_REGION" ]; then
  echo "=== Login a ECR (región: $AWS_REGION) ==="
  aws ecr get-login-password --region "$AWS_REGION" \
    | docker login --username AWS --password-stdin "$REGISTRY"
  echo "✓ Login exitoso"
else
  echo "  Registry no es ECR — omitiendo aws login"
fi

# ── Tag de imagen ─────────────────────────────────────────────────────────
TAG="${1:-latest}"
echo ""
echo "=== Build y push con tag: $TAG ==="
echo "    Registry: $REGISTRY"
echo ""

build_push() {
  local NAME=$1
  local DOCKERFILE=$2
  local CONTEXT=$3
  echo "── $NAME ─────────────────────────────"
  docker build \
    --build-arg BUILDKIT_INLINE_CACHE=1 \
    -t "$REGISTRY/puj/$NAME:$TAG" \
    -t "$REGISTRY/puj/$NAME:latest" \
    -f "$ROOT/$DOCKERFILE" \
    "$ROOT/$CONTEXT"
  docker push "$REGISTRY/puj/$NAME:$TAG"
  [ "$TAG" != "latest" ] && docker push "$REGISTRY/puj/$NAME:latest"
  echo "✓ $NAME pushed"
  echo ""
}

# Servicios con contexto en raíz (necesitan libs/ en el build context)
build_push user-service          services/user-service/Dockerfile          .
build_push course-service        services/course-service/Dockerfile         .
build_push assessment-service    services/assessment-service/Dockerfile     .
build_push collaboration-service services/collaboration-service/Dockerfile  .
build_push email-service         services/email-service/Dockerfile          .
build_push web-ui                frontend/web-ui/Dockerfile                 .

# analytics-service tiene su propio contexto
echo "── analytics-service ──────────────────"
docker build \
  -t "$REGISTRY/puj/analytics-service:$TAG" \
  -t "$REGISTRY/puj/analytics-service:latest" \
  "$ROOT/services/analytics-service"
docker push "$REGISTRY/puj/analytics-service:$TAG"
[ "$TAG" != "latest" ] && docker push "$REGISTRY/puj/analytics-service:latest"
echo "✓ analytics-service pushed"

# ── Reemplazar REGISTRY en los manifests K8s ─────────────────────────────
echo ""
echo "=== Actualizando manifests K8s con REGISTRY=$REGISTRY ==="
find "$ROOT/infra/k8s" -name "deployment.yaml" \
  -exec sed -i "s|REGISTRY|$REGISTRY|g" {} \;
echo "✓ Manifests actualizados"

echo ""
echo "=== Todas las imágenes publicadas. Para desplegar en K8s: ==="
echo "    kubectl apply -k infra/k8s/"
