#!/usr/bin/env bash
# Pobla la plataforma con datos de demostración.
# Uso: bash scripts/seed-data.sh

set -euo pipefail

USER_SVC="http://localhost:8081/api/v1"
COURSE_SVC="http://localhost:8082/api/v1"
ASSESS_SVC="http://localhost:8083/api/v1"
COLLAB_SVC="http://localhost:8084/api/v1"

PSQL="docker exec -i puj-postgres psql -U puj_admin -d learning_platform"

ok()  { echo "[OK]  $*"; }
info(){ echo "[..] $*"; }
fail(){ echo "[ERR] $*" >&2; exit 1; }

# ─── 1. Promover admin a rol ADMIN ────────────────────────────────────────────
info "Promoviendo admin@puj.edu.co → ADMIN"
$PSQL -c "UPDATE users.users SET role='ADMIN' WHERE email='admin@puj.edu.co';" > /dev/null
ok "admin@puj.edu.co es ADMIN"

# ─── 2. Login como admin ──────────────────────────────────────────────────────
info "Autenticando admin..."
ADMIN_RESP=$(curl -sf -X POST "$USER_SVC/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@puj.edu.co","password":"Admin1234!"}')
ADMIN_TOKEN=$(echo "$ADMIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")
ADMIN_ID=$(echo "$ADMIN_RESP"    | python3 -c "import sys,json; print(json.load(sys.stdin)['user']['id'])")
ok "Token admin obtenido (id=$ADMIN_ID)"

auth_admin() { echo "Authorization: Bearer $ADMIN_TOKEN"; }

# ─── 3. Registrar instructor ──────────────────────────────────────────────────
info "Registrando instructor..."
curl -sf -X POST "$USER_SVC/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email":"prof.garcia@puj.edu.co","password":"Profesor1234!","firstName":"Carlos","lastName":"García","consentGiven":true}' \
  > /dev/null && ok "prof.garcia@puj.edu.co registrado" || info "prof.garcia ya existía"

INSTR_ID=$(docker exec -i puj-postgres psql -U puj_admin -d learning_platform -t -A \
  -c "SELECT id FROM users.users WHERE email='prof.garcia@puj.edu.co';")

info "Promoviendo prof.garcia → INSTRUCTOR"
$PSQL -c "UPDATE users.users SET role='INSTRUCTOR' WHERE email='prof.garcia@puj.edu.co';" > /dev/null

# Login como instructor
INSTR_RESP=$(curl -sf -X POST "$USER_SVC/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"prof.garcia@puj.edu.co","password":"Profesor1234!"}')
INSTR_TOKEN=$(echo "$INSTR_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")
ok "Token instructor obtenido (id=$INSTR_ID)"

auth_instr() { echo "Authorization: Bearer $INSTR_TOKEN"; }

# ─── 4. Registrar estudiantes ─────────────────────────────────────────────────
info "Registrando estudiantes..."
register_student() {
  local email=$1 pass=$2 first=$3 last=$4
  curl -sf -X POST "$USER_SVC/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\",\"password\":\"$pass\",\"firstName\":\"$first\",\"lastName\":\"$last\",\"consentGiven\":true}" \
    > /dev/null && ok "$email registrado" || info "$email ya existía"
}

register_student "maria.lopez@puj.edu.co"   "Estudiante1!" "María"    "López"
register_student "juan.perez@puj.edu.co"    "Estudiante1!" "Juan"     "Pérez"
register_student "sofia.ruiz@puj.edu.co"    "Estudiante1!" "Sofía"    "Ruiz"
register_student "andres.mora@puj.edu.co"   "Estudiante1!" "Andrés"   "Mora"
register_student "valentina.gil@puj.edu.co" "Estudiante1!" "Valentina" "Gil"

get_token() {
  curl -sf -X POST "$USER_SVC/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$1\",\"password\":\"Estudiante1!\"}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])"
}

T_MARIA=$(get_token "maria.lopez@puj.edu.co")
T_JUAN=$(get_token "juan.perez@puj.edu.co")
T_SOFIA=$(get_token "sofia.ruiz@puj.edu.co")
T_ANDRES=$(get_token "andres.mora@puj.edu.co")
T_VALENT=$(get_token "valentina.gil@puj.edu.co")
ok "Tokens de estudiantes obtenidos"

# ─── 5. Crear cursos ──────────────────────────────────────────────────────────
info "Creando cursos..."

create_course() {
  local title=$1 desc=$2 max=$3
  curl -sf -X POST "$COURSE_SVC/courses" \
    -H "Content-Type: application/json" \
    -H "$(auth_instr)" \
    -d "{\"title\":\"$title\",\"description\":\"$desc\",\"maxStudents\":$max}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])"
}

CID1=$(create_course \
  "Fundamentos de Programación con Java" \
  "Aprende los pilares de la programación orientada a objetos con Java 17: variables, estructuras de control, colecciones, POO y manejo de excepciones." \
  40)
ok "Curso 1: $CID1"

CID2=$(create_course \
  "Arquitecturas de Software Modernas" \
  "Microservicios, arquitectura hexagonal, patrones de diseño (Factory, Observer, Strategy) y comunicación asíncrona con mensajería." \
  35)
ok "Curso 2: $CID2"

CID3=$(create_course \
  "Bases de Datos Relacionales con PostgreSQL" \
  "Modelado de datos, SQL avanzado, índices, transacciones ACID, procedimientos almacenados y optimización de consultas." \
  30)
ok "Curso 3: $CID3"

CID4=$(create_course \
  "Desarrollo Web con Jakarta EE 10" \
  "Servlets, JSF 4, CDI, RESTEasy, JPA/Hibernate, seguridad con JWT y despliegue en WildFly." \
  25)
ok "Curso 4: $CID4"

# Crear un curso extra como admin
CID5=$(curl -sf -X POST "$COURSE_SVC/courses" \
  -H "Content-Type: application/json" \
  -H "$(auth_admin)" \
  -d '{"title":"Inteligencia Artificial y Aprendizaje Adaptativo","description":"Algoritmos de recomendación, sistemas adaptativos, procesamiento de lenguaje natural básico y evaluaciones inteligentes para plataformas educativas.","maxStudents":50}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
ok "Curso 5: $CID5"

# ─── 6. Publicar cursos ───────────────────────────────────────────────────────
info "Publicando cursos..."
for CID in $CID1 $CID2 $CID3 $CID4; do
  curl -sf -X POST "$COURSE_SVC/courses/$CID/publish" \
    -H "$(auth_instr)" > /dev/null && ok "Curso $CID publicado"
done
curl -sf -X POST "$COURSE_SVC/courses/$CID5/publish" \
  -H "$(auth_admin)" > /dev/null && ok "Curso $CID5 publicado"

# ─── 7. Inscribir estudiantes ─────────────────────────────────────────────────
info "Inscribiendo estudiantes..."

enroll() {
  local token=$1 course=$2 email=$3
  curl -sf -X POST "$COURSE_SVC/enrollments/courses/$course" \
    -H "Authorization: Bearer $token" > /dev/null \
    && ok "$email inscrito en $course" || info "$email ya inscrito"
}

enroll "$T_MARIA"  "$CID1" "maria"
enroll "$T_MARIA"  "$CID2" "maria"
enroll "$T_MARIA"  "$CID4" "maria"
enroll "$T_JUAN"   "$CID1" "juan"
enroll "$T_JUAN"   "$CID3" "juan"
enroll "$T_JUAN"   "$CID5" "juan"
enroll "$T_SOFIA"  "$CID2" "sofia"
enroll "$T_SOFIA"  "$CID3" "sofia"
enroll "$T_SOFIA"  "$CID4" "sofia"
enroll "$T_ANDRES" "$CID1" "andres"
enroll "$T_ANDRES" "$CID5" "andres"
enroll "$T_VALENT" "$CID2" "valentina"
enroll "$T_VALENT" "$CID3" "valentina"
enroll "$T_VALENT" "$CID5" "valentina"

# ─── 8. Insertar assessments + preguntas vía SQL ─────────────────────────────
info "Insertando evaluaciones y preguntas..."

# Instructor ID (trimmed)
INSTR_ID_TRIM=$(echo "$INSTR_ID" | tr -d '[:space:]')

$PSQL << SQL
-- Evaluación 1: Java Fundamentos
INSERT INTO assessments.assessments
  (id, title, course_id, instructor_id, description, max_attempts, passing_score_pct, randomize_questions, created_at, updated_at)
VALUES
  ('aaaaaaaa-0001-0001-0001-000000000001',
   'Quiz: Pilares de la POO', '$CID1', '$INSTR_ID_TRIM',
   'Evalúa tu comprensión de encapsulamiento, herencia, polimorfismo y abstracción.',
   3, 60.0, true, NOW(), NOW()),
  ('aaaaaaaa-0001-0001-0001-000000000002',
   'Examen Parcial: Java Colecciones', '$CID1', '$INSTR_ID_TRIM',
   'Listas, mapas, conjuntos y streams en Java 17.',
   2, 70.0, false, NOW(), NOW());

-- Evaluación 2: Arquitecturas
INSERT INTO assessments.assessments
  (id, title, course_id, instructor_id, description, max_attempts, passing_score_pct, randomize_questions, created_at, updated_at)
VALUES
  ('aaaaaaaa-0002-0002-0002-000000000001',
   'Quiz: Microservicios vs Monolito', '$CID2', '$INSTR_ID_TRIM',
   'Ventajas, desventajas y cuándo usar cada estilo arquitectónico.',
   3, 60.0, true, NOW(), NOW());

-- Evaluación 3: PostgreSQL
INSERT INTO assessments.assessments
  (id, title, course_id, instructor_id, description, max_attempts, passing_score_pct, randomize_questions, created_at, updated_at)
VALUES
  ('aaaaaaaa-0003-0003-0003-000000000001',
   'Quiz: SQL Avanzado', '$CID3', '$INSTR_ID_TRIM',
   'JOINs, subconsultas, window functions e índices.',
   3, 65.0, false, NOW(), NOW());

-- Preguntas: Quiz POO ─────────────────────────────────────────────────────────
INSERT INTO assessments.questions (id, assessment_id, text, type, points, order_index) VALUES
  ('bbbb0001-0001-0001-0001-000000000001', 'aaaaaaaa-0001-0001-0001-000000000001',
   '¿Cuál principio de la POO permite ocultar la implementación interna de un objeto?',
   'SINGLE_CHOICE', 1.0, 1),
  ('bbbb0001-0001-0001-0001-000000000002', 'aaaaaaaa-0001-0001-0001-000000000001',
   '¿Qué permite la herencia en la programación orientada a objetos?',
   'SINGLE_CHOICE', 1.0, 2),
  ('bbbb0001-0001-0001-0001-000000000003', 'aaaaaaaa-0001-0001-0001-000000000001',
   '¿El polimorfismo permite que un mismo método tenga diferentes comportamientos según el objeto que lo invoque?',
   'TRUE_FALSE', 1.0, 3),
  ('bbbb0001-0001-0001-0001-000000000004', 'aaaaaaaa-0001-0001-0001-000000000001',
   '¿Cuáles de los siguientes son pilares de la POO? (selecciona todos los correctos)',
   'MULTIPLE_CHOICE', 2.0, 4);

-- Opciones: Q1 POO
INSERT INTO assessments.answer_options (id, question_id, text, is_correct, order_index) VALUES
  (gen_random_uuid(), 'bbbb0001-0001-0001-0001-000000000001', 'Encapsulamiento', true,  1),
  (gen_random_uuid(), 'bbbb0001-0001-0001-0001-000000000001', 'Compilación',     false, 2),
  (gen_random_uuid(), 'bbbb0001-0001-0001-0001-000000000001', 'Iteración',        false, 3),
  (gen_random_uuid(), 'bbbb0001-0001-0001-0001-000000000001', 'Depuración',       false, 4);

-- Opciones: Q2 herencia
INSERT INTO assessments.answer_options (id, question_id, text, is_correct, order_index) VALUES
  (gen_random_uuid(), 'bbbb0001-0001-0001-0001-000000000002', 'Reutilizar código de una clase padre en clases hijas', true,  1),
  (gen_random_uuid(), 'bbbb0001-0001-0001-0001-000000000002', 'Ejecutar código en paralelo',                          false, 2),
  (gen_random_uuid(), 'bbbb0001-0001-0001-0001-000000000002', 'Conectarse a bases de datos',                          false, 3),
  (gen_random_uuid(), 'bbbb0001-0001-0001-0001-000000000002', 'Gestionar memoria automáticamente',                    false, 4);

-- Opciones: Q3 true/false
INSERT INTO assessments.answer_options (id, question_id, text, is_correct, order_index) VALUES
  (gen_random_uuid(), 'bbbb0001-0001-0001-0001-000000000003', 'Verdadero', true,  1),
  (gen_random_uuid(), 'bbbb0001-0001-0001-0001-000000000003', 'Falso',     false, 2);

-- Opciones: Q4 múltiple
INSERT INTO assessments.answer_options (id, question_id, text, is_correct, order_index) VALUES
  (gen_random_uuid(), 'bbbb0001-0001-0001-0001-000000000004', 'Encapsulamiento', true,  1),
  (gen_random_uuid(), 'bbbb0001-0001-0001-0001-000000000004', 'Herencia',        true,  2),
  (gen_random_uuid(), 'bbbb0001-0001-0001-0001-000000000004', 'Polimorfismo',    true,  3),
  (gen_random_uuid(), 'bbbb0001-0001-0001-0001-000000000004', 'Compilación',     false, 4);

-- Preguntas: Quiz Microservicios ──────────────────────────────────────────────
INSERT INTO assessments.questions (id, assessment_id, text, type, points, order_index) VALUES
  ('bbbb0002-0002-0002-0002-000000000001', 'aaaaaaaa-0002-0002-0002-000000000001',
   '¿Cuál es la principal ventaja de una arquitectura de microservicios frente a un monolito?',
   'SINGLE_CHOICE', 1.0, 1),
  ('bbbb0002-0002-0002-0002-000000000002', 'aaaaaaaa-0002-0002-0002-000000000001',
   '¿Los microservicios siempre deben compartir la misma base de datos?',
   'TRUE_FALSE', 1.0, 2),
  ('bbbb0002-0002-0002-0002-000000000003', 'aaaaaaaa-0002-0002-0002-000000000001',
   '¿Qué patrón permite que los servicios se comuniquen sin acoplamiento directo?',
   'SINGLE_CHOICE', 2.0, 3);

INSERT INTO assessments.answer_options (id, question_id, text, is_correct, order_index) VALUES
  (gen_random_uuid(), 'bbbb0002-0002-0002-0002-000000000001', 'Despliegue y escalado independiente por servicio', true,  1),
  (gen_random_uuid(), 'bbbb0002-0002-0002-0002-000000000001', 'Menor latencia de red',                            false, 2),
  (gen_random_uuid(), 'bbbb0002-0002-0002-0002-000000000001', 'Código más fácil de leer',                         false, 3),
  (gen_random_uuid(), 'bbbb0002-0002-0002-0002-000000000001', 'Sin necesidad de pruebas de integración',           false, 4);

INSERT INTO assessments.answer_options (id, question_id, text, is_correct, order_index) VALUES
  (gen_random_uuid(), 'bbbb0002-0002-0002-0002-000000000002', 'Verdadero', false, 1),
  (gen_random_uuid(), 'bbbb0002-0002-0002-0002-000000000002', 'Falso',     true,  2);

INSERT INTO assessments.answer_options (id, question_id, text, is_correct, order_index) VALUES
  (gen_random_uuid(), 'bbbb0002-0002-0002-0002-000000000003', 'Mensajería asíncrona (ej. RabbitMQ, Kafka)', true,  1),
  (gen_random_uuid(), 'bbbb0002-0002-0002-0002-000000000003', 'Llamadas directas a métodos',                false, 2),
  (gen_random_uuid(), 'bbbb0002-0002-0002-0002-000000000003', 'Variables globales compartidas',             false, 3),
  (gen_random_uuid(), 'bbbb0002-0002-0002-0002-000000000003', 'Archivos de texto plano',                    false, 4);

-- Preguntas: Quiz SQL ──────────────────────────────────────────────────────────
INSERT INTO assessments.questions (id, assessment_id, text, type, points, order_index) VALUES
  ('bbbb0003-0003-0003-0003-000000000001', 'aaaaaaaa-0003-0003-0003-000000000001',
   '¿Qué tipo de JOIN devuelve todos los registros de ambas tablas aunque no tengan coincidencia?',
   'SINGLE_CHOICE', 1.0, 1),
  ('bbbb0003-0003-0003-0003-000000000002', 'aaaaaaaa-0003-0003-0003-000000000001',
   '¿Una transacción ACID garantiza que si falla una operación, todas las demás del mismo bloque se revierten?',
   'TRUE_FALSE', 1.0, 2),
  ('bbbb0003-0003-0003-0003-000000000003', 'aaaaaaaa-0003-0003-0003-000000000001',
   '¿Qué función de ventana (window function) calcula el número de fila dentro de una partición?',
   'SINGLE_CHOICE', 2.0, 3);

INSERT INTO assessments.answer_options (id, question_id, text, is_correct, order_index) VALUES
  (gen_random_uuid(), 'bbbb0003-0003-0003-0003-000000000001', 'FULL OUTER JOIN', true,  1),
  (gen_random_uuid(), 'bbbb0003-0003-0003-0003-000000000001', 'INNER JOIN',      false, 2),
  (gen_random_uuid(), 'bbbb0003-0003-0003-0003-000000000001', 'LEFT JOIN',       false, 3),
  (gen_random_uuid(), 'bbbb0003-0003-0003-0003-000000000001', 'CROSS JOIN',      false, 4);

INSERT INTO assessments.answer_options (id, question_id, text, is_correct, order_index) VALUES
  (gen_random_uuid(), 'bbbb0003-0003-0003-0003-000000000002', 'Verdadero', true,  1),
  (gen_random_uuid(), 'bbbb0003-0003-0003-0003-000000000002', 'Falso',     false, 2);

INSERT INTO assessments.answer_options (id, question_id, text, is_correct, order_index) VALUES
  (gen_random_uuid(), 'bbbb0003-0003-0003-0003-000000000003', 'ROW_NUMBER()', true,  1),
  (gen_random_uuid(), 'bbbb0003-0003-0003-0003-000000000003', 'COUNT()',       false, 2),
  (gen_random_uuid(), 'bbbb0003-0003-0003-0003-000000000003', 'RANK()',        false, 3),
  (gen_random_uuid(), 'bbbb0003-0003-0003-0003-000000000003', 'SUM()',         false, 4);

SELECT 'assessments OK' AS status;
SQL
ok "Evaluaciones y preguntas insertadas"

# ─── 9. Crear foros ──────────────────────────────────────────────────────────
info "Creando foros..."

create_forum() {
  local course_id=$1 title=$2 token=$3
  curl -sf -X POST "$COLLAB_SVC/forums" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $token" \
    -d "{\"courseId\":\"$course_id\",\"title\":\"$title\"}" \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('id',''))" 2>/dev/null || echo ""
}

FID1=$(create_forum "$CID1" "Foro General — Programación Java" "$INSTR_TOKEN")
ok "Foro curso 1: $FID1"
FID2=$(create_forum "$CID2" "Foro General — Arquitecturas de Software" "$INSTR_TOKEN")
ok "Foro curso 2: $FID2"
FID3=$(create_forum "$CID3" "Foro General — PostgreSQL" "$INSTR_TOKEN")
ok "Foro curso 3: $FID3"

# ─── 10. Crear hilos en los foros ────────────────────────────────────────────
info "Creando hilos y posts en foros..."

create_thread() {
  local forum_id=$1 title=$2 content=$3 token=$4
  curl -sf -X POST "$COLLAB_SVC/forums/$forum_id/threads" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $token" \
    -d "{\"title\":\"$title\",\"content\":\"$content\"}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || echo ""
}

reply_thread() {
  local thread_id=$1 content=$2 token=$3
  curl -sf -X POST "$COLLAB_SVC/forums/threads/$thread_id/posts" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $token" \
    -d "{\"content\":\"$content\"}" > /dev/null 2>&1 || true
}

if [ -n "$FID1" ]; then
  TH1=$(create_thread "$FID1" \
    "¿Cuándo usar interfaz vs clase abstracta?" \
    "Hola a todos! Estoy confundido sobre cuándo es mejor usar una interfaz y cuándo una clase abstracta. ¿Alguien puede explicarlo con un ejemplo práctico?" \
    "$T_MARIA")
  [ -n "$TH1" ] && reply_thread "$TH1" \
    "Gran pregunta María. La regla práctica: usa interfaz cuando defines un contrato que múltiples clases sin relación pueden implementar (ej. Serializable, Comparable). Usa clase abstracta cuando tienes código común que quieres reutilizar y las subclases sí tienen una relación 'es-un'." \
    "$INSTR_TOKEN"
  [ -n "$TH1" ] && reply_thread "$TH1" \
    "Yo lo recuerdo así: si necesitas herencia múltiple → interfaz. Si necesitas compartir implementación → clase abstracta. En Java 8+ las interfaces también pueden tener métodos default, lo que difumina un poco la línea." \
    "$T_JUAN"

  TH2=$(create_thread "$FID1" \
    "Duda con NullPointerException en colecciones" \
    "Me sale NullPointerException al iterar una lista. La inicialicé como List<String> lista; sin asignar valor. ¿El problema es ese?" \
    "$T_ANDRES")
  [ -n "$TH2" ] && reply_thread "$TH2" \
    "Exacto Andrés. Declarar List<String> lista; solo crea la referencia, su valor es null. Debes inicializarla: List<String> lista = new ArrayList<>(); Luego ya puedes iterar sin problema." \
    "$INSTR_TOKEN"
fi

if [ -n "$FID2" ]; then
  TH3=$(create_thread "$FID2" \
    "¿Event sourcing es obligatorio en microservicios?" \
    "El profesor mencionó event sourcing pero no queda claro si es un requisito en arquitecturas de microservicios o solo una opción avanzada." \
    "$T_SOFIA")
  [ -n "$TH3" ] && reply_thread "$TH3" \
    "No es obligatorio, Sofía. Event sourcing es un patrón adicional que añade auditabilidad completa almacenando todos los eventos que cambiaron el estado, en lugar de solo el estado actual. Es muy útil para dominios financieros o con requisitos de auditoría, pero añade complejidad. Puedes tener microservicios perfectamente funcionales sin él." \
    "$INSTR_TOKEN"
fi

if [ -n "$FID3" ]; then
  TH4=$(create_thread "$FID3" \
    "Diferencia entre TRUNCATE y DELETE" \
    "En clase vimos ambos comandos para borrar datos. ¿Cuándo conviene usar uno u otro?" \
    "$T_VALENT")
  [ -n "$TH4" ] && reply_thread "$TH4" \
    "DELETE es DML: puede ir dentro de una transacción, activa triggers y genera registros en el WAL fila a fila. TRUNCATE es DDL: más rápido porque borra todo la tabla de golpe, no activa triggers de fila, pero no se puede usar con WHERE. Si necesitas borrar selectivamente → DELETE. Si quieres vaciar toda la tabla rápido → TRUNCATE." \
    "$INSTR_TOKEN"
  [ -n "$TH4" ] && reply_thread "$TH4" \
    "Añado: TRUNCATE también reinicia las secuencias si usas RESTART IDENTITY. Muy útil en tests para limpiar datos de prueba." \
    "$T_JUAN"
fi

ok "Foros, hilos y posts creados"

# ─── 11. Crear grupos de estudio ─────────────────────────────────────────────
info "Creando grupos de estudio..."

create_group() {
  local name=$1 course_id=$2 max=$3 token=$4
  curl -sf -X POST "$COLLAB_SVC/groups" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $token" \
    -d "{\"name\":\"$name\",\"courseId\":\"$course_id\",\"maxMembers\":$max}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || echo ""
}

join_group() {
  local group_id=$1 token=$2
  curl -sf -X POST "$COLLAB_SVC/groups/$group_id/join" \
    -H "Authorization: Bearer $token" > /dev/null 2>&1 || true
}

GID1=$(create_group "Grupo A — Java Básico" "$CID1" 8 "$T_MARIA")
GID2=$(create_group "Grupo B — Java Avanzado" "$CID1" 8 "$T_JUAN")
GID3=$(create_group "Equipo Arquitectura" "$CID2" 6 "$T_SOFIA")

[ -n "$GID1" ] && join_group "$GID1" "$T_ANDRES" && ok "Andrés se unió a Grupo A"
[ -n "$GID1" ] && join_group "$GID1" "$T_SOFIA"  && ok "Sofía se unió a Grupo A"
[ -n "$GID2" ] && join_group "$GID2" "$T_VALENT" && ok "Valentina se unió a Grupo B"
[ -n "$GID3" ] && join_group "$GID3" "$T_MARIA"  && ok "María se unió a Equipo Arquitectura"
[ -n "$GID3" ] && join_group "$GID3" "$T_VALENT" && ok "Valentina se unió a Equipo Arquitectura"

ok "Grupos de estudio creados"

# ─── Resumen ──────────────────────────────────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════════════════"
echo " Seed completado"
echo "═══════════════════════════════════════════════════════"
echo " Usuarios:"
echo "   admin@puj.edu.co          Admin1234!   (ADMIN)"
echo "   prof.garcia@puj.edu.co    Profesor1234! (INSTRUCTOR)"
echo "   maria.lopez@puj.edu.co    Estudiante1!  (STUDENT)"
echo "   juan.perez@puj.edu.co     Estudiante1!  (STUDENT)"
echo "   sofia.ruiz@puj.edu.co     Estudiante1!  (STUDENT)"
echo "   andres.mora@puj.edu.co    Estudiante1!  (STUDENT)"
echo "   valentina.gil@puj.edu.co  Estudiante1!  (STUDENT)"
echo ""
echo " Cursos (5 publicados con inscripciones)"
echo " Evaluaciones (4 quizzes con preguntas y opciones)"
echo " Foros (3) con hilos y respuestas"
echo " Grupos de estudio (3) con miembros"
echo "═══════════════════════════════════════════════════════"
echo " Web UI: http://localhost:8080"
echo "═══════════════════════════════════════════════════════"
