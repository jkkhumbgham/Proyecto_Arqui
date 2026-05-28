#!/usr/bin/env bash
# seed-data.sh — Pobla la plataforma con datos de demostración completos.
#
# Simula el ciclo de vida real vía HTTP APIs (sin SQL para contenido):
#   ✓ Registro de usuarios → USER_REGISTERED
#   ✓ Director creado por admin panel → USER_REGISTERED (adminCreate)
#   ✓ Inscripciones de estudiantes  → COURSE_ENROLLED
#   ✓ Lecciones completadas         → LESSON_COMPLETED
#   ✓ Lecciones suplementarias desbloqueadas por reglas adaptativas
#   ✓ Contenidos de lección (TEXT, VIDEO, PDF, DOCUMENT)
#   ✓ Evaluaciones vinculadas a lecciones + evaluaciones de cierre de curso
#   ✓ Submissions de evaluaciones   → ASSESSMENT_SUBMITTED
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

# Espera a que analytics.$metric alcance el valor esperado.
analytics_wait() {
  local metric=$1 expected=$2 tries=${3:-24}
  info "Esperando analytics.$metric >= $expected..."
  for i in $(seq 1 "$tries"); do
    local val
    val=$(curl -sf -H "Authorization: Bearer $ADMIN_TOKEN" \
      "$ANALYTICS_API/dashboard/summary" 2>/dev/null \
      | python3 -c "import sys,json; print(json.load(sys.stdin).get('$metric',0))" 2>/dev/null \
      || echo "0")
    if python3 -c "exit(0 if int(float('$val')) >= $expected else 1)" 2>/dev/null; then
      ok "analytics.$metric = $val  (esperado >= $expected) ✓"
      return 0
    fi
    info "  $metric = $val / $expected  (intento $i/$tries)..."
    sleep 5
  done
  echo "  ⚠ analytics.$metric no alcanzo $expected tras $((tries * 5)) s — continuando"
}

# ══════════════════════════════════════════════════════════════════════════════
step "0 — Verificar conectividad RabbitMQ (productor Y consumidor)"

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
$READY || fail "analytics-service no respondio tras 200 s. Verifica pod y port-forward 8085."

info "Reseteando analytics_db para seed limpio..."
if $K8S_MODE; then
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

info "Reseteando learning_platform (cursos, evaluaciones, colaboracion)..."
$PSQL -c "
TRUNCATE
  courses.lesson_progress,
  courses.lesson_contents,
  courses.s3_resources,
  courses.lessons,
  courses.modules,
  courses.enrollments,
  courses.courses,
  assessments.submissions,
  assessments.adaptive_rules,
  assessments.answer_options,
  assessments.questions,
  assessments.assessments,
  collaboration.chat_messages,
  collaboration.group_members,
  collaboration.posts,
  collaboration.threads,
  collaboration.forums,
  collaboration.study_groups
CASCADE;" > /dev/null 2>&1 || true
ok "learning_platform truncada (cursos/evaluaciones/colaboracion en cero)"

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
  echo "  ⚠ $label no confirmo RabbitMQ tras 100 s — continuando"
}

wait_mq_producer "$HEALTH_USER"   "user-service"
wait_mq_producer "$HEALTH_COURSE" "course-service"
wait_mq_producer "$HEALTH_ASSESS" "assessment-service"

EXPECTED_USER_REGISTERED=0

# ══════════════════════════════════════════════════════════════════════════════
step "1 — Admin: registro + promocion via SQL"

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
step "2 — Instructor: registro + promocion via SQL"

INSTR_REG=$(register_or_skip "prof.garcia@puj.edu.co" "Profesor1234!" "Carlos" "Garcia")
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

info "Creando director@puj.edu.co via POST /api/v1/users (panel admin)..."
DIRECTOR_HTTP=$(curl -s -o /tmp/_dir_resp.json -w "%{http_code}" -X POST "$USER_SVC/users" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"email":"director@puj.edu.co","password":"Director1234!","firstName":"Lucia","lastName":"Mendoza","role":"DIRECTOR","consentGiven":true}')
if [[ "$DIRECTOR_HTTP" == "201" || "$DIRECTOR_HTTP" == "200" ]]; then
  DIRECTOR_ID=$(python3 -c "import json; print(json.load(open('/tmp/_dir_resp.json'))['id'])")
  ok "director@puj.edu.co creado (id=$DIRECTOR_ID) → USER_REGISTERED"
  EXPECTED_USER_REGISTERED=$((EXPECTED_USER_REGISTERED + 1))
else
  info "director@puj.edu.co ya existia (HTTP $DIRECTOR_HTTP) — sin USER_REGISTERED"
fi

DIRECTOR_LOGIN=$(curl -sf -X POST "$USER_SVC/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"director@puj.edu.co","password":"Director1234!"}')
DIRECTOR_TOKEN=$(jval "$DIRECTOR_LOGIN" "d['accessToken']")
ok "Token director obtenido"

# ══════════════════════════════════════════════════════════════════════════════
step "4 — Estudiantes: registro (hasta 5 x USER_REGISTERED)"

R=$(register_or_skip "maria.lopez@puj.edu.co"   "Estudiante1!" "Maria"     "Lopez")
[[ "$R" == "new" ]] && EXPECTED_USER_REGISTERED=$((EXPECTED_USER_REGISTERED + 1))
R=$(register_or_skip "juan.perez@puj.edu.co"    "Estudiante1!" "Juan"      "Perez")
[[ "$R" == "new" ]] && EXPECTED_USER_REGISTERED=$((EXPECTED_USER_REGISTERED + 1))
R=$(register_or_skip "sofia.ruiz@puj.edu.co"    "Estudiante1!" "Sofia"     "Ruiz")
[[ "$R" == "new" ]] && EXPECTED_USER_REGISTERED=$((EXPECTED_USER_REGISTERED + 1))
R=$(register_or_skip "andres.mora@puj.edu.co"   "Estudiante1!" "Andres"    "Mora")
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

if [[ "$EXPECTED_USER_REGISTERED" -gt 0 ]]; then
  analytics_wait "totalUsers" "$EXPECTED_USER_REGISTERED"
else
  info "Todos los usuarios ya existian — saltando checkpoint totalUsers"
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
  "title":       "Fundamentos de Programacion con Java",
  "description": "Aprende los pilares de la POO con Java 17: tipos, estructuras de control, colecciones, herencia y manejo de excepciones.",
  "maxStudents": 40
}')
ok "Curso 1 — Java ($CID1)"

CID2=$(create_course '{
  "title":       "Arquitecturas de Software Modernas",
  "description": "Microservicios, arquitectura hexagonal, patrones de diseno y comunicacion asincrona con mensajeria.",
  "maxStudents": 35
}')
ok "Curso 2 — Arquitecturas ($CID2)"

CID3=$(create_course '{
  "title":       "Bases de Datos Relacionales con PostgreSQL",
  "description": "Modelado, SQL avanzado, indices, transacciones ACID, procedimientos almacenados y optimizacion de consultas.",
  "maxStudents": 30
}')
ok "Curso 3 — PostgreSQL ($CID3)"

# ══════════════════════════════════════════════════════════════════════════════
step "6 — Modulos, lecciones, suplementarias y contenidos"

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
    -d "{\"title\":\"$title\",\"content\":\"$content\",\"orderIndex\":$order,\"durationMinutes\":20}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])"
}

create_supp_lesson() {
  local mid=$1 title=$2 content=$3 order=$4
  curl -sf -X POST "$COURSE_SVC/modules/$mid/lessons" \
    -H "Authorization: Bearer $INSTR_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"title\":\"$title\",\"content\":\"$content\",\"orderIndex\":$order,\"durationMinutes\":15,\"supplementary\":true}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])"
}

# add_content <lessonId> <title> <contentType> [url] [description]
add_content() {
  local lid=$1 title=$2 ctype=$3 url="${4:-}" desc="${5:-Recurso de aprendizaje}"
  local body="{\"title\":\"$title\",\"description\":\"$desc\",\"contentType\":\"$ctype\""
  [[ -n "$url" ]] && body="$body,\"contentUrl\":\"$url\""
  body="$body}"
  curl -s -o /dev/null -X POST "$COURSE_SVC/lessons/$lid/contents" \
    -H "Authorization: Bearer $INSTR_TOKEN" \
    -H "Content-Type: application/json" \
    -d "$body" || true
}

# ── Curso 1 — Java ────────────────────────────────────────────────────────────
MOD1A=$(create_module "$CID1" "Introduccion a Java" 1)
MOD1B=$(create_module "$CID1" "Programacion Orientada a Objetos" 2)

L1A1=$(create_lesson "$MOD1A" "Que es Java y por que usarlo" \
  "Java es un lenguaje orientado a objetos, robusto y portable, creado por Sun Microsystems en 1995 y mantenido hoy por Oracle. Compila a bytecode que ejecuta la JVM." 1)
L1A2=$(create_lesson "$MOD1A" "Variables, tipos y operadores" \
  "Los tipos primitivos son: int, long, double, boolean, char. Las variables se declaran con su tipo seguido del nombre. Operadores aritmeticos: +, -, *, / y %. El modulo (%) devuelve el resto de la division entera." 2)
L1A3=$(create_lesson "$MOD1A" "Estructuras de control" \
  "if/else, switch, for, while y do-while controlan el flujo. El for-each simplifica la iteracion sobre colecciones. break sale del bucle y continue pasa a la siguiente iteracion." 3)
L1A_SUPP=$(create_supp_lesson "$MOD1A" "Practica extra: tipos y control de flujo" \
  "Ejercicios adicionales de refuerzo sobre tipos primitivos, operadores y estructuras de control en Java. Ideal si necesitas mas practica antes de avanzar al siguiente modulo." 4)

L1B1=$(create_lesson "$MOD1B" "Clases y objetos" \
  "Una clase define atributos y comportamientos. Los objetos son instancias creadas con new. Los constructores inicializan el estado del objeto y tienen el mismo nombre que la clase." 1)
L1B2=$(create_lesson "$MOD1B" "Herencia y polimorfismo" \
  "extends permite heredar de una clase padre. super() llama al constructor del padre. El polimorfismo permite tratar objetos de subclase como instancias de la superclase." 2)
L1B3=$(create_lesson "$MOD1B" "Interfaces y abstraccion" \
  "Una interfaz define un contrato que las clases implementan con implements. Desde Java 8 pueden tener metodos default. Las clases abstractas no se instancian y pueden mezclar metodos concretos y abstractos." 3)
L1B_SUPP=$(create_supp_lesson "$MOD1B" "Practica extra: herencia y diseno de clases" \
  "Ejercicios de refuerzo sobre herencia, polimorfismo e interfaces. Aprende a identificar cuando usar clases abstractas vs interfaces con ejemplos practicos del mundo real." 4)

ok "Modulos y lecciones del Curso 1 creados"

# Contenidos Curso 1 — L1A1: TEXT + VIDEO
add_content "$L1A1" "Lectura: Historia y ecosistema de Java" "TEXT" "" \
  "Resumen de la historia de Java, la JVM y por que Java sigue siendo el lenguaje mas usado en backend empresarial."
add_content "$L1A1" "Video: Introduccion a Java 17" "VIDEO" \
  "https://www.youtube.com/watch?v=eIrMbAQSU34" \
  "Tutorial en video: primeros pasos con Java, instalacion del JDK y tu primer programa Hola Mundo."

# L1A2: TEXT + PDF
add_content "$L1A2" "Lectura: Tipos primitivos y referencias" "TEXT" "" \
  "Tabla comparativa de todos los tipos primitivos de Java con rangos de valores y casos de uso recomendados."
add_content "$L1A2" "Java Language Specification (PDF)" "PDF" \
  "https://docs.oracle.com/javase/specs/jls/se17/jls17.pdf" \
  "Especificacion oficial del lenguaje Java SE 17 — referencia completa de tipos, expresiones y sentencias."

# L1A3: TEXT + DOCUMENT
add_content "$L1A3" "Lectura: Guia de estructuras de control" "TEXT" "" \
  "Comparacion de if/else vs switch, bucles for vs while y patrones comunes de iteracion en Java."
add_content "$L1A3" "Ejercicios: Estructuras de control (DOCX)" "DOCUMENT" \
  "https://file-examples.com/wp-content/uploads/2017/02/file-sample_100kB.docx" \
  "Hoja de ejercicios con 20 problemas de logica usando estructuras de control en Java."

# L1A_SUPP: TEXT only
add_content "$L1A_SUPP" "Lectura suplementaria: Practica guiada" "TEXT" "" \
  "Ejercicios paso a paso con soluciones comentadas para reforzar tipos y control de flujo antes de continuar."

# L1B1: TEXT + VIDEO
add_content "$L1B1" "Lectura: Anatomia de una clase Java" "TEXT" "" \
  "Guia visual de la estructura de una clase: campos, constructores, metodos, getters/setters y buenas practicas de encapsulamiento."
add_content "$L1B1" "Video: Clases y objetos en Java" "VIDEO" \
  "https://www.youtube.com/watch?v=IIAbOdfgx0E" \
  "Video tutorial sobre como crear clases, instanciar objetos y usar constructores en Java."

# L1B2: TEXT + PDF
add_content "$L1B2" "Lectura: Herencia vs composicion" "TEXT" "" \
  "Cuando usar herencia y cuando preferir composicion. El principio de Liskov y como aplicarlo en Java."
add_content "$L1B2" "JVM Specification SE 17 (PDF)" "PDF" \
  "https://docs.oracle.com/javase/specs/jvms/se17/jvms17.pdf" \
  "Especificacion de la JVM: como se implementa internamente la herencia y el despacho de metodos virtuales."

# L1B3: TEXT + DOCUMENT
add_content "$L1B3" "Lectura: Interfaces funcionales en Java 8+" "TEXT" "" \
  "Como las interfaces funcionales habilitaron las lambdas en Java 8. Ejemplos con Runnable, Comparator y Callable."
add_content "$L1B3" "Taller: Diseno con interfaces (DOCX)" "DOCUMENT" \
  "https://file-examples.com/wp-content/uploads/2017/02/file-sample_100kB.docx" \
  "Taller practico: implementa el patron Strategy usando interfaces en un sistema de pagos simplificado."

# L1B_SUPP: TEXT only
add_content "$L1B_SUPP" "Lectura suplementaria: Ejercicios OOP avanzados" "TEXT" "" \
  "Problemas de diseno orientado a objetos con solucion: jerarquias de herencia, interfaces multiples y clases abstractas."

ok "Contenidos del Curso 1 agregados (TEXT+VIDEO, TEXT+PDF, TEXT+DOCUMENT, TEXT)"

# ── Curso 2 — Arquitecturas ────────────────────────────────────────────────────
MOD2A=$(create_module "$CID2" "Fundamentos de Arquitectura" 1)
MOD2B=$(create_module "$CID2" "Microservicios en Practica" 2)

L2A1=$(create_lesson "$MOD2A" "Estilos arquitectonicos" \
  "Los principales estilos son: monolitico, SOA, microservicios y serverless. El monolito es simple de desplegar; los microservicios escalan de forma independiente. Cada estilo tiene trade-offs en complejidad operacional." 1)
L2A2=$(create_lesson "$MOD2A" "Arquitectura hexagonal" \
  "La arquitectura hexagonal separa el dominio de la infraestructura. El nucleo de negocio no depende de frameworks ni de bases de datos; los adaptadores traducen entre el mundo externo y el dominio." 2)
L2A3=$(create_lesson "$MOD2A" "Patrones de diseno GoF" \
  "Creacionales: Factory, Singleton, Builder. Estructurales: Adapter, Decorator, Facade. Comportamiento: Observer, Strategy, Command. Observer notifica automaticamente a multiples objetos cuando cambia el estado del sujeto." 3)
L2A_SUPP=$(create_supp_lesson "$MOD2A" "Practica extra: arquitectura hexagonal aplicada" \
  "Ejercicio guiado para implementar un caso de uso completo usando arquitectura hexagonal. Defines puertos, adaptadores y el nucleo de dominio paso a paso." 4)

L2B1=$(create_lesson "$MOD2B" "Que son los microservicios" \
  "Un microservicio es una unidad de despliegue independiente que implementa una capacidad de negocio especifica. Se comunica mediante REST o mensajeria asincrona. Cada servicio tiene su propia base de datos." 1)
L2B2=$(create_lesson "$MOD2B" "Comunicacion asincrona con RabbitMQ" \
  "RabbitMQ implementa AMQP 0-9-1. Los productores publican mensajes a exchanges; los consumers los reciben de colas vinculadas. Los exchanges de tipo topic enrutan por patron con comodines (* y #)." 2)
L2B3=$(create_lesson "$MOD2B" "Resiliencia y Circuit Breaker" \
  "El Circuit Breaker evita cascadas de fallos. Estado Closed: deja pasar llamadas. Si el ratio de error supera el umbral pasa a Open: rechaza sin llamar al servicio. Half-Open: deja pasar algunas llamadas de prueba." 3)
L2B_SUPP=$(create_supp_lesson "$MOD2B" "Practica extra: Circuit Breaker con Resilience4j" \
  "Implementacion practica del patron Circuit Breaker en Java usando la libreria Resilience4j. Configuracion de umbrales, fallbacks y manejo de timeouts." 4)

ok "Modulos y lecciones del Curso 2 creados"

# Contenidos Curso 2 — L2A1: TEXT + VIDEO
add_content "$L2A1" "Lectura: Comparativa de estilos arquitectonicos" "TEXT" "" \
  "Tabla comparativa con pros y contras de monolito, SOA y microservicios segun tamano de equipo y volumen de trafico."
add_content "$L2A1" "Video: Evolucion de las arquitecturas de software" "VIDEO" \
  "https://www.youtube.com/watch?v=2dKZ-dWaCiU" \
  "Charla tecnica sobre la evolucion de los estilos arquitectonicos y cuando conviene cada uno."

# L2A2: TEXT + PDF
add_content "$L2A2" "Lectura: Puertos y adaptadores en detalle" "TEXT" "" \
  "Explicacion profunda del modelo de puertos de entrada y salida, con ejemplos de adaptadores HTTP, JPA y de mensajeria."
add_content "$L2A2" "Guia de arquitectura hexagonal (PDF)" "PDF" \
  "https://alistair.cockburn.us/hexagonal-architecture.pdf" \
  "Articulo original de Alistair Cockburn donde describe la arquitectura de puertos y adaptadores."

# L2A3: TEXT + DOCUMENT
add_content "$L2A3" "Lectura: Patrones GoF en Java moderno" "TEXT" "" \
  "Como los patrones GoF se implementan en Java usando lambdas, Optional y streams de Java 8+."
add_content "$L2A3" "Catalogo de patrones de diseno (DOCX)" "DOCUMENT" \
  "https://file-examples.com/wp-content/uploads/2017/02/file-sample_100kB.docx" \
  "Catalogo con los 23 patrones GoF: descripcion, diagrama UML y ejemplo de codigo en Java."

# L2A_SUPP: TEXT only
add_content "$L2A_SUPP" "Lectura suplementaria: Caso de uso paso a paso" "TEXT" "" \
  "Ejercicio completo: implementa un servicio de reservas usando arquitectura hexagonal con puertos de entrada REST y salida JPA."

# L2B1: TEXT + VIDEO
add_content "$L2B1" "Lectura: Principios de diseno de microservicios" "TEXT" "" \
  "Los 5 principios clave: responsabilidad unica, base de datos por servicio, comunicacion via API, despliegue independiente y descubrimiento de servicios."
add_content "$L2B1" "Video: Microservicios desde cero" "VIDEO" \
  "https://www.youtube.com/watch?v=lL_j7ilk7rc" \
  "Implementacion practica de dos microservicios que se comunican via REST con Spring Boot."

# L2B2: TEXT + PDF
add_content "$L2B2" "Lectura: Exchanges y colas en RabbitMQ" "TEXT" "" \
  "Tipos de exchange (direct, fanout, topic, headers) con ejemplos de uso y patrones de enrutamiento de mensajes."
add_content "$L2B2" "Especificacion AMQP 0-9-1 (PDF)" "PDF" \
  "https://www.amqp.org/sites/amqp.org/files/amqp.pdf" \
  "Especificacion completa del protocolo AMQP 0-9-1 que implementa RabbitMQ."

# L2B3: TEXT + DOCUMENT
add_content "$L2B3" "Lectura: Patrones de resiliencia en microservicios" "TEXT" "" \
  "Circuit Breaker, Retry, Bulkhead y Timeout: cuando usar cada patron y como combinarlos para maxima resiliencia."
add_content "$L2B3" "Taller: Resiliencia con Resilience4j (DOCX)" "DOCUMENT" \
  "https://file-examples.com/wp-content/uploads/2017/02/file-sample_100kB.docx" \
  "Guia paso a paso para configurar Circuit Breaker, Retry y RateLimiter con Resilience4j en un servicio Java."

# L2B_SUPP: TEXT only
add_content "$L2B_SUPP" "Lectura suplementaria: Implementacion avanzada" "TEXT" "" \
  "Ejercicio avanzado: configura un Circuit Breaker real con Resilience4j, simula fallos y observa el comportamiento en cada estado."

ok "Contenidos del Curso 2 agregados (TEXT+VIDEO, TEXT+PDF, TEXT+DOCUMENT, TEXT)"

# ── Curso 3 — PostgreSQL ───────────────────────────────────────────────────────
MOD3A=$(create_module "$CID3" "SQL Fundamental" 1)
MOD3B=$(create_module "$CID3" "SQL Avanzado y Optimizacion" 2)

L3A1=$(create_lesson "$MOD3A" "Modelo relacional y DDL" \
  "El modelo relacional organiza datos en tablas con filas y columnas. DDL incluye: CREATE TABLE, ALTER TABLE y DROP TABLE. Las claves primarias identifican filas univocamente; las foraneas relacionan tablas." 1)
L3A2=$(create_lesson "$MOD3A" "Consultas SELECT y JOINs" \
  "SELECT recupera datos. INNER JOIN devuelve solo filas con coincidencia en ambas tablas. LEFT JOIN incluye todas las filas de la tabla izquierda aunque no haya coincidencia. WHERE filtra filas; ORDER BY ordena resultados." 2)
L3A3=$(create_lesson "$MOD3A" "Funciones de agregacion" \
  "COUNT, SUM, AVG, MIN y MAX operan sobre grupos de filas. GROUP BY agrupa; HAVING filtra grupos despues de agregar. Ejemplo: SELECT dept, AVG(salary) FROM employees GROUP BY dept HAVING AVG(salary) > 50000;" 3)
L3A_SUPP=$(create_supp_lesson "$MOD3A" "Practica extra: subconsultas y GROUP BY avanzado" \
  "Ejercicios de refuerzo sobre subconsultas correlacionadas, HAVING con multiples condiciones y combinacion de JOINs con agregaciones." 4)

L3B1=$(create_lesson "$MOD3B" "Indices y rendimiento" \
  "Los indices B-tree aceleran busquedas al costo de escrituras mas lentas. EXPLAIN ANALYZE muestra el plan real de ejecucion. Un indice compuesto (col1, col2) cubre consultas que filtran por col1 o por (col1, col2)." 1)
L3B2=$(create_lesson "$MOD3B" "Transacciones ACID" \
  "Atomicidad: la transaccion es todo o nada. Consistencia: la BD queda en estado valido. Isolation: transacciones concurrentes no se interfieren. Durabilidad: los cambios persisten tras COMMIT. Usa BEGIN / COMMIT / ROLLBACK." 2)
L3B3=$(create_lesson "$MOD3B" "Window Functions" \
  "Las window functions operan sobre ventanas de filas sin colapsar el resultado. ROW_NUMBER() asigna numero unico. RANK() asigna rango con saltos en empates. LAG(col, n) y LEAD(col, n) acceden a filas anteriores o posteriores." 3)
L3B_SUPP=$(create_supp_lesson "$MOD3B" "Practica extra: Window Functions y analisis de datos" \
  "Ejercicios avanzados de analisis de datos con Window Functions: calculos de totales acumulados, percentiles, medias moviles y ranking por particion." 4)

ok "Modulos y lecciones del Curso 3 creados"

# Contenidos Curso 3 — L3A1: TEXT + VIDEO
add_content "$L3A1" "Lectura: El modelo relacional de Codd" "TEXT" "" \
  "Principios del modelo relacional: relaciones, tuplas, dominios y las 12 reglas de Codd que definen un RDBMS verdadero."
add_content "$L3A1" "Video: PostgreSQL desde cero" "VIDEO" \
  "https://www.youtube.com/watch?v=HXV3zeQKqGY" \
  "Curso introductorio a PostgreSQL: instalacion, creacion de bases de datos y primeras tablas."

# L3A2: TEXT + PDF
add_content "$L3A2" "Lectura: Tipos de JOIN con ejemplos visuales" "TEXT" "" \
  "Diagramas de Venn para INNER, LEFT, RIGHT y FULL OUTER JOIN con ejemplos SQL y casos de uso tipicos."
add_content "$L3A2" "Documentacion oficial de PostgreSQL 14 (PDF)" "PDF" \
  "https://www.postgresql.org/files/documentation/pdf/14/postgresql-14-A4.pdf" \
  "Documentacion completa de PostgreSQL 14 en formato PDF: comandos SQL, tipos de datos y funciones integradas."

# L3A3: TEXT + DOCUMENT
add_content "$L3A3" "Lectura: Agregaciones eficientes en PostgreSQL" "TEXT" "" \
  "Tecnicas de optimizacion para consultas con GROUP BY: uso de indices parciales, materialized views y aggregate pushdown."
add_content "$L3A3" "Ejercicios SQL: Agregaciones (DOCX)" "DOCUMENT" \
  "https://file-examples.com/wp-content/uploads/2017/02/file-sample_100kB.docx" \
  "25 ejercicios de SQL con GROUP BY, HAVING y funciones de agregacion sobre una base de datos de ventas."

# L3A_SUPP: TEXT only
add_content "$L3A_SUPP" "Lectura suplementaria: Subconsultas avanzadas" "TEXT" "" \
  "Guia de subconsultas: correlacionadas vs no correlacionadas, EXISTS vs IN, y cuales son mas eficientes en PostgreSQL."

# L3B1: TEXT + VIDEO
add_content "$L3B1" "Lectura: Estrategias de indexacion en PostgreSQL" "TEXT" "" \
  "Tipos de indice: B-tree, Hash, GiST, GIN y BRIN. Cuando usar cada uno y como medir su impacto con EXPLAIN ANALYZE."
add_content "$L3B1" "Video: Optimizacion de consultas en PostgreSQL" "VIDEO" \
  "https://www.youtube.com/watch?v=Ven-UbVBVNM" \
  "Tecnicas avanzadas de optimizacion: lectura de planes de ejecucion, indices parciales y statistics."

# L3B2: TEXT + PDF
add_content "$L3B2" "Lectura: Niveles de aislamiento en PostgreSQL" "TEXT" "" \
  "Los cuatro niveles ANSI: Read Uncommitted, Read Committed, Repeatable Read y Serializable. Como PostgreSQL implementa MVCC."
add_content "$L3B2" "PostgreSQL: Concurrencia y MVCC (PDF)" "PDF" \
  "https://www.postgresql.org/files/documentation/pdf/14/postgresql-14-A4.pdf" \
  "Capitulos de la documentacion oficial sobre control de concurrencia, MVCC y niveles de aislamiento de transacciones."

# L3B3: TEXT + DOCUMENT
add_content "$L3B3" "Lectura: Guia de Window Functions" "TEXT" "" \
  "Referencia rapida de todas las window functions de PostgreSQL: ROW_NUMBER, RANK, DENSE_RANK, LAG, LEAD, FIRST_VALUE, LAST_VALUE."
add_content "$L3B3" "Taller: Analisis de datos con Window Functions (DOCX)" "DOCUMENT" \
  "https://file-examples.com/wp-content/uploads/2017/02/file-sample_100kB.docx" \
  "Taller practico: resuelve 15 problemas de analisis de datos usando Window Functions sobre un dataset de ventas real."

# L3B_SUPP: TEXT only
add_content "$L3B_SUPP" "Lectura suplementaria: Analisis avanzado con SQL" "TEXT" "" \
  "Ejercicios de analisis de series temporales, calculos de percentiles y rankings jerarquicos usando Window Functions en PostgreSQL."

ok "Contenidos del Curso 3 agregados (TEXT+VIDEO, TEXT+PDF, TEXT+DOCUMENT, TEXT)"

# ══════════════════════════════════════════════════════════════════════════════
step "7 — Publicar cursos"

for CID in "$CID1" "$CID2" "$CID3"; do
  curl -sf -X POST "$COURSE_SVC/courses/$CID/publish" \
    -H "Authorization: Bearer $INSTR_TOKEN" > /dev/null
  ok "Curso $CID publicado"
done

# ══════════════════════════════════════════════════════════════════════════════
step "8 — Inscripciones (10 x COURSE_ENROLLED)"

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

# Curso 1 (Java):         Maria, Juan, Andres              → 3 inscripciones
enroll "$T_MARIA"  "$CID1" "Maria"
enroll "$T_JUAN"   "$CID1" "Juan"
enroll "$T_ANDRES" "$CID1" "Andres"

# Curso 2 (Arquitecturas): Maria, Sofia, Valentina          → 3 inscripciones
enroll "$T_MARIA"  "$CID2" "Maria"
enroll "$T_SOFIA"  "$CID2" "Sofia"
enroll "$T_VALENT" "$CID2" "Valentina"

# Curso 3 (PostgreSQL):   Juan, Sofia, Valentina, Andres    → 4 inscripciones
enroll "$T_JUAN"   "$CID3" "Juan"
enroll "$T_SOFIA"  "$CID3" "Sofia"
enroll "$T_VALENT" "$CID3" "Valentina"
enroll "$T_ANDRES" "$CID3" "Andres"

analytics_wait "totalEnrollments" 10
analytics_wait "totalCourses"      3

# ══════════════════════════════════════════════════════════════════════════════
step "9 — Completar lecciones (LESSON_COMPLETED)"
# Maria completa TODAS las lecciones de Java (100%) para demo de finalizacion
# Sofia completa TODAS las lecciones de Arquitecturas (100%) para demo de finalizacion
# Juan completa todo el Modulo 1 de Java (para tomar evaluacion y fallarla → suplementaria)
# Andres completa todo el Modulo 1 de PostgreSQL (para tomar evaluacion y fallarla → suplementaria)

complete_lesson() {
  local token=$1 lid=$2 name=$3
  curl -s -o /dev/null -X POST "$COURSE_SVC/lessons/$lid/complete" \
    -H "Authorization: Bearer $token" || true
  ok "$name completo leccion $lid"
}

# Maria — Java (100%: 6 lecciones regulares) + Arquitecturas parcial
complete_lesson "$T_MARIA" "$L1A1" "Maria"
complete_lesson "$T_MARIA" "$L1A2" "Maria"
complete_lesson "$T_MARIA" "$L1A3" "Maria"
complete_lesson "$T_MARIA" "$L1B1" "Maria"
complete_lesson "$T_MARIA" "$L1B2" "Maria"
complete_lesson "$T_MARIA" "$L1B3" "Maria"
complete_lesson "$T_MARIA" "$L2A1" "Maria"
complete_lesson "$T_MARIA" "$L2A2" "Maria"

# Juan — Java Modulo 1 completo (podra tomar AID1A; fallara → suplementaria) + PostgreSQL parcial
complete_lesson "$T_JUAN" "$L1A1" "Juan"
complete_lesson "$T_JUAN" "$L1A2" "Juan"
complete_lesson "$T_JUAN" "$L1A3" "Juan"
complete_lesson "$T_JUAN" "$L3A1" "Juan"
complete_lesson "$T_JUAN" "$L3A2" "Juan"

# Sofia — Arquitecturas (100%: 6 lecciones regulares) + PostgreSQL parcial
complete_lesson "$T_SOFIA" "$L2A1" "Sofia"
complete_lesson "$T_SOFIA" "$L2A2" "Sofia"
complete_lesson "$T_SOFIA" "$L2A3" "Sofia"
complete_lesson "$T_SOFIA" "$L2B1" "Sofia"
complete_lesson "$T_SOFIA" "$L2B2" "Sofia"
complete_lesson "$T_SOFIA" "$L2B3" "Sofia"
complete_lesson "$T_SOFIA" "$L3A1" "Sofia"
complete_lesson "$T_SOFIA" "$L3B1" "Sofia"

# Andres — Java parcial + PostgreSQL Modulo 1 completo (podra tomar AID3A; fallara → suplementaria)
complete_lesson "$T_ANDRES" "$L1B1" "Andres"
complete_lesson "$T_ANDRES" "$L3A1" "Andres"
complete_lesson "$T_ANDRES" "$L3A2" "Andres"
complete_lesson "$T_ANDRES" "$L3A3" "Andres"
complete_lesson "$T_ANDRES" "$L3B1" "Andres"

# Valentina — parcial en Arquitecturas y PostgreSQL
complete_lesson "$T_VALENT" "$L2A2" "Valentina"
complete_lesson "$T_VALENT" "$L2B2" "Valentina"
complete_lesson "$T_VALENT" "$L3B2" "Valentina"

# ══════════════════════════════════════════════════════════════════════════════
step "10 — Evaluaciones: 6 vinculadas a lecciones + 3 de cierre de curso"

# Helper: extrae respuestas CORRECTAS del JSON de la evaluacion creada
extract_answers() {
  python3 -c "
import sys, json
d = json.load(sys.stdin)
answers = {}
for q in d['questions']:
    correct = [o['id'] for o in q['options'] if o.get('correct', False)]
    if correct:
        answers[q['id']] = correct
print(json.dumps(answers))
"
}

# Helper: extrae la PRIMERA respuesta INCORRECTA de cada pregunta (para simular fallo)
extract_wrong_answers() {
  python3 -c "
import sys, json
d = json.load(sys.stdin)
answers = {}
for q in d['questions']:
    wrong = [o['id'] for o in q['options'] if not o.get('correct', False)]
    if wrong:
        answers[q['id']] = [wrong[0]]
print(json.dumps(answers))
"
}

# ──────────────────────────────────────────────────────────────────────────────
# EVALUACIONES VINCULADAS A LECCIONES (lessonId = ultima leccion regular del modulo)
# Cada una tiene 3 preguntas y passingScorePct=60 (necesita >= 2/3 para aprobar)
# ──────────────────────────────────────────────────────────────────────────────

info "Creando AID1A — Evaluacion de Modulo 1 Java (vinculada a L1A3)..."
ASSESS1A_JSON=$(curl -sf -X POST "$ASSESS_SVC/assessments" \
  -H "Authorization: Bearer $INSTR_TOKEN" \
  -H "Content-Type: application/json" \
  -d @- <<EOF
{
  "title":          "Prueba: Estructuras de control en Java",
  "courseId":       "$CID1",
  "lessonId":       "$L1A3",
  "description":    "Evalua la comprension de variables, tipos y estructuras de control basicas en Java.",
  "passingScorePct": 60.0,
  "maxAttempts":    3,
  "questions": [
    {
      "text":   "En Java, cual es el resultado de la expresion 10 % 3?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "3",    "correct": false },
        { "text": "1",    "correct": true  },
        { "text": "0",    "correct": false },
        { "text": "3.33", "correct": false }
      ]
    },
    {
      "text":   "Cual estructura de control en Java itera sobre cada elemento de una coleccion?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "while",    "correct": false },
        { "text": "do-while", "correct": false },
        { "text": "for-each", "correct": true  },
        { "text": "switch",   "correct": false }
      ]
    },
    {
      "text":   "Cual tipo primitivo de Java almacena un valor verdadero o falso?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "int",     "correct": false },
        { "text": "byte",    "correct": false },
        { "text": "char",    "correct": false },
        { "text": "boolean", "correct": true  }
      ]
    }
  ]
}
EOF
)
AID1A=$(jval "$ASSESS1A_JSON" "d['id']")
ANSWERS1A=$(echo "$ASSESS1A_JSON" | extract_answers)
WRONG1A=$(echo "$ASSESS1A_JSON"   | extract_wrong_answers)
ok "AID1A creada (id=$AID1A) — vinculada a L1A3 (Java Mod1)"

info "Creando AID1B — Evaluacion de Modulo 2 Java (vinculada a L1B3)..."
ASSESS1B_JSON=$(curl -sf -X POST "$ASSESS_SVC/assessments" \
  -H "Authorization: Bearer $INSTR_TOKEN" \
  -H "Content-Type: application/json" \
  -d @- <<EOF
{
  "title":          "Prueba: POO — Herencia e Interfaces",
  "courseId":       "$CID1",
  "lessonId":       "$L1B3",
  "description":    "Evalua la comprension de herencia, polimorfismo e interfaces en Java.",
  "passingScorePct": 60.0,
  "maxAttempts":    3,
  "questions": [
    {
      "text":   "Cual palabra clave se usa en Java para heredar de una clase padre?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "implements", "correct": false },
        { "text": "inherits",   "correct": false },
        { "text": "extends",    "correct": true  },
        { "text": "super",      "correct": false }
      ]
    },
    {
      "text":   "Una interfaz en Java puede contener metodos con implementacion desde cual version?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "Java 5",  "correct": false },
        { "text": "Java 8",  "correct": true  },
        { "text": "Java 11", "correct": false },
        { "text": "Java 17", "correct": false }
      ]
    },
    {
      "text":   "Cual es la diferencia principal entre una clase abstracta y una interfaz en Java?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "Una interfaz puede tener estado (campos)",                        "correct": false },
        { "text": "Una clase abstracta puede heredar de multiples clases",           "correct": false },
        { "text": "Una clase abstracta puede tener metodos concretos y abstractos",  "correct": true  },
        { "text": "No hay diferencia en Java moderno",                               "correct": false }
      ]
    }
  ]
}
EOF
)
AID1B=$(jval "$ASSESS1B_JSON" "d['id']")
ANSWERS1B=$(echo "$ASSESS1B_JSON" | extract_answers)
ok "AID1B creada (id=$AID1B) — vinculada a L1B3 (Java Mod2)"

info "Creando AID2A — Evaluacion de Modulo 1 Arquitecturas (vinculada a L2A3)..."
ASSESS2A_JSON=$(curl -sf -X POST "$ASSESS_SVC/assessments" \
  -H "Authorization: Bearer $INSTR_TOKEN" \
  -H "Content-Type: application/json" \
  -d @- <<EOF
{
  "title":          "Prueba: Estilos arquitectonicos y patrones GoF",
  "courseId":       "$CID2",
  "lessonId":       "$L2A3",
  "description":    "Evalua el conocimiento de estilos arquitectonicos, arquitectura hexagonal y patrones GoF.",
  "passingScorePct": 60.0,
  "maxAttempts":    3,
  "questions": [
    {
      "text":   "En arquitectura hexagonal, que contiene el nucleo de la aplicacion?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "La base de datos",           "correct": false },
        { "text": "Los frameworks HTTP",        "correct": false },
        { "text": "Las reglas de negocio",      "correct": true  },
        { "text": "Los adaptadores de entrada", "correct": false }
      ]
    },
    {
      "text":   "Cual patron GoF notifica automaticamente a varios objetos cuando cambia el estado de un sujeto?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "Factory",   "correct": false },
        { "text": "Singleton", "correct": false },
        { "text": "Observer",  "correct": true  },
        { "text": "Command",   "correct": false }
      ]
    },
    {
      "text":   "Cual estilo arquitectonico facilita el escalado independiente de cada componente?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "Monolito",       "correct": false },
        { "text": "Microservicios", "correct": true  },
        { "text": "SOA clasico",    "correct": false },
        { "text": "MVC",            "correct": false }
      ]
    }
  ]
}
EOF
)
AID2A=$(jval "$ASSESS2A_JSON" "d['id']")
ANSWERS2A=$(echo "$ASSESS2A_JSON" | extract_answers)
ok "AID2A creada (id=$AID2A) — vinculada a L2A3 (Arq Mod1)"

info "Creando AID2B — Evaluacion de Modulo 2 Arquitecturas (vinculada a L2B3)..."
ASSESS2B_JSON=$(curl -sf -X POST "$ASSESS_SVC/assessments" \
  -H "Authorization: Bearer $INSTR_TOKEN" \
  -H "Content-Type: application/json" \
  -d @- <<EOF
{
  "title":          "Prueba: Microservicios y resiliencia",
  "courseId":       "$CID2",
  "lessonId":       "$L2B3",
  "description":    "Evalua la comprension de microservicios, RabbitMQ y el patron Circuit Breaker.",
  "passingScorePct": 60.0,
  "maxAttempts":    3,
  "questions": [
    {
      "text":   "En que estado pasa un Circuit Breaker cuando supera el umbral de errores?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "Closed",    "correct": false },
        { "text": "Half-Open", "correct": false },
        { "text": "Open",      "correct": true  },
        { "text": "Broken",    "correct": false }
      ]
    },
    {
      "text":   "En RabbitMQ, que componente enruta los mensajes del productor a las colas?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "Queue",      "correct": false },
        { "text": "Exchange",   "correct": true  },
        { "text": "Consumer",   "correct": false },
        { "text": "Connection", "correct": false }
      ]
    },
    {
      "text":   "Cual es la principal ventaja de la comunicacion asincrona en microservicios?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "Menor latencia que REST",                "correct": false },
        { "text": "Desacoplamiento temporal entre servicios","correct": true  },
        { "text": "Transacciones distribuidas mas simples",  "correct": false },
        { "text": "Menor uso de red",                       "correct": false }
      ]
    }
  ]
}
EOF
)
AID2B=$(jval "$ASSESS2B_JSON" "d['id']")
ANSWERS2B=$(echo "$ASSESS2B_JSON" | extract_answers)
ok "AID2B creada (id=$AID2B) — vinculada a L2B3 (Arq Mod2)"

info "Creando AID3A — Evaluacion de Modulo 1 PostgreSQL (vinculada a L3A3)..."
ASSESS3A_JSON=$(curl -sf -X POST "$ASSESS_SVC/assessments" \
  -H "Authorization: Bearer $INSTR_TOKEN" \
  -H "Content-Type: application/json" \
  -d @- <<EOF
{
  "title":          "Prueba: SQL basico y agregaciones",
  "courseId":       "$CID3",
  "lessonId":       "$L3A3",
  "description":    "Evalua el manejo de DDL, JOINs y funciones de agregacion en PostgreSQL.",
  "passingScorePct": 60.0,
  "maxAttempts":    3,
  "questions": [
    {
      "text":   "Cual clausula SQL filtra grupos despues de una agregacion?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "WHERE",    "correct": false },
        { "text": "HAVING",   "correct": true  },
        { "text": "GROUP BY", "correct": false },
        { "text": "ORDER BY", "correct": false }
      ]
    },
    {
      "text":   "Cual tipo de JOIN devuelve SOLO las filas con coincidencias en ambas tablas?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "LEFT JOIN",       "correct": false },
        { "text": "FULL OUTER JOIN", "correct": false },
        { "text": "INNER JOIN",      "correct": true  },
        { "text": "CROSS JOIN",      "correct": false }
      ]
    },
    {
      "text":   "Que funcion de agregacion devuelve el numero de filas en un grupo?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "SUM()",   "correct": false },
        { "text": "AVG()",   "correct": false },
        { "text": "COUNT()", "correct": true  },
        { "text": "MAX()",   "correct": false }
      ]
    }
  ]
}
EOF
)
AID3A=$(jval "$ASSESS3A_JSON" "d['id']")
ANSWERS3A=$(echo "$ASSESS3A_JSON" | extract_answers)
WRONG3A=$(echo "$ASSESS3A_JSON"   | extract_wrong_answers)
ok "AID3A creada (id=$AID3A) — vinculada a L3A3 (PG Mod1)"

info "Creando AID3B — Evaluacion de Modulo 2 PostgreSQL (vinculada a L3B3)..."
ASSESS3B_JSON=$(curl -sf -X POST "$ASSESS_SVC/assessments" \
  -H "Authorization: Bearer $INSTR_TOKEN" \
  -H "Content-Type: application/json" \
  -d @- <<EOF
{
  "title":          "Prueba: Indices, transacciones y Window Functions",
  "courseId":       "$CID3",
  "lessonId":       "$L3B3",
  "description":    "Evalua el conocimiento de indices, ACID y window functions avanzadas en PostgreSQL.",
  "passingScorePct": 60.0,
  "maxAttempts":    3,
  "questions": [
    {
      "text":   "Cual window function asigna un numero de fila unico dentro de cada particion?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "RANK()",       "correct": false },
        { "text": "DENSE_RANK()", "correct": false },
        { "text": "ROW_NUMBER()", "correct": true  },
        { "text": "COUNT(*)",     "correct": false }
      ]
    },
    {
      "text":   "En ACID, que garantiza la propiedad de Aislamiento (Isolation)?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "Los cambios son permanentes tras COMMIT",               "correct": false },
        { "text": "La transaccion es toda o nada",                        "correct": false },
        { "text": "Las transacciones concurrentes no se ven entre si",    "correct": true  },
        { "text": "La BD queda en estado valido tras cada transaccion",   "correct": false }
      ]
    },
    {
      "text":   "Que comando muestra el plan de ejecucion real (con tiempos) en PostgreSQL?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "SHOW PLAN",       "correct": false },
        { "text": "DESCRIBE",        "correct": false },
        { "text": "EXPLAIN ANALYZE", "correct": true  },
        { "text": "QUERY PLAN",      "correct": false }
      ]
    }
  ]
}
EOF
)
AID3B=$(jval "$ASSESS3B_JSON" "d['id']")
ANSWERS3B=$(echo "$ASSESS3B_JSON" | extract_answers)
ok "AID3B creada (id=$AID3B) — vinculada a L3B3 (PG Mod2)"

# ──────────────────────────────────────────────────────────────────────────────
# EVALUACIONES DE CIERRE DE CURSO (sin lessonId — curso completo)
# ──────────────────────────────────────────────────────────────────────────────

info "Creando AID1 — Evaluacion final: Fundamentos Java..."
ASSESS1_JSON=$(curl -sf -X POST "$ASSESS_SVC/assessments" \
  -H "Authorization: Bearer $INSTR_TOKEN" \
  -H "Content-Type: application/json" \
  -d @- <<EOF
{
  "title":          "Quiz final: Pilares de la POO",
  "courseId":       "$CID1",
  "description":    "Evaluacion de cierre del curso — comprension integral de los principios OOP en Java.",
  "passingScorePct": 60.0,
  "maxAttempts":    3,
  "questions": [
    {
      "text":   "Cual principio de la POO permite ocultar la implementacion interna de un objeto?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "Herencia",        "correct": false },
        { "text": "Encapsulamiento", "correct": true  },
        { "text": "Polimorfismo",    "correct": false },
        { "text": "Abstraccion",     "correct": false }
      ]
    },
    {
      "text":   "Cual coleccion de Java no permite elementos duplicados?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "ArrayList",  "correct": false },
        { "text": "LinkedList", "correct": false },
        { "text": "HashSet",    "correct": true  },
        { "text": "ArrayDeque", "correct": false }
      ]
    },
    {
      "text":   "En Java con tipos enteros, cual es el resultado de 10 / 3?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "3.33",                     "correct": false },
        { "text": "3",                        "correct": true  },
        { "text": "4",                        "correct": false },
        { "text": "Error de compilacion",     "correct": false }
      ]
    },
    {
      "text":   "Que palabra clave llama al constructor de la clase padre en Java?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "parent()",  "correct": false },
        { "text": "this()",    "correct": false },
        { "text": "super()",   "correct": true  },
        { "text": "base()",    "correct": false }
      ]
    }
  ]
}
EOF
)
AID1=$(jval "$ASSESS1_JSON" "d['id']")
ANSWERS1=$(echo "$ASSESS1_JSON" | extract_answers)
ok "AID1 final creada (id=$AID1) — Curso Java"

info "Creando AID2 — Evaluacion final: Arquitecturas de Software..."
ASSESS2_JSON=$(curl -sf -X POST "$ASSESS_SVC/assessments" \
  -H "Authorization: Bearer $INSTR_TOKEN" \
  -H "Content-Type: application/json" \
  -d @- <<EOF
{
  "title":          "Quiz final: Microservicios y Patrones",
  "courseId":       "$CID2",
  "description":    "Evaluacion de cierre — comprension de estilos arquitectonicos y patrones de diseno.",
  "passingScorePct": 60.0,
  "maxAttempts":    3,
  "questions": [
    {
      "text":   "Cual es la principal ventaja de los microservicios sobre el monolito?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "Menor latencia de red",                   "correct": false },
        { "text": "Escalado independiente por servicio",     "correct": true  },
        { "text": "Menor complejidad operacional",           "correct": false },
        { "text": "Transacciones distribuidas mas faciles",  "correct": false }
      ]
    },
    {
      "text":   "Que patron GoF encapsula una accion como objeto para diferirla o deshacerla?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "Observer",  "correct": false },
        { "text": "Strategy",  "correct": false },
        { "text": "Command",   "correct": true  },
        { "text": "Singleton", "correct": false }
      ]
    },
    {
      "text":   "En arquitectura hexagonal, como se llama el elemento que traduce entre el dominio y el mundo exterior?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "Servicio",   "correct": false },
        { "text": "Adaptador",  "correct": true  },
        { "text": "Repositorio","correct": false },
        { "text": "Entidad",    "correct": false }
      ]
    },
    {
      "text":   "En que estado un Circuit Breaker deja pasar solo algunas llamadas de prueba?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "Open",      "correct": false },
        { "text": "Closed",    "correct": false },
        { "text": "Half-Open", "correct": true  },
        { "text": "Broken",    "correct": false }
      ]
    }
  ]
}
EOF
)
AID2=$(jval "$ASSESS2_JSON" "d['id']")
ANSWERS2=$(echo "$ASSESS2_JSON" | extract_answers)
ok "AID2 final creada (id=$AID2) — Curso Arquitecturas"

info "Creando AID3 — Evaluacion final: PostgreSQL..."
ASSESS3_JSON=$(curl -sf -X POST "$ASSESS_SVC/assessments" \
  -H "Authorization: Bearer $INSTR_TOKEN" \
  -H "Content-Type: application/json" \
  -d @- <<EOF
{
  "title":          "Quiz final: SQL Avanzado",
  "courseId":       "$CID3",
  "description":    "Evaluacion de cierre — JOINs, agregaciones, indices y transacciones ACID.",
  "passingScorePct": 65.0,
  "maxAttempts":    3,
  "questions": [
    {
      "text":   "Cual window function accede al valor de la fila anterior dentro de una particion?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "LEAD()",       "correct": false },
        { "text": "LAG()",        "correct": true  },
        { "text": "FIRST_VALUE()", "correct": false },
        { "text": "RANK()",       "correct": false }
      ]
    },
    {
      "text":   "Cual propiedad ACID garantiza que los cambios persisten incluso ante un fallo del sistema?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "Atomicidad",    "correct": false },
        { "text": "Consistencia",  "correct": false },
        { "text": "Aislamiento",   "correct": false },
        { "text": "Durabilidad",   "correct": true  }
      ]
    },
    {
      "text":   "Un indice compuesto en (col1, col2) es util para filtrar solo por col2 sin col1?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "Si, siempre",                                 "correct": false },
        { "text": "No, el indice no se usa en ese caso",         "correct": true  },
        { "text": "Si, si col2 es la mas selectiva",             "correct": false },
        { "text": "Depende del motor de base de datos",          "correct": false }
      ]
    },
    {
      "text":   "Cual clausula SQL se usa para revertir una transaccion?",
      "type":   "SINGLE_CHOICE",
      "points": 1.0,
      "options": [
        { "text": "ABORT",    "correct": false },
        { "text": "UNDO",     "correct": false },
        { "text": "ROLLBACK", "correct": true  },
        { "text": "REVERT",   "correct": false }
      ]
    }
  ]
}
EOF
)
AID3=$(jval "$ASSESS3_JSON" "d['id']")
ANSWERS3=$(echo "$ASSESS3_JSON" | extract_answers)
ok "AID3 final creada (id=$AID3) — Curso PostgreSQL"

# ══════════════════════════════════════════════════════════════════════════════
step "10b — Reglas adaptativas (6 reglas: una por modulo)"
# Si un estudiante obtiene < 60% en la evaluacion del modulo,
# se desbloquea la leccion suplementaria de ese modulo.

create_adaptive_rule() {
  local aid=$1 cid=$2 lid=$3 supp_lid=$4 msg=$5
  local http_code
  http_code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$ASSESS_SVC/adaptive-rules" \
    -H "Authorization: Bearer $INSTR_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"assessmentId\":\"$aid\",\"courseId\":\"$cid\",\"lessonId\":\"$lid\",\"scoreThresholdPct\":60.0,\"supplementaryLessonId\":\"$supp_lid\",\"message\":\"$msg\",\"active\":true}")
  case "$http_code" in
    201|200) ok "Regla adaptativa: AID=$aid → suplementaria=$supp_lid" ;;
    *)       info "Advertencia: regla adaptativa $aid devolvio HTTP $http_code" ;;
  esac
}

# Java Modulo 1: AID1A < 60% → desbloquea L1A_SUPP
create_adaptive_rule "$AID1A" "$CID1" "$L1A3" "$L1A_SUPP" \
  "Tu puntuacion en Estructuras de control fue baja. Te recomendamos el contenido de refuerzo antes de continuar."

# Java Modulo 2: AID1B < 60% → desbloquea L1B_SUPP
create_adaptive_rule "$AID1B" "$CID1" "$L1B3" "$L1B_SUPP" \
  "Tu puntuacion en POO fue baja. Revisa el material suplementario de herencia e interfaces."

# Arq Modulo 1: AID2A < 60% → desbloquea L2A_SUPP
create_adaptive_rule "$AID2A" "$CID2" "$L2A3" "$L2A_SUPP" \
  "Tu puntuacion en estilos arquitectonicos fue baja. Practica con el ejercicio de arquitectura hexagonal."

# Arq Modulo 2: AID2B < 60% → desbloquea L2B_SUPP
create_adaptive_rule "$AID2B" "$CID2" "$L2B3" "$L2B_SUPP" \
  "Tu puntuacion en microservicios fue baja. Refuerza con el taller de Circuit Breaker."

# PG Modulo 1: AID3A < 60% → desbloquea L3A_SUPP
create_adaptive_rule "$AID3A" "$CID3" "$L3A3" "$L3A_SUPP" \
  "Tu puntuacion en SQL basico fue baja. Practica subconsultas y GROUP BY avanzado antes de continuar."

# PG Modulo 2: AID3B < 60% → desbloquea L3B_SUPP
create_adaptive_rule "$AID3B" "$CID3" "$L3B3" "$L3B_SUPP" \
  "Tu puntuacion en Window Functions fue baja. Refuerza con el taller de analisis de datos."

ok "6 reglas adaptativas creadas"

# ══════════════════════════════════════════════════════════════════════════════
step "11 — Submissions (22 x ASSESSMENT_SUBMITTED)"
# Escenarios de demo:
#  ✓ Juan FALLA AID1A (Java Mod1) con respuestas incorrectas → L1A_SUPP se desbloquea
#  ✓ Andres FALLA AID3A (PG Mod1) con respuestas incorrectas → L3A_SUPP se desbloquea
#  ✓ Maria aprueba todas las evaluaciones de Java → puede finalizar el curso
#  ✓ Sofia aprueba todas las evaluaciones de Arq → puede finalizar el curso

submit() {
  local token=$1 aid=$2 answers=$3 name=$4 label=$5
  local payload="{\"answers\":$answers,\"durationSeconds\":240}"
  local http_code
  http_code=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    "$ASSESS_SVC/assessments/$aid/submit" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "$payload")
  case "$http_code" in
    200|201) ok "$name submitio '$label' → ASSESSMENT_SUBMITTED" ;;
    *)       info "$name — error $http_code al submitir '$label'" ;;
  esac
}

# ── Evaluaciones de modulo (lección vinculada) ─────────────────────────────────
# AID1A — Java Mod1: Juan FALLA (respuestas incorrectas), Maria APRUEBA
submit "$T_JUAN"   "$AID1A" "$WRONG1A"   "Juan"  "AID1A Java Mod1 [FALLA → L1A_SUPP desbloqueada]"
submit "$T_MARIA"  "$AID1A" "$ANSWERS1A" "Maria" "AID1A Java Mod1 [APRUEBA]"

# AID1B — Java Mod2: Maria APRUEBA, Andres APRUEBA
submit "$T_MARIA"  "$AID1B" "$ANSWERS1B" "Maria"  "AID1B Java Mod2 [APRUEBA]"
submit "$T_ANDRES" "$AID1B" "$ANSWERS1B" "Andres" "AID1B Java Mod2 [APRUEBA]"

# AID2A — Arq Mod1: Sofia APRUEBA, Maria APRUEBA
submit "$T_SOFIA"  "$AID2A" "$ANSWERS2A" "Sofia" "AID2A Arq Mod1 [APRUEBA]"
submit "$T_MARIA"  "$AID2A" "$ANSWERS2A" "Maria" "AID2A Arq Mod1 [APRUEBA]"

# AID2B — Arq Mod2: Sofia APRUEBA, Valentina APRUEBA
submit "$T_SOFIA"  "$AID2B" "$ANSWERS2B" "Sofia"     "AID2B Arq Mod2 [APRUEBA]"
submit "$T_VALENT" "$AID2B" "$ANSWERS2B" "Valentina" "AID2B Arq Mod2 [APRUEBA]"

# AID3A — PG Mod1: Andres FALLA (respuestas incorrectas), Juan APRUEBA
submit "$T_ANDRES" "$AID3A" "$WRONG3A"   "Andres" "AID3A PG Mod1 [FALLA → L3A_SUPP desbloqueada]"
submit "$T_JUAN"   "$AID3A" "$ANSWERS3A" "Juan"   "AID3A PG Mod1 [APRUEBA]"

# AID3B — PG Mod2: Valentina APRUEBA, Sofia APRUEBA
submit "$T_VALENT" "$AID3B" "$ANSWERS3B" "Valentina" "AID3B PG Mod2 [APRUEBA]"
submit "$T_SOFIA"  "$AID3B" "$ANSWERS3B" "Sofia"     "AID3B PG Mod2 [APRUEBA]"

# ── Evaluaciones de cierre de curso ──────────────────────────────────────────
# AID1 (Java final): Maria, Juan, Andres — todos con respuestas correctas
submit "$T_MARIA"  "$AID1" "$ANSWERS1" "Maria"  "AID1 Java final [APRUEBA]"
submit "$T_JUAN"   "$AID1" "$ANSWERS1" "Juan"   "AID1 Java final [APRUEBA]"
submit "$T_ANDRES" "$AID1" "$ANSWERS1" "Andres" "AID1 Java final [APRUEBA]"

# AID2 (Arq final): Maria, Sofia, Valentina — todos con respuestas correctas
submit "$T_MARIA"  "$AID2" "$ANSWERS2" "Maria"     "AID2 Arq final [APRUEBA]"
submit "$T_SOFIA"  "$AID2" "$ANSWERS2" "Sofia"     "AID2 Arq final [APRUEBA]"
submit "$T_VALENT" "$AID2" "$ANSWERS2" "Valentina" "AID2 Arq final [APRUEBA]"

# AID3 (PG final): Juan, Sofia, Valentina, Andres — todos con respuestas correctas
submit "$T_JUAN"   "$AID3" "$ANSWERS3" "Juan"      "AID3 PG final [APRUEBA]"
submit "$T_SOFIA"  "$AID3" "$ANSWERS3" "Sofia"     "AID3 PG final [APRUEBA]"
submit "$T_VALENT" "$AID3" "$ANSWERS3" "Valentina" "AID3 PG final [APRUEBA]"
submit "$T_ANDRES" "$AID3" "$ANSWERS3" "Andres"    "AID3 PG final [APRUEBA]"

# ── Checkpoint 3 ─────────────────────────────────────────────────────────────
analytics_wait "totalSubmissions" 20
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
  "Preguntas y debates sobre Java y programacion orientada a objetos." "$CID1")
ok "Foro 1 creado ($FID1)"

FID2=$(create_forum "Discusiones: Arquitecturas de Software" \
  "Debates sobre patrones de diseno, microservicios y arquitectura." "$CID2")
ok "Foro 2 creado ($FID2)"

FID3=$(create_forum "Discusiones: PostgreSQL" \
  "Dudas sobre SQL, optimizacion de consultas y modelado de datos." "$CID3")
ok "Foro 3 creado ($FID3)"

# ── Foro 1: Java ─────────────────────────────────────────────────────────────
TH1=$(create_thread "$FID1" \
  "Java permite herencia multiple?" \
  "Hola, lei que Java no permite herencia multiple pero no entiendo por que. Como lo resuelvo?" \
  "$T_MARIA")
reply_thread "$TH1" \
  "Java no permite herencia multiple de clases para evitar el problema del diamante. Si puedes implementar varias interfaces simultaneamente con 'implements A, B'." \
  "$INSTR_TOKEN"
reply_thread "$TH1" \
  "Y si dos interfaces tienen un metodo default con la misma firma?" \
  "$T_MARIA"
reply_thread "$TH1" \
  "Debes sobreescribirlo obligatoriamente en la clase que las implementa. El compilador te forzara a hacerlo." \
  "$T_JUAN"

TH2=$(create_thread "$FID1" \
  "Diferencia entre List y Set en Java" \
  "Cuando conviene usar List y cuando Set? El profesor menciono algo sobre duplicados." \
  "$T_JUAN")
reply_thread "$TH2" \
  "List mantiene el orden de insercion y permite duplicados. Set no permite duplicados y no garantiza orden (excepto TreeSet o LinkedHashSet). HashSet es el mas rapido para verificar si un elemento ya existe." \
  "$INSTR_TOKEN"
reply_thread "$TH2" \
  "Uso Set cuando necesito eliminar duplicados automaticamente, por ejemplo al consolidar IDs unicos de una lista que puede tener repetidos." \
  "$T_ANDRES"

# ── Foro 2: Arquitecturas ─────────────────────────────────────────────────────
TH3=$(create_thread "$FID2" \
  "Los microservicios siempre son la mejor opcion?" \
  "Estoy evaluando migrar un sistema a microservicios. Siempre vale la pena el esfuerzo?" \
  "$T_SOFIA")
reply_thread "$TH3" \
  "Depende del contexto. Los microservicios anaden complejidad operacional: service discovery, trazas distribuidas, consistencia eventual, multiples pipelines de despliegue. Para equipos pequenos, un monolito bien modularizado suele ser mas productivo." \
  "$INSTR_TOKEN"
reply_thread "$TH3" \
  "La recomendacion de Martin Fowler es: no empieces con microservicios; empieza con un monolito modular y extrae servicios cuando identifiques cuellos de botella reales de escalado." \
  "$T_VALENT"
reply_thread "$TH3" \
  "Buena referencia. Y cuando seria el momento correcto para dar el salto?" \
  "$T_SOFIA"
reply_thread "$TH3" \
  "Cuando un modulo especifico necesite escalar independientemente, o cuando equipos distintos necesiten desplegar sin coordinarse. Ese es el umbral practico." \
  "$INSTR_TOKEN"

# ── Foro 3: PostgreSQL ────────────────────────────────────────────────────────
TH4=$(create_thread "$FID3" \
  "Cuando usar indice compuesto vs indice simple?" \
  "Tengo muchas consultas por (usuario_id, fecha). Conviene un indice compuesto?" \
  "$T_ANDRES")
reply_thread "$TH4" \
  "Si. CREATE INDEX idx_user_date ON tabla(usuario_id, fecha). El indice compuesto cubre consultas que filtran por usuario_id solo, o por (usuario_id, fecha). El orden de las columnas importa: la primera columna es la mas selectiva." \
  "$INSTR_TOKEN"
reply_thread "$TH4" \
  "Y si a veces filtro solo por fecha sin usuario_id?" \
  "$T_ANDRES"
reply_thread "$TH4" \
  "En ese caso el indice compuesto (usuario_id, fecha) NO lo cubrira. Necesitas un indice adicional solo en (fecha) si esas consultas tambien son frecuentes." \
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
    200|201) ok "$name se unio al grupo $gid" ;;
    *)       info "$name — respuesta $http_code al unirse a $gid" ;;
  esac
}

GRP1=$(create_group "Equipo Java Avanzado"  "$CID1" "$T_MARIA")
GRP2=$(create_group "Club de Arquitecturas" "$CID2" "$T_SOFIA")
GRP3=$(create_group "SQL Masters"           "$CID3" "$T_JUAN")

[[ -n "$GRP1" ]] && { ok "Grupo 1 creado ($GRP1)"; join_group "$GRP1" "$T_JUAN" "Juan"; join_group "$GRP1" "$T_ANDRES" "Andres"; }
[[ -n "$GRP2" ]] && { ok "Grupo 2 creado ($GRP2)"; join_group "$GRP2" "$T_MARIA" "Maria"; join_group "$GRP2" "$T_VALENT" "Valentina"; }
[[ -n "$GRP3" ]] && { ok "Grupo 3 creado ($GRP3)"; join_group "$GRP3" "$T_SOFIA" "Sofia"; join_group "$GRP3" "$T_ANDRES" "Andres"; join_group "$GRP3" "$T_VALENT" "Valentina"; }

# ══════════════════════════════════════════════════════════════════════════════
step "Resumen final"

SUMMARY=$(curl -sf -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$ANALYTICS_API/dashboard/summary" 2>/dev/null || echo '{}')

echo ""
echo "══════════════════════════════════════════════════════════════════"
echo "  Seed completado — metricas en analytics-service:"
echo ""
echo "$SUMMARY" | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(f'  Usuarios registrados  : {d.get(\"totalUsers\", \"?\")}'  )
print(f'  Inscripciones         : {d.get(\"totalEnrollments\", \"?\")}'  )
print(f'  Cursos con actividad  : {d.get(\"totalCourses\", \"?\")}'  )
print(f'  Submissions           : {d.get(\"totalSubmissions\", \"?\")}'  )
print(f'  Puntuacion promedio   : {d.get(\"averageScore\", \"?\")}'  )
print(f'  Tasa de aprobacion    : {d.get(\"passRate\", \"?\")}'  )
" 2>/dev/null || echo "  (no se pudo obtener el resumen — verifica token y analytics)"
echo ""
echo "  Datos de demo relevantes:"
echo "    Maria (Java 100%)  → puede demostrar Finalizar Curso"
echo "    Sofia (Arq 100%)   → puede demostrar Finalizar Curso"
echo "    Juan               → fallo AID1A → L1A_SUPP desbloqueada en Java"
echo "    Andres             → fallo AID3A → L3A_SUPP desbloqueada en PostgreSQL"
echo ""
echo "  Contenidos por leccion:"
echo "    Leccion 1 de cada modulo : TEXT + VIDEO"
echo "    Leccion 2 de cada modulo : TEXT + PDF"
echo "    Leccion 3 de cada modulo : TEXT + DOCUMENT"
echo "    Lecciones suplementarias : TEXT"
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
