#!/usr/bin/env bash
# setup-env.sh — Configuración inicial del entorno (ejecutar UNA sola vez)
# Genera el par RSA para JWT y crea el .env listo para usar con docker compose.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT/.env"
ENV_EXAMPLE="$ROOT/.env.example"

echo "=== PUJ Learning Platform — Setup inicial ==="

# ── 1. Crear .env desde el ejemplo si no existe ────────────────────────────
if [ ! -f "$ENV_FILE" ]; then
  cp "$ENV_EXAMPLE" "$ENV_FILE"
  echo "✓ .env creado desde .env.example"
else
  echo "  .env ya existe — omitiendo copia"
fi

# ── 2. Generar par RSA solo si no hay claves ya ────────────────────────────
PRIVATE_KEY_PEM="$ROOT/jwt_private.pem"
PUBLIC_KEY_PEM="$ROOT/jwt_public.pem"

EXISTING_PRIV=$(grep "^JWT_PRIVATE_KEY=" "$ENV_FILE" | cut -d'=' -f2-)
if [ -z "$EXISTING_PRIV" ]; then
  echo "  Generando par RSA 2048..."
  openssl genrsa -out "$PRIVATE_KEY_PEM" 2048 2>/dev/null
  openssl rsa -in "$PRIVATE_KEY_PEM" -pubout -out "$PUBLIC_KEY_PEM" 2>/dev/null

  # Escapar saltos de línea para meterlos en .env como una sola línea
  PRIV=$(awk '{printf "%s\\n", $0}' "$PRIVATE_KEY_PEM")
  PUB=$(awk '{printf "%s\\n", $0}' "$PUBLIC_KEY_PEM")

  # Sustituir en .env (compatible macOS y Linux)
  if sed --version 2>/dev/null | grep -q GNU; then
    sed -i "s|^JWT_PRIVATE_KEY=.*|JWT_PRIVATE_KEY=${PRIV}|" "$ENV_FILE"
    sed -i "s|^JWT_PUBLIC_KEY=.*|JWT_PUBLIC_KEY=${PUB}|"   "$ENV_FILE"
  else
    # macOS sed necesita '' después de -i
    sed -i '' "s|^JWT_PRIVATE_KEY=.*|JWT_PRIVATE_KEY=${PRIV}|" "$ENV_FILE"
    sed -i '' "s|^JWT_PUBLIC_KEY=.*|JWT_PUBLIC_KEY=${PUB}|"    "$ENV_FILE"
  fi

  echo "✓ Claves RSA generadas y escritas en .env"
  echo "  Archivos PEM guardados en: jwt_private.pem / jwt_public.pem"
else
  echo "  Claves JWT ya configuradas — omitiendo generación"
fi

# ── 3. Recordar qué falta rellenar manualmente ────────────────────────────
echo ""
echo "══════════════════════════════════════════════════════════"
echo "  Rellena manualmente en .env antes de hacer docker compose up:"
echo ""

check_var() {
  VAL=$(grep "^$1=" "$ENV_FILE" | cut -d'=' -f2-)
  if [ -z "$VAL" ]; then
    echo "  ✗ $1  ← pendiente"
  else
    echo "  ✓ $1"
  fi
}

check_var "AWS_ACCESS_KEY_ID"
check_var "AWS_SECRET_ACCESS_KEY"
check_var "REGISTRY"
check_var "SMTP_USER"
check_var "SMTP_PASSWORD"

echo ""
echo "  Las variables vacías solo bloquean funciones específicas:"
echo "  AWS → subida de materiales al S3"
echo "  REGISTRY → build/push de imágenes a ECR"
echo "  SMTP → correos en producción (local usa MailHog automáticamente)"
echo "══════════════════════════════════════════════════════════"
echo ""
echo "  Para levantar el entorno local:"
echo "    docker compose up -d"
echo ""
echo "  Listo."
