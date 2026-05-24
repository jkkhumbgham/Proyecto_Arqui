#!/usr/bin/env bash
# push-images.sh — Build y push de todas las imágenes a cualquier registry OCI
#
# Registry gratuito recomendado: ghcr.io (GitHub Container Registry)
#   REGISTRY=ghcr.io/tu-usuario
#
# Requiere: REGISTRY definido en .env, Docker corriendo, login previo al registry
# Uso: bash scripts/ecr-push.sh [TAG]   (TAG por defecto: latest)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT/.env"

set -a; source "$ENV_FILE"; set +a

if [ -z "${REGISTRY:-}" ]; then
  echo "ERROR: REGISTRY no está definido en .env"
  echo ""
  echo "  Para ghcr.io (gratis con cuenta GitHub):"
  echo "    REGISTRY=ghcr.io/tu-usuario-github"
  echo ""
  echo "  Para registry local (sin internet):"
  echo "    REGISTRY=localhost:5000"
  echo ""
  echo "  Luego haz login antes de ejecutar este script:"
  echo "    ghcr.io:  echo \$GITHUB_PAT | docker login ghcr.io -u tu-usuario --password-stdin"
  echo "    local:    (sin login necesario)"
  exit 1
fi

# Login automático a ECR si el registry es de AWS (opcional, solo en entornos pagados)
AWS_REGION=$(echo "$REGISTRY" | grep -oP '(?<=\.ecr\.)[^.]+(?=\.amazonaws)' || true)
if [ -n "$AWS_REGION" ]; then
  echo "=== Login a ECR (región: $AWS_REGION) ==="
  aws ecr get-login-password --region "$AWS_REGION" \
    | docker login --username AWS --password-stdin "$REGISTRY"
  echo "✓ Login exitoso"
fi

TAG="${1:-latest}"
echo ""
echo "=== Build y push  tag=$TAG  registry=$REGISTRY ==="
echo ""

build_push() {
  local NAME=$1
  local DOCKERFILE=$2
  local CONTEXT=$3
  echo "── $NAME ──────────────────────────────────────"
  docker build \
    --build-arg BUILDKIT_INLINE_CACHE=1 \
    -t "$REGISTRY/puj/$NAME:$TAG" \
    -t "$REGISTRY/puj/$NAME:latest" \
    -f "$ROOT/$DOCKERFILE" \
    "$ROOT/$CONTEXT"
  docker push "$REGISTRY/puj/$NAME:$TAG"
  [ "$TAG" != "latest" ] && docker push "$REGISTRY/puj/$NAME:latest"
  echo "✓ $NAME"
  echo ""
}

build_push user-service          services/user-service/Dockerfile          .
build_push course-service        services/course-service/Dockerfile         .
build_push assessment-service    services/assessment-service/Dockerfile     .
build_push collaboration-service services/collaboration-service/Dockerfile  .
build_push email-service         services/email-service/Dockerfile          .
build_push web-ui                frontend/web-ui/Dockerfile                 .

echo "── analytics-service ──────────────────────────"
docker build \
  -t "$REGISTRY/puj/analytics-service:$TAG" \
  -t "$REGISTRY/puj/analytics-service:latest" \
  "$ROOT/services/analytics-service"
docker push "$REGISTRY/puj/analytics-service:$TAG"
[ "$TAG" != "latest" ] && docker push "$REGISTRY/puj/analytics-service:latest"
echo "✓ analytics-service"

echo ""
echo "=== Todas las imágenes publicadas en $REGISTRY ==="
echo ""
echo "Para desplegar en minikube con estas imágenes:"
echo "  kubectl apply -k infra/k8s/overlays/registry/"
echo ""
echo "Para desplegar en producción (K8s real):"
echo "  kubectl apply -k infra/k8s/"
