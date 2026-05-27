#!/usr/bin/env bash
# seed-data.sh — Pobla la plataforma con datos de demostración completos.
#
# Simula el ciclo de vida real vía HTTP APIs (sin SQL para contenido):
#   ✓ Registro de usuarios → USER_REGISTERED
#   ✓ Director creado por admin panel → USER_REGISTERED (adminCreate)
#   ✓ Inscripciones de estudiantes  → COURSE_ENROLLED
#   ✓ Lecciones completadas         → LESSON_COMPLETED
#   ✓ Submissions de evaluaciones   → ASSESSMENT_SUBMITTED  (respuestas correctas extraídas del API)
#   ✓ Foros, hilos y respuestas
#   ✓ Grupos de estudio con miembros
#
# Uso:
#   Docker Compose : bash scripts/seed-data.sh
#   Kubernetes     : bash scripts/seed-data.sh --k8s
#
# Con --k8s los port-forwards ya deben estar activos (los lanza local-k8s.sh):
#   user-service:8081  course-service:8082  assessment-service:8083
#   collaboration-service:8084  analytics-service:8085

set -euo pipefail

# ── Configuración según entorno ───────────────────────────────────────────────
K8S_MODE=false
[[ "${1:-}" == "--k8s" ]] && K8S_MODE=true

if $K8S_MODE; then
  USER_SVC="http://localhost:8081/api/v1"
  COURSE_SVC="http://localhost:8082/api/v1"
  ASSESS_SVC="http://localhost:8083/api/v1"
  COLLAB_SVC="http://localhost:8084/api/v1"
  ANALYTICS_API="http://localhost:8085/api/v1/analytics"
  ANALYTICS_HEALTH="http://localhost:8085/health"
  HEALTH_USER="http://localhost:8081/api/v1/health"
  HEALTH_COURSE="http://localhost:8082/api/v1/health"
  HEALTH_ASSESS="http://localhost:8083/api/v1/health"
  PSQL="kubectl exec -n puj-platform deploy/postgres -c postgres -- psql -U puj_admin -d learning_platform"
else
  GW="http://localhost:8090"
  USER_SVC="$GW/api/v1"
  COURSE_SVC="$GW/api/v1"
  ASSESS_SVC="$GW/api/v1"
  COLLAB_SVC="$GW/api/v1"
  ANALYTICS_API="$GW/api/v1/analytics"
  ANALYTICS_HEALTH="$GW/health/analytics"
  HEALTH_USER="$GW/health/user"
  HEALTH_COURSE="$GW/health/course"
  HEALTH_ASSESS="$GW/health/assessment"
  PSQL="docker exec -i puj-postgres psql -U puj_admin -d learning_platform"
fi

# ── Helpers ───────────────────────────────────────────────────────────────────
ok()   { echo "  ✓ $*"; }
info() { echo "  · $*"; }
step() { echo ""; echo "── $* ──────────────────────────────────────────────"; }
fail() { echo "  ✗ $*" >&2; exit 1; }

jval() { echo "$1" | python3 -c "import sys,json; d=json.load(sys.stdin); print($2)"; }

# Registra un usuario solo si no existe aún; devuelve "new" o "existing".
# Siempre imprime OK para no romper set -e.
register_or_skip() {
  local email=$1 pass=$2 first=$3 last=$4
  local http_code
  http_code=$(curl -s -o /tmp/_reg_resp.json -w "%{http_code}" -X POST "$USER_SVC/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\",\"password\":\"$pass\",\"firstName\":\"$first\",\"lastName\":\"$last\",\"consentGiven\":true}")
  if [[ "$http_code" == "201" || "$http_code" == "200" ]]; then
    ok "$email registrado (NUEVO → USER_REGISTERED)"
    echo "new"
  else
    info "$email ya existía (HTTP $http_code) — sin USER_REGISTERED"
    echo "existing"
  fi
}

# Espera a que analytics.$metric alcance el valor esperado (sondea cada 5 s).
# Requiere que ADMIN_TOKEN esté definido antes de la primera llamada.
analytics_wait() {
  local metric=$1 expected=$2 tries=${3:-24}
  info "Esperando analytics.$metric ≥ $expected..."
  for i in $(seq 1 "$tries"); do
    local val
    val=$(curl -sf -H "Authorization: Bearer $ADMIN_TOKEN" \
      "$ANALYTICS_API/dashboard/summary" 2>/dev/null \
      | python3 -c "import sys,json; print(json.load(sys.stdin).get('$metric',0))" 2>/dev/null \
      || echo "0")
    if python3 -c "exit(0 if int(float('$val')) >= $expected else 1)" 2>/dev/null; then
      ok "analytics.$metric = $val  (esperado ≥ $expected) ✓"
      return 0
    fi
    info "  $metric = $val / $expected  (intento $i/$tries)..."
    sleep 5
  done
  echo "  ⚠ analytics.$metric no alcanzó $expected tras $((tries * 5)) s — continuando"
}

# ══════════════════════════════════════════════════════════════════════════════
step "0 — Verificar conectividad RabbitMQ (productor Y consumidor)"

# a) Consumidor: analytics-service responde /health SOLO cuando MassTransit
#    ya está conectado a RabbitMQ (WaitUntilStarted=true).
info "Esperando analytics-service (MassTransit conectado)..."
READY=false
for i in $(seq 1 40); do
  if curl -sf "$ANALYTICS_HEALTH" > /dev/null 2>&1; then
    ok "analytics-service listo (intento $i/40)"
    READY=true
    break
  fi
  info "  analytics no responde (intento $i/40) — esperando 5 s..."
  sleep 5
done
$READY || fail "analytics-service no respondió tras 200 s. Verifica pod y port-forward 8085."

# c) Reset analytics_db — necesario cuando el postgres PVC persiste entre deploys.
#    Las tablas de analytics viven en la BD 'analytics_db' (EF Core EnsureCreated).
#    Truncamos los contadores para que el seed siempre parta de cero.
info "Reseteando analytics_db para seed limpio..."
PSQL_ANALYTICS="kubectl exec -n puj-platform deploy/postgres -c postgres -- psql -U puj_admin -d analytics_db"
if $K8S_MODE; then
  # Truncar en orden (FK: course_metrics, student_name_caches → platform_stats)
  kubectl exec -n puj-platform deploy/postgres -c postgres -- \
    psql -U puj_admin -d analytics_db -c \
    "TRUNCATE analytics.platform_stats, analytics.course_metrics, analytics.student_name_caches RESTART IDENTITY CASCADE;" \
    > /dev/null 2>&1 || true
else
  docker exec -i puj-postgres psql -U puj_admin -d analytics_db -c \
    "TRUNCATE analytics.platform_stats, analytics.course_metrics, analytics.student_name_caches RESTART IDENTITY CASCADE;" \
    > /dev/null 2>&1 || true
fi
ok "analytics_db truncada (counters en cero)"

# b) Productores: cada servicio Java reporta rabbitMQ:true en su /health
#    (la inyección de RabbitMQConnectionProvider llama isAvailable() en cada check)
wait_mq_producer() {
  local url=$1 label=$2
  info "Verificando productor RabbitMQ en $label..."
  for i in $(seq 1 20); do
    local resp mq_ok
    resp=$(curl -sf "$url" 2>/dev/null || echo '{}')
    mq_ok=$(echo "$resp" \
      | python3 -c "import sys,json; print(json.load(sys.stdin).get('rabbitMQ',False))" \
      2>/dev/null || echo "False")
    if [[ "$mq_ok" == "True" ]]; then
      ok "$label → rabbitMQ: true"
      return 0
    fi
    info "  $label rabbitMQ=$mq_ok (intento $i/20) — esperando 5 s..."
    sleep 5
  done
  echo "  ⚠ $label no confirmó RabbitMQ tras 100 s — continuando (eventos llegarán con retraso)"
}

wait_mq_producer "$HEALTH_USER"   "user-service"
wait_mq_producer "$HEALTH_COURSE" "course-service"
wait_mq_producer "$HEALTH_ASSESS" "assessment-service"

# Contador de USER_REGISTERED que el seed va a generar en esta ejecución.
# Se incrementa solo cuando register_or_skip devuelve "new".
EXPECTED_USER_REGISTERED=0

# ══════════════════════════════════════════════════════════════════════════════
step "1 — Admin: registro + promoción vía SQL"

ADMIN_REG=$(register_or_skip "admin@puj.edu.co" "Admin1234!" "Admin" "PUJ")
[[ "$ADMIN_REG" == "new" ]] && EXPECTED_USER_REGISTERED=$((EXPECTED_USER_REGISTERED + 1))

info "Promoviendo admin@puj.edu.co → ADMIN..."
$PSQL -c "UPDATE users.users SET role='ADMIN' WHERE email='admin@puj.edu.co';" > /dev/null
ok "admin@puj.edu.co es ADMIN"

ADMIN_RESP=$(curl -sf -X POST "$USER_SVC/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@puj.edu.co","password":"Admin1234!"}')
ADMIN_TOKEN=$(jval "$ADMIN_RESP" "d['accessToken']")
ADMIN_ID=$(jval     "$ADMIN_RESP" "d['user']['id']")
ok "Token admin obtenido (id=$ADMIN_ID)"

# ══════════════════════════════════════════════════════════════════════════════
step "2 — Instructor: registro + promoción vía SQL"

INSTR_REG=$(register_or_skip "prof.garcia@puj.edu.co" "Profesor1234!" "Carlos" "García")
[[ "$INSTR_REG" == "new" ]] && EXPECTED_USER_REGISTERED=$((EXPECTED_USER_REGISTERED + 1))

$PSQL -c "UPDATE users.users SET role='INSTRUCTOR' WHERE email='prof.garcia@puj.edu.co';" > /dev/null

INSTR_RESP=$(curl -sf -X POST "$USER_SVC/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"prof.garcia@puj.edu.co","password":"Profesor1234!"}')
INSTR_TOKEN=$(jval "$INSTR_RESP" "d['accessToken']")
INSTR_ID=$(jval     "$INSTR_RESP" "d['user']['id']")
ok "prof.garcia@puj.edu.co es INSTRUCTOR (id=$INSTR_ID)"

# ══════════════════════════════════════════════════════════════════════════════
step "3 — Director: creado por panel admin (prueba adminCreate → USER_REGISTERED)"

# adminCreate siempre dispara USER_REGISTERED; si ya existe devuelve 409 y lo saltamos
info "Creando director@puj.edu.co vía POST /api/v1/users (panel admin)..."
DIRECTOR_HTTP=$(curl -s -o /tmp/_dir_resp.json -w "%{http_code}" -X POST "$USER_SVC/users" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"email":"director@puj.edu.co","password":"Director1234!","firstName":"Lucía","lastName":"Mendoza","role":"DIRECTOR","consentGiven":true}')
if [[ "$DIRECTOR_HTTP" == "201" || "$DIRECTOR_HTTP" == "200" ]]; then
  DIRECTOR_ID=$(python3 -c "import json; print(json.load(open('/tmp/_dir_resp.json'))['id'])")
  ok "director@puj.edu.co creado (id=$DIRECTOR_ID) → USER_REGISTERED"
  EXPECTED_USER_REGISTERED=$((EXPECTED_USER_REGISTERED + 1))
else
  info "director@puj.edu.co ya existía (HTTP $DIRECTOR_HTTP) — sin USER_REGISTERED"
fi

DIRECTOR_LOGIN=$(curl -sf -X POST "$USER_SVC/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"director@puj.edu.co","password":"Director1234!"}')
DIRECTOR_TOKEN=$(jval "$DIRECTOR_LOGIN" "d['accessToken']")
ok "Token director obtenido"

# ══════════════════════════════════════════════════════════════════════════════
step "4 — Estudiantes: registro (hasta 5 × USER_REGISTERED)"

R=$(register_or_skip "maria.lopez@puj.edu.co"   "Estudiante1!" "María"     "López")
[[ "$R" == "new" ]] && EXPECTED_USER_REGISTERED=$((EXPECTED_USER_REGISTERED + 1))
R=$(register_or_skip "juan.perez@puj.edu.co"    "Estudiante1!" "Juan"      "Pérez")
[[ "$R" == "new" ]] && EXPECTED_USER_REGISTERED=$((EXPECTED_USER_REGISTERED + 1))
R=$(register_or_skip "sofia.ruiz@puj.edu.co"    "Estudiante1!" "Sofía"     "Ruiz")
[[ "$R" == "new" ]] && EXPECTED_USER_REGISTERED=$((EXPECTED_USER_REGISTERED + 1))
R=$(register_or_skip "andres.mora@puj.edu.co"   "Estudiante1!" "Andrés"    "Mora")
[[ "$R" == "new" ]] && EXPECTED_USER_REGISTERED=$((EXPECTED_USER_REGISTERED + 1))
R=$(register_or_skip "valentina.gil@puj.edu.co" "Estudiante1!" "Valentina" "Gil")
[[ "$R" == "new" ]] && EXPECTED_USER_REGISTERED=$((EXPECTED_USER_REGISTERED + 1))
ok "Total USER_REGISTERED que esperar en analytics: $EXPECTED_USER_REGISTERED"

get_token() {
  curl -sf -X POST "$USER_SVC/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$1\",\"password\":\"Estudiante1!\"}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])"
}

T_MARIA=$(get_token  "maria.lopez@puj.edu.co")
T_JUAN=$(get_token   "juan.perez@puj.edu.co")
T_SOFIA=$(get_token  "sofia.ruiz@puj.edu.co")
T_ANDRES=$(get_token "andres.mora@puj.edu.co")
T_VALENT=$(get_token "valentina.gil@puj.edu.co")
ok "Tokens de 5 estudiantes obtenidos"

# ── Checkpoint 1: TotalUsers ─────────────────────────────────────────────────
# Solo esperamos si se generaron nuevos USER_REGISTERED en este run
if [[ "$EXPECTED_USER_REGISTERED" -gt 0 ]]; then
  analytics_wait "totalUsers" "$EXPECTED_USER_REGISTERED"
else
  info "Todos los usuarios ya existían — saltando checkpoint totalUsers"
fi

# ══════════════════════════════════════════════════════════════════════════════
step "5 — Cursos"

create_course() {
  curl -sf -X POST "$COURSE_SVC/courses" \
    -H "Authorization: Bearer $INSTR_TOKEN" \
    -H "Content-Type: application/json" \
    -d "$1" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])"
}

CID1=$(create_course '{
  "title":       "Fundamentos de Programación con Java",
  "description": "Aprende los pilares de la POO con Java 17: tipos, estructuras de control, colecciones, herencia y manejo de excepciones.",
  "maxStudents": 40
}')
ok "Curso 1 — Java ($CID1)"

CID2=$(create_course '{
  "title":       "Arquitecturas de Software Modernas",
  "description": "Microservicios, arquitectura hexagonal, patrones de diseño y comunicación asíncrona con mensajería.",
  "maxStudents": 35
}')
ok "Curso 2 — Arquitecturas ($CID2)"

CID3=$(create_course '{
  "title":       "Bases de Datos Relacionales con PostgreSQL",
  "description": "Modelado, SQL avanzado, índices, transacciones ACID, procedimientos almacenados y optimización de consultas.",
  "maxStudents": 30
}')
ok "Curso 3 — PostgreSQL ($CID3)"

# ══════════════════════════════════════════════════════════════════════════════
step "6 — Módulos y lecciones"

create_module() {
  local cid=$1 title=$2 order=$3
  curl -sf -X POST "$COURSE_SVC/courses/$cid/modules" \
    -H "Authorization: Bearer $INSTR_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"title\":\"$title\",\"orderIndex\":$order}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])"
}

create_lesson() {
  local mid=$1 title=$2 content=$3 order=$4
  curl -sf -X POST "$COURSE_SVC/modules/$mid/lessons" \
    -H "Authorization: Bearer $INSTR_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"title\":\"$title\",\"content\":\"$content\",\"orderIndex\":$order,\"durationMinutes\":20,\"contentType\":\"TEXT\"}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])"
}

# Curso 1 — Java
MOD1A=$(create_module "$CID1" "Introducción a Java" 1)
MOD1B=$(create_module "$CID1" "Programación Orientada a Objetos" 2)

L1A1=$(create_lesson "$MOD1A" "¿Qué es Java y por qué usarlo?" \
  "Java es un lenguaje orientado a objetos, robusto y portable, creado por Sun Microsystems en 1995 y mantenido hoy por Oracle. Compila a bytecode que ejecuta la JVM." 1)
L1A2=$(create_lesson "$MOD1A" "Variables, tipos y operadores" \
  "Los tipos primitivos son: int, long, double, boolean, char. Las variables se declaran con su tipo seguido del nombre. Operadores aritméticos: +, -, *, / y %. El módulo (%) devuelve el resto de la división entera." 2)
L1A3=$(create_lesson "$MOD1A" "Estructuras de control" \
  "if/else, switch, for, while y do-while controlan el flujo. El for-each simplifica la iteración sobre colecciones: for (Tipo var : coleccion). break sale del bucle y continue pasa a la siguiente iteración." 3)

L1B1=$(create_lesson "$MOD1B" "Clases y objetos" \
  "Una clase define atributos (campos) y comportamientos (métodos). Los objetos son instancias creadas con new. Los constructores inicializan el estado del objeto y tienen el mismo nombre que la clase." 1)
L1B2=$(create_lesson "$MOD1B" "Herencia y polimorfismo" \
  "extends permite heredar de una clase padre. super() llama al constructor del padre. El polimorfismo permite tratar objetos de subclase como instancias de la superclase. @Override indica que se sobreescribe un método." 2)
L1B3=$(create_lesson "$MOD1B" "Interfaces y abstracción" \
  "Una interfaz define un contrato que las clases implementan con implements. Desde Java 8 pueden tener métodos default. Las clases abstractas (abstract) no se instancian y pueden mezclar métodos concretos y abstractos." 3)
ok "Módulos y lecciones del Curso 1 creados (ids: $L1A1, $L1A2, $L1A3, $L1B1, $L1B2, $L1B3)"

# Curso 2 — Arquitecturas
MOD2A=$(create_module "$CID2" "Fundamentos de Arquitectura" 1)
MOD2B=$(create_module "$CID2" "Microservicios en Práctica" 2)

L2A1=$(create_lesson "$MOD2A" "Estilos arquitectónicos" \
  "Los principales estilos son: monolítico, SOA, microservicios y serverless. El monolito es simple de desplegar; los microservicios escalan de forma independiente. Cada estilo tiene trade-offs en complejidad operacional." 1)
L2A2=$(create_lesson "$MOD2A" "Arquitectura hexagonal" \
  "La arquitectura hexagonal (puertos y adaptadores) separa el dominio de la infraestructura. El núcleo de negocio no depende de frameworks ni de bases de datos; los adaptadores traducen entre el mundo externo y el dominio." 2)
L2A3=$(create_lesson "$MOD2A" "Patrones de diseño GoF" \
  "Creacionales: Factory, Singleton, Builder. Estructurales: Adapter, Decorator, Facade. Comportamiento: Observer, Strategy, Command. Observer notifica automáticamente a múltiples objetos cuando cambia el estado del sujeto." 3)

L2B1=$(create_lesson "$MOD2B" "¿Qué son los microservicios?" \
  "Un microservicio es una unidad de despliegue independiente que implementa una capacidad de negocio específica. Se comunica mediante REST o mensajería asíncrona. Cada servicio tiene su propia base de datos." 1)
L2B2=$(create_lesson "$MOD2B" "Comunicación asíncrona con RabbitMQ" \
  "RabbitMQ implementa AMQP 0-9-1. Los productores publican mensajes a exchanges; los consumers los reciben de colas vinculadas. Los exchanges de tipo topic enrutan por patrón con comodines (* y #)." 2)
L2B3=$(create_lesson "$MOD2B" "Resiliencia y Circuit Breaker" \
  "El Circuit Breaker evita cascadas de fallos. Estado Closed: deja pasar llamadas. Si el ratio de error supera el umbral pasa a Open: rechaza sin llamar al servicio. Half-Open: deja pasar algunas llamadas de prueba." 3)
ok "Módulos y lecciones del Curso 2 creados (ids: $L2A1, $L2A2, $L2A3, $L2B1, $L2B2, $L2B3)"

# Curso 3 — PostgreSQL
MOD3A=$(create_module "$CID3" "SQL Fundamental" 1)
MOD3B=$(create_module "$CID3" "SQL Avanzado y Optimización" 2)

L3A1=$(create_lesson "$MOD3A" "Modelo relacional y DDL" \
  "El modelo relacional organiza datos en tablas con filas y columnas. DDL incluye: CREATE TABLE, ALTER TABLE y DROP TABLE. Las claves primarias (PRIMARY KEY) identifican filas unívocamente; las foráneas (FOREIGN KEY) relacionan tablas." 1)
L3A2=$(create_lesson "$MOD3A" "Consultas SELECT y JOINs" \
  "SELECT recupera datos. INNER JOIN devuelve solo filas con coincidencia en ambas tablas. LEFT JOIN incluye todas las filas de la tabla izquierda aunque no haya coincidencia. WHERE filtra filas; ORDER BY ordena resultados." 2)
L3A3=$(create_lesson "$MOD3A" "Funciones de agregación" \
  "COUNT, SUM, AVG, MIN y MAX operan sobre grupos de filas. GROUP BY agrupa; HAVING filtra grupos (como WHERE pero después de agregar). Ejemplo: SELECT dept, AVG(salary) FROM employees GROUP BY dept HAVING AVG(salary) > 50000;" 3)

L3B1=$(create_lesson "$MOD3B" "Índices y rendimiento" \
  "Los índices B-tree aceleran búsquedas al costo de escrituras más lentas. EXPLAIN ANALYZE muestra el plan real de ejecución con costos y tiempos. Un índice compuesto (col1, col2) cubre consultas que filtran por col1 o por (col1, col2)." 1)
L3B2=$(create_lesson "$MOD3B" "Transacciones ACID" \
  "Atomicidad: la transacción es todo o nada. Consistencia: la BD queda en estado válido. Isolation (Aislamiento): transacciones concurrentes no se interfieren. Durabilidad: los cambios persisten tras COMMIT. Usa BEGIN / COMMIT / ROLLBACK." 2)
L3B3=$(create_lesson "$MOD3B" "Window Functions" \
  "Las window functions operan sobre ventanas de filas sin colapsar el resultado. ROW_NUMBER() asigna número único. RANK() asigna rango con saltos en empates. LAG(col, n) y LEAD(col, n) acceden a filas anteriores o posteriores en la ventana." 3)
ok "Módulos y lecciones del Curso 3 creados (ids: $L3A1, $L3A2, $L3A3, $L3B1, $L3B2, $L3B3)"

# ══════════════════════════════════════════════════════════════════════════════
step "7 — Publicar cursos"

for CID in "$CID1" "$CID2" "$CID3"; do
  curl -sf -X POST "$COURSE_SVC/courses/$CID/publish" \
    -H "Authorization: Bearer $INSTR_TOKEN" > /dev/null
  ok "Curso $CID publicado"
done

# ══════════════════════════════════════════════════════════════════════════════
step "8 — Inscripciones (10 × COURSE_ENROLLED)"

enroll() {
  local token=$1 cid=$2 name=$3
  local http_code
  http_code=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    "$COURSE_SVC/enrollments/courses/$cid" \
    -H "Authorization: Bearer $token")
  case "$http_code" in
    200|201) ok "$name inscrito en $cid → COURSE_ENROLLED" ;;
    *)       info "$name ya inscrito o error ($http_code) en $cid" ;;
  esac
}

# Curso 1 (Java):        María, Juan, Andrés                 → 3 inscripciones
enroll "$T_MARIA"  "$CID1" "María"
enroll "$T_JUAN"   "$CID1" "Juan"
enroll "$T_ANDRES" "$CID1" "Andrés"

# Curso 2 (Arquitecturas): María, Sofía, Valentina            → 3 inscripciones
enroll "$T_MARIA"  "$CID2" "María"
enroll "$T_SOFIA"  "$CID2" "Sofía"
enroll "$T_VALENT" "$CID2" "Valentina"

# Curso 3 (PostgreSQL):  Juan, Sofía, Valentina, Andrés       → 4 inscripciones
enroll "$T_JUAN"   "$CID3" "Juan"
enroll "$T_SOFIA"  "$CID3" "Sofía"
enroll "$T_VALENT" "$CID3" "Valentina"
enroll "$T_ANDRES" "$CID3" "Andrés"

# ── Checkpoint 2 ─────────────────────────────────────────────────────────────
analytics_wait "totalEnrollments" 10
analytics_wait "totalCourses"      3

# ══════════════════════════════════════════════════════════════════════════════
step "9 — Completar lecciones (LESSON_COMPLETED)"

complete_lesson() {
  local token=$1 lid=$2 name=$3
  curl -s -o /dev/null -X POST "$COURSE_SVC/lessons/$lid/complete" \
    -H "Authorization: Bearer $token" || true
  ok "$name completó lección $lid"
}

# María (Java + Arquitecturas)
complete_lesson "$T_MARIA" "$L1A1" "María"
complete_lesson "$T_MARIA" "$L1A2" "María"
complete_lesson "$T_MARIA" "$L2A1" "María"
complete_lesson "$T_MARIA" "$L2A2" "María"

# Juan (Java + PostgreSQL)
complete_lesson "$T_JUAN" "$L1A1" "Juan"
complete_lesson "$T_JUAN" "$L1A2" "Juan"
complete_lesson "$T_JUAN" "$L3A1" "Juan"
complete_lesson "$T_JUAN" "$L3A2" "Juan"

# Sofía (Arquitecturas + PostgreSQL)
complete_lesson "$T_SOFIA" "$L2A1" "Sofía"
complete_lesson "$T_SOFIA" "$L2B1" "Sofía"
complete_lesson "$T_SOFIA" "$L3A1" "Sofía"
complete_lesson "$T_SOFIA" "$L3B1" "Sofía"

# Andrés (Java + PostgreSQL)
complete_lesson "$T_ANDRES" "$L1B1" "Andrés"
complete_lesson "$T_ANDRES" "$L3A1" "Andrés"
complete_lesson "$T_ANDRES" "$L3B1" "Andrés"

# Valentina (Arquitecturas + PostgreSQL)
complete_lesson "$T_VALENT" "$L2A2" "Valentina"
complete_lesson "$T_VALENT" "$L2B2" "Valentina"
complete_lesson "$T_VALENT" "$L3B2" "Valentina"

# ══════════════════════════════════════════════════════════════════════════════
step "10 — Evaluaciones (creadas vía API para que las respuestas sean dinámicas)"

# extract_answers: dado el JSON de AssessmentDetail, devuelve el mapa de
# answers { questionId: [correctOptionId, ...] } que acepta el endpoint /submit.
extract_answers() {
  python3 -c "
import sys, json
d = json.load(sys.stdin)
answers = {}
for q in d['questions']:
    correct = [o['id'] for o in q['options'] if o['correct']]
    if correct:
        answers[q['id']] = correct
print(json.dumps(answers))
"
}

# ── Evaluación 1: Fundamentos Java ───────────────────────────────────────────
info "Creando evaluación 1 (Java)..."
ASSESS1_JSON=$(curl -sf -X POST "$ASSESS_SVC/assessments" \
  -H "Authorization: Bearer $INSTR_TOKEN" \
  -H "Content-Type: application/json" \
  -d @- <<EOF
{
  "title": "Quiz: Pilares de la POO",
  "courseId": "$CID1",
  "description": "Evalúa tu comprensión de los principios de la programación orientada a objetos en Java.",
  "passingScorePct": 60.0,
  "maxAttempts": 3,
  "questions": [
    {
      "text": "¿Cuál principio de la POO permite ocultar la implementación interna de un objeto?",
      "type": "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "Herencia",        "correct": false },
        { "text": "Encapsulamiento", "correct": true  },
        { "text": "Polimorfismo",    "correct": false },
        { "text": "Abstracción",     "correct": false }
      ]
    },
    {
      "text": "¿Qué palabra clave se usa para heredar de una clase en Java?",
      "type": "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "implements", "correct": false },
        { "text": "inherits",   "correct": false },
        { "text": "super",      "correct": false },
        { "text": "extends",    "correct": true  }
      ]
    },
    {
      "text": "¿Cuál colección de Java no permite elementos duplicados?",
      "type": "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "ArrayList",  "correct": false },
        { "text": "LinkedList", "correct": false },
        { "text": "HashSet",    "correct": true  },
        { "text": "ArrayDeque", "correct": false }
      ]
    },
    {
      "text": "En Java con tipos enteros, ¿cuál es el resultado de 10 / 3?",
      "type": "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "3.33", "correct": false },
        { "text": "3",    "correct": true  },
        { "text": "4",    "correct": false },
        { "text": "Error de compilación", "correct": false }
      ]
    }
  ]
}
EOF
)
AID1=$(jval "$ASSESS1_JSON" "d['id']")
ANSWERS1=$(echo "$ASSESS1_JSON" | extract_answers)
ok "Evaluación 1 creada (id=$AID1)"

# ── Evaluación 2: Arquitecturas ───────────────────────────────────────────────
info "Creando evaluación 2 (Arquitecturas)..."
ASSESS2_JSON=$(curl -sf -X POST "$ASSESS_SVC/assessments" \
  -H "Authorization: Bearer $INSTR_TOKEN" \
  -H "Content-Type: application/json" \
  -d @- <<EOF
{
  "title": "Quiz: Microservicios y Patrones",
  "courseId": "$CID2",
  "description": "Comprensión de estilos arquitectónicos y patrones de diseño.",
  "passingScorePct": 60.0,
  "maxAttempts": 3,
  "questions": [
    {
      "text": "¿Cuál es la principal ventaja de los microservicios sobre el monolito?",
      "type": "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "Menor latencia de red",                "correct": false },
        { "text": "Escalado independiente por servicio",  "correct": true  },
        { "text": "Menor complejidad operacional",        "correct": false },
        { "text": "Transacciones distribuidas más fáciles","correct": false }
      ]
    },
    {
      "text": "¿Qué patrón de diseño notifica automáticamente a varios objetos cuando cambia el estado?",
      "type": "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "Factory",   "correct": false },
        { "text": "Strategy",  "correct": false },
        { "text": "Singleton", "correct": false },
        { "text": "Observer",  "correct": true  }
      ]
    },
    {
      "text": "En arquitectura hexagonal, ¿qué contiene el núcleo de la aplicación?",
      "type": "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "La base de datos",         "correct": false },
        { "text": "Los frameworks HTTP",      "correct": false },
        { "text": "Las reglas de negocio",    "correct": true  },
        { "text": "Los adaptadores de entrada","correct": false }
      ]
    },
    {
      "text": "¿En qué estado pasa un Circuit Breaker cuando supera el umbral de errores?",
      "type": "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "Closed",    "correct": false },
        { "text": "Half-Open", "correct": false },
        { "text": "Broken",    "correct": false },
        { "text": "Open",      "correct": true  }
      ]
    }
  ]
}
EOF
)
AID2=$(jval "$ASSESS2_JSON" "d['id']")
ANSWERS2=$(echo "$ASSESS2_JSON" | extract_answers)
ok "Evaluación 2 creada (id=$AID2)"

# ── Evaluación 3: PostgreSQL ──────────────────────────────────────────────────
info "Creando evaluación 3 (PostgreSQL)..."
ASSESS3_JSON=$(curl -sf -X POST "$ASSESS_SVC/assessments" \
  -H "Authorization: Bearer $INSTR_TOKEN" \
  -H "Content-Type: application/json" \
  -d @- <<EOF
{
  "title": "Quiz: SQL Avanzado",
  "courseId": "$CID3",
  "description": "JOINs, funciones de agregación, índices y transacciones ACID.",
  "passingScorePct": 65.0,
  "maxAttempts": 3,
  "questions": [
    {
      "text": "¿Qué tipo de JOIN devuelve solo las filas con coincidencias en ambas tablas?",
      "type": "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "LEFT JOIN",       "correct": false },
        { "text": "FULL OUTER JOIN", "correct": false },
        { "text": "CROSS JOIN",      "correct": false },
        { "text": "INNER JOIN",      "correct": true  }
      ]
    },
    {
      "text": "¿Cuál window function asigna un número de fila único dentro de cada partición?",
      "type": "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "RANK()",       "correct": false },
        { "text": "DENSE_RANK()", "correct": false },
        { "text": "COUNT(*)",     "correct": false },
        { "text": "ROW_NUMBER()", "correct": true  }
      ]
    },
    {
      "text": "En ACID, ¿qué garantiza la propiedad de Aislamiento (Isolation)?",
      "type": "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "Los cambios son permanentes tras COMMIT",                    "correct": false },
        { "text": "La transacción es toda o nada",                             "correct": false },
        { "text": "Las transacciones concurrentes no se ven entre sí",         "correct": true  },
        { "text": "La BD queda en un estado válido tras cada transacción",     "correct": false }
      ]
    },
    {
      "text": "¿Qué comando muestra el plan de ejecución real (con tiempos) de una consulta en PostgreSQL?",
      "type": "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "SHOW PLAN",       "correct": false },
        { "text": "DESCRIBE",        "correct": false },
        { "text": "QUERY PLAN",      "correct": false },
        { "text": "EXPLAIN ANALYZE", "correct": true  }
      ]
    }
  ]
}
EOF
)
AID3=$(jval "$ASSESS3_JSON" "d['id']")
ANSWERS3=$(echo "$ASSESS3_JSON" | extract_answers)
ok "Evaluación 3 creada (id=$AID3)"

# ══════════════════════════════════════════════════════════════════════════════
step "11 — Submissions (10 × ASSESSMENT_SUBMITTED)"

submit() {
  local token=$1 aid=$2 answers=$3 name=$4 course=$5
  local payload="{\"answers\":$answers,\"durationSeconds\":240}"
  local http_code
  http_code=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    "$ASSESS_SVC/assessments/$aid/submit" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "$payload")
  case "$http_code" in
    200|201) ok "$name submitió evaluación de $course → ASSESSMENT_SUBMITTED" ;;
    *)       info "$name — error $http_code al submitir $course" ;;
  esac
}

# Evaluación 1 (Java): inscritos en CID1 = María, Juan, Andrés
submit "$T_MARIA"  "$AID1" "$ANSWERS1" "María"  "Java"
submit "$T_JUAN"   "$AID1" "$ANSWERS1" "Juan"   "Java"
submit "$T_ANDRES" "$AID1" "$ANSWERS1" "Andrés" "Java"

# Evaluación 2 (Arquitecturas): inscritos en CID2 = María, Sofía, Valentina
submit "$T_MARIA"  "$AID2" "$ANSWERS2" "María"     "Arquitecturas"
submit "$T_SOFIA"  "$AID2" "$ANSWERS2" "Sofía"     "Arquitecturas"
submit "$T_VALENT" "$AID2" "$ANSWERS2" "Valentina" "Arquitecturas"

# Evaluación 3 (PostgreSQL): inscritos en CID3 = Juan, Sofía, Valentina, Andrés
submit "$T_JUAN"   "$AID3" "$ANSWERS3" "Juan"      "PostgreSQL"
submit "$T_SOFIA"  "$AID3" "$ANSWERS3" "Sofía"     "PostgreSQL"
submit "$T_VALENT" "$AID3" "$ANSWERS3" "Valentina" "PostgreSQL"
submit "$T_ANDRES" "$AID3" "$ANSWERS3" "Andrés"    "PostgreSQL"

# ── Checkpoint 3 ─────────────────────────────────────────────────────────────
analytics_wait "totalSubmissions" 10
analytics_wait "totalEnrollments" 10

# ══════════════════════════════════════════════════════════════════════════════
step "12 — Foros y discusiones"

create_forum() {
  local title=$1 desc=$2 cid=$3
  curl -sf -X POST "$COLLAB_SVC/forums" \
    -H "Authorization: Bearer $INSTR_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"title\":\"$title\",\"description\":\"$desc\",\"courseId\":\"$cid\"}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])"
}

create_thread() {
  local fid=$1 title=$2 content=$3 token=$4
  curl -sf -X POST "$COLLAB_SVC/forums/$fid/threads" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "{\"title\":\"$title\",\"content\":\"$content\"}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])"
}

reply_thread() {
  local tid=$1 content=$2 token=$3
  curl -s -o /dev/null -X POST "$COLLAB_SVC/forums/threads/$tid/posts" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "{\"content\":\"$content\"}" || true
}

FID1=$(create_forum "Discusiones: Fundamentos Java" \
  "Preguntas y debates sobre Java y programación orientada a objetos." "$CID1")
ok "Foro 1 creado ($FID1)"

FID2=$(create_forum "Discusiones: Arquitecturas de Software" \
  "Debates sobre patrones de diseño, microservicios y arquitectura." "$CID2")
ok "Foro 2 creado ($FID2)"

FID3=$(create_forum "Discusiones: PostgreSQL" \
  "Dudas sobre SQL, optimización de consultas y modelado de datos." "$CID3")
ok "Foro 3 creado ($FID3)"

# ── Foro 1: Java ─────────────────────────────────────────────────────────────
TH1=$(create_thread "$FID1" \
  "¿Java permite herencia múltiple?" \
  "Hola, leí que Java no permite herencia múltiple pero no entiendo por qué. ¿Cómo lo resuelvo?" \
  "$T_MARIA")
reply_thread "$TH1" \
  "Java no permite herencia múltiple de clases para evitar el problema del diamante. Sí puedes implementar varias interfaces simultáneamente con 'implements A, B'." \
  "$INSTR_TOKEN"
reply_thread "$TH1" \
  "¿Y si dos interfaces tienen un método default con la misma firma?" \
  "$T_MARIA"
reply_thread "$TH1" \
  "Debes sobreescribirlo obligatoriamente en la clase que las implementa. El compilador te forzará a hacerlo." \
  "$T_JUAN"

TH2=$(create_thread "$FID1" \
  "Diferencia entre List y Set en Java" \
  "¿Cuándo conviene usar List y cuándo Set? El profesor mencionó algo sobre duplicados." \
  "$T_JUAN")
reply_thread "$TH2" \
  "List mantiene el orden de inserción y permite duplicados. Set no permite duplicados y no garantiza orden (excepto TreeSet o LinkedHashSet). HashSet es el más rápido para verificar si un elemento ya existe." \
  "$INSTR_TOKEN"
reply_thread "$TH2" \
  "Uso Set cuando necesito eliminar duplicados automáticamente, por ejemplo al consolidar IDs únicos de una lista que puede tener repetidos." \
  "$T_ANDRES"

# ── Foro 2: Arquitecturas ─────────────────────────────────────────────────────
TH3=$(create_thread "$FID2" \
  "¿Los microservicios siempre son la mejor opción?" \
  "Estoy evaluando migrar un sistema a microservicios. ¿Siempre vale la pena el esfuerzo?" \
  "$T_SOFIA")
reply_thread "$TH3" \
  "Depende del contexto. Los microservicios añaden complejidad operacional: service discovery, trazas distribuidas, consistencia eventual, múltiples pipelines de despliegue. Para equipos pequeños, un monolito bien modularizado suele ser más productivo." \
  "$INSTR_TOKEN"
reply_thread "$TH3" \
  "La recomendación de Martin Fowler es: no empieces con microservicios; empieza con un monolito modular y extrae servicios cuando identifiques cuellos de botella reales de escalado." \
  "$T_VALENT"
reply_thread "$TH3" \
  "Buena referencia. ¿Y cuándo sería el momento correcto para dar el salto?" \
  "$T_SOFIA"
reply_thread "$TH3" \
  "Cuando un módulo específico necesite escalar independientemente, o cuando equipos distintos necesiten desplegar sin coordinarse. Ese es el umbral práctico." \
  "$INSTR_TOKEN"

# ── Foro 3: PostgreSQL ────────────────────────────────────────────────────────
TH4=$(create_thread "$FID3" \
  "¿Cuándo usar índice compuesto vs índice simple?" \
  "Tengo muchas consultas por (usuario_id, fecha). ¿Conviene un índice compuesto?" \
  "$T_ANDRES")
reply_thread "$TH4" \
  "Sí. CREATE INDEX idx_user_date ON tabla(usuario_id, fecha). El índice compuesto cubre consultas que filtran por usuario_id solo, o por (usuario_id, fecha). El orden de las columnas importa: la primera columna es la más selectiva." \
  "$INSTR_TOKEN"
reply_thread "$TH4" \
  "¿Y si a veces filtro solo por fecha sin usuario_id?" \
  "$T_ANDRES"
reply_thread "$TH4" \
  "En ese caso el índice compuesto (usuario_id, fecha) NO lo cubrirá. Necesitas un índice adicional solo en (fecha) o un índice separado (fecha, usuario_id) si esas consultas también son frecuentes." \
  "$T_JUAN"

ok "Foros, hilos y respuestas creados"

# ══════════════════════════════════════════════════════════════════════════════
step "13 — Grupos de estudio"

create_group() {
  local name=$1 cid=$2 token=$3
  curl -sf -X POST "$COLLAB_SVC/groups" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"$name\",\"courseId\":\"$cid\",\"maxMembers\":10}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])" \
    2>/dev/null || echo ""
}

join_group() {
  local gid=$1 token=$2 name=$3
  local http_code
  http_code=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    "$COLLAB_SVC/groups/$gid/join" \
    -H "Authorization: Bearer $token")
  case "$http_code" in
    200|201) ok "$name se unió al grupo $gid" ;;
    *)       info "$name — respuesta $http_code al unirse a $gid" ;;
  esac
}

GRP1=$(create_group "Equipo Java Avanzado"  "$CID1" "$T_MARIA")
GRP2=$(create_group "Club de Arquitecturas" "$CID2" "$T_SOFIA")
GRP3=$(create_group "SQL Masters"           "$CID3" "$T_JUAN")

[[ -n "$GRP1" ]] && { ok "Grupo 1 creado ($GRP1)"; join_group "$GRP1" "$T_JUAN" "Juan"; join_group "$GRP1" "$T_ANDRES" "Andrés"; }
[[ -n "$GRP2" ]] && { ok "Grupo 2 creado ($GRP2)"; join_group "$GRP2" "$T_MARIA" "María"; join_group "$GRP2" "$T_VALENT" "Valentina"; }
[[ -n "$GRP3" ]] && { ok "Grupo 3 creado ($GRP3)"; join_group "$GRP3" "$T_SOFIA" "Sofía"; join_group "$GRP3" "$T_ANDRES" "Andrés"; join_group "$GRP3" "$T_VALENT" "Valentina"; }

# ══════════════════════════════════════════════════════════════════════════════
step "Resumen final"

SUMMARY=$(curl -sf -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$ANALYTICS_API/dashboard/summary" 2>/dev/null || echo '{}')

echo ""
echo "══════════════════════════════════════════════════════════════════"
echo "  Seed completado — métricas en analytics-service:"
echo ""
echo "$SUMMARY" | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(f'  Usuarios registrados  : {d.get(\"totalUsers\", \"?\")}'  )
print(f'  Inscripciones         : {d.get(\"totalEnrollments\", \"?\")}'  )
print(f'  Cursos con actividad  : {d.get(\"totalCourses\", \"?\")}'  )
print(f'  Submissions           : {d.get(\"totalSubmissions\", \"?\")}'  )
print(f'  Puntuación promedio   : {d.get(\"averageScore\", \"?\")}'  )
print(f'  Tasa de aprobación    : {d.get(\"passRate\", \"?\")}'  )
" 2>/dev/null || echo "  (no se pudo obtener el resumen — verifica token y analytics)"
echo ""
echo "  Credenciales de demo:"
echo "    admin@puj.edu.co          Admin1234!    (ADMIN)"
echo "    director@puj.edu.co       Director1234! (DIRECTOR)"
echo "    prof.garcia@puj.edu.co    Profesor1234! (INSTRUCTOR)"
echo "    maria.lopez@puj.edu.co    Estudiante1!  (STUDENT)"
echo "    juan.perez@puj.edu.co     Estudiante1!  (STUDENT)"
echo "    sofia.ruiz@puj.edu.co     Estudiante1!  (STUDENT)"
echo "    andres.mora@puj.edu.co    Estudiante1!  (STUDENT)"
echo "    valentina.gil@puj.edu.co  Estudiante1!  (STUDENT)"
echo "══════════════════════════════════════════════════════════════════"
