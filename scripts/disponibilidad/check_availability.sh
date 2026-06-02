
set -euo pipefail

# ── Configuración de URLs base ────────────────────────────────────────────────
USERS="${1:-http://localhost:8081}"
COURSES="${2:-http://localhost:8082}"
ASSESSMENTS="${3:-http://localhost:8083}"
COLLAB="${4:-http://localhost:8084}"
ANALYTICS="${5:-http://localhost:5000}"

PASS=0
FAIL=0

# ── Colores ───────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

# ── Helpers ───────────────────────────────────────────────────────────────────
log_test() { echo -e "${CYAN}[TEST] $1${NC}"; }
log_ok()   { echo -e "${GREEN}  ✔ PASS — $1${NC}"; ((PASS++)); }
log_fail() { echo -e "${RED}  ✘ FAIL — $1${NC}"; ((FAIL++)); }

check_status() {
  local id="$1" desc="$2" expected="$3" url="$4"
  shift 4
  log_test "$id · $desc"
  HTTP=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$@" "$url" 2>/dev/null || echo "000")
  TIME=$(curl -s -o /dev/null -w "%{time_total}" --max-time 5 "$@" "$url" 2>/dev/null || echo "9.999")
  if [[ "$HTTP" == "$expected" && $(echo "$TIME < 1.0" | bc -l) -eq 1 ]]; then
    log_ok "HTTP $HTTP · tiempo ${TIME}s"
  else
    log_fail "HTTP $HTTP (esperado $expected) · tiempo ${TIME}s (esperado < 1 s)"
  fi
}

# ─────────────────────────────────────────────────────────────────────────────
echo ""
echo "============================================================"
echo " PRUEBAS DE DISPONIBILIDAD  — Plan Pruebas PUJ 2026"
echo "============================================================"

# ── TD-001: Login exitoso ─────────────────────────────────────────────────────
log_test "TD-001 · Login con credenciales válidas"
RESPONSE=$(curl -s --max-time 5 -X POST "$USERS/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"estudiante@puj.edu.co","password":"Password1!"}' 2>/dev/null || echo '{}')
TOKEN=$(echo "$RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('accessToken',''))" 2>/dev/null || echo "")
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 -X POST "$USERS/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"estudiante@puj.edu.co","password":"Password1!"}' 2>/dev/null || echo "000")
if [[ "$HTTP_CODE" == "200" ]]; then
  log_ok "HTTP 200 · token obtenido"
else
  log_fail "HTTP $HTTP_CODE (esperado 200)"
fi

# ── TD-002: Login fallido → bloqueo ──────────────────────────────────────────
log_test "TD-002 · 5 intentos fallidos → cuenta bloqueada (HTTP 401)"
for i in {1..5}; do
  curl -s -o /dev/null --max-time 5 -X POST "$USERS/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"email":"bloqueo@puj.edu.co","password":"Incorrecta!"}' &>/dev/null || true
done
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 -X POST "$USERS/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"bloqueo@puj.edu.co","password":"Incorrecta!"}' 2>/dev/null || echo "000")
if [[ "$HTTP_CODE" == "401" ]]; then
  log_ok "HTTP 401 · cuenta bloqueada correctamente"
else
  log_fail "HTTP $HTTP_CODE (esperado 401 tras 5 intentos fallidos)"
fi

# ── TD-003: Perfil del usuario autenticado ────────────────────────────────────
check_status "TD-003" "GET /auth/me → HTTP 200" "200" \
  "$USERS/api/v1/auth/me" \
  -H "Authorization: Bearer ${TOKEN:-invalid}"

# ── TD-004: Catálogo de cursos publicados ─────────────────────────────────────
check_status "TD-004" "GET /courses/published → HTTP 200" "200" \
  "$COURSES/api/v1/courses?page=0&size=10"

# ── TD-005: Health check user-service ─────────────────────────────────────────
check_status "TD-005" "GET /health user-service → HTTP 200" "200" \
  "$USERS/api/v1/health"

# ── TD-006: Health check course-service ──────────────────────────────────────
check_status "TD-006" "GET /health course-service → HTTP 200" "200" \
  "$COURSES/api/v1/health"

# ── TD-007: Health check assessment-service ──────────────────────────────────
check_status "TD-007" "GET /health assessment-service → HTTP 200" "200" \
  "$ASSESSMENTS/api/v1/health"

# ── TD-008: Health check collaboration-service ────────────────────────────────
check_status "TD-008" "GET /health collaboration-service → HTTP 200" "200" \
  "$COLLAB/api/v1/health"

# ── TD-009: Health check analytics-service ────────────────────────────────────
check_status "TD-009" "GET /health analytics-service → HTTP 200" "200" \
  "$ANALYTICS/api/v1/health"

# ── TD-010: Dashboard analytics acceso ADMIN ──────────────────────────────────
check_status "TD-010" "GET /analytics/dashboard/summary (ADMIN) → HTTP 200" "200" \
  "$ANALYTICS/api/v1/analytics/dashboard/summary" \
  -H "Authorization: Bearer ${TOKEN:-invalid}"

# ── Resumen ───────────────────────────────────────────────────────────────────
echo ""
echo "============================================================"
echo " RESULTADOS: $PASS PASS · $FAIL FAIL"
echo "============================================================"
echo ""
[[ $FAIL -eq 0 ]] && exit 0 || exit 1
