# Manual de ejecución y pruebas
**Plataforma de Aprendizaje Adaptativo y Colaborativo — PUJ 2026**

---

## Tabla de contenidos

1. [Requisitos previos](#1-requisitos-previos)
2. [Configuración inicial (solo una vez)](#2-configuración-inicial-solo-una-vez)
3. [Correr en local (Docker Compose)](#3-correr-en-local-docker-compose)
4. [Ver la UI web](#4-ver-la-ui-web)
5. [Inspeccionar la base de datos](#5-inspeccionar-la-base-de-datos)
6. [Correr en Kubernetes (EKS)](#6-correr-en-kubernetes-eks)
7. [Probar el sistema comando a comando](#7-probar-el-sistema-comando-a-comando)
8. [Ejecutar los tests unitarios](#8-ejecutar-los-tests-unitarios)
9. [Mapa de puertos (local)](#9-mapa-de-puertos-local)

---

## 1. Requisitos previos

| Herramienta | Versión mínima | Para qué |
|---|---|---|
| Docker + Docker Compose | 24 / 2.24 | Entorno local |
| Java (JDK) | 17 | Compilar los servicios Java si no usas Docker |
| Maven | 3.9 | Build Java fuera de Docker |
| .NET SDK | 8.0 | Compilar analytics-service si no usas Docker |
| kubectl | 1.28 | Desplegar en Kubernetes |
| AWS CLI | 2.x | Login a ECR para producción |
| openssl | cualquiera | Generar par RSA para JWT (lo usa setup-env.sh) |

---

## 2. Configuración inicial (solo una vez)

Este paso genera las claves RSA para JWT y crea el archivo `.env` con todas las variables que Docker Compose necesita. **Solo hay que ejecutarlo una vez** — después de esto, `docker compose up` funciona directamente sin exportar nada.

```bash
# Desde la raíz del repositorio
bash scripts/setup-env.sh
```

El script hace tres cosas:

1. Copia `.env.example` → `.env` (si `.env` no existe todavía).
2. Genera un par RSA 2048 con `openssl` y escribe las claves en `.env` en una sola línea (escapando los saltos de línea).
3. Imprime qué variables todavía necesitan llenarse a mano.

La salida se ve así:

```
=== PUJ Learning Platform — Setup inicial ===
✓ .env creado desde .env.example
  Generando par RSA 2048...
✓ Claves RSA generadas y escritas en .env

══════════════════════════════════════════════════════════
  Rellena manualmente en .env antes de hacer docker compose up:

  ✗ AWS_ACCESS_KEY_ID  ← pendiente
  ✗ AWS_SECRET_ACCESS_KEY  ← pendiente
  ✗ REGISTRY  ← pendiente
  ✓ SMTP_HOST
  ✗ SMTP_USER  ← pendiente
  ✗ SMTP_PASSWORD  ← pendiente
══════════════════════════════════════════════════════════
```

Las variables de AWS y SMTP son opcionales para desarrollo local:

- **AWS**: solo necesaria para el upload de materiales a S3. Sin ella, los endpoints de upload fallan pero todo lo demás funciona.
- **REGISTRY**: solo necesaria para hacer push a ECR. En local no se usa.
- **SMTP**: en local el email-service usa MailHog automáticamente (SMTP_HOST no tiene efecto si el contenedor apunta a `mailhog:1025` internamente).

Si en algún momento necesitas regenerar las claves (ej: rotación de seguridad), borra las líneas `JWT_PRIVATE_KEY` y `JWT_PUBLIC_KEY` del `.env` y vuelve a ejecutar el script.

> `.env`, `jwt_private.pem` y `jwt_public.pem` están en `.gitignore` — nunca se suben al repositorio.

---

## 3. Correr en local (Docker Compose)

### 3.1 Levantar infraestructura primero

```bash
docker compose up -d postgres redis rabbitmq mailhog
```

Esperar a que los healthchecks pasen (entre 15 y 30 segundos):

```bash
docker compose ps
# Todos deben mostrar "(healthy)" o "running"
```

### 3.2 Levantar los servicios

```bash
docker compose up -d user-service course-service assessment-service \
                     collaboration-service analytics-service email-service web-ui
```

Los servicios Java (WildFly) tardan ~60 segundos en arrancar. Puedes seguir el log de uno:

```bash
docker compose logs -f user-service
# Busca: "WildFly Full 31.0.0.Final started"
```

### 3.3 Verificar que todo está vivo

```bash
# user-service
curl -s http://localhost:8081/api/v1/auth/me | jq
# → { "error": "UNAUTHORIZED", ... }  (esperado, no hay token)

# analytics-service
curl -s http://localhost:8085/health | jq
# → { "status": "UP" }

# email-service
curl -s http://localhost:8086/health | jq
# → { "status": "UP" }

# UI web
open http://localhost:8080        # macOS
xdg-open http://localhost:8080   # Linux
```

### 3.4 Apagar todo

```bash
docker compose down            # para los contenedores, conserva volúmenes
docker compose down -v         # para Y borra los datos (postgres, redis, rabbit)
```

---

## 4. Ver la UI web

La UI está en **http://localhost:8080**. Es una aplicación JSF (server-side rendering): el servidor genera el HTML completo, el browser no llama a ningún API directamente.

### Roles y qué ve cada uno

Hay cuatro roles en el sistema. Lo que se muestra en pantalla cambia completamente según el rol.

#### STUDENT (el flujo principal)

1. Ve al formulario de registro en `/register.xhtml`.
2. Rellena email, contraseña y **marca la casilla de consentimiento** (obligatoria por Ley 1581 — sin ella el botón no habilitará el envío).
3. Tras registrarse, abre **http://localhost:8025** (MailHog) — llegó el correo de bienvenida con el aviso de privacidad.
4. Inicia sesión en `/login.xhtml` → redirige a `/dashboard.xhtml`.
5. Dashboard de estudiante muestra:
   - Cursos inscritos con su progreso (% de lecciones completadas).
   - Evaluaciones pendientes.
   - Notificaciones del motor adaptativo (si hay recomendaciones de refuerzo).
6. Desde `/courses.xhtml` puede explorar el catálogo e inscribirse. El correo de confirmación de inscripción llega en segundos a MailHog.
7. Al completar una evaluación con nota < umbral configurado, aparece un banner amarillo con el material de refuerzo recomendado.
8. Desde `/groups.xhtml` puede crear o unirse a grupos de estudio y chatear en tiempo real.

#### INSTRUCTOR

1. Registra cuenta y pide a un ADMIN que cambie su rol (o el ADMIN lo crea directamente).
2. Inicia sesión → `/instructor/dashboard.xhtml`:
   - Lista de cursos que imparte.
   - Botón "Nuevo curso" → formulario con título, descripción y capacidad máxima.
3. Dentro de un curso puede agregar módulos, lecciones y evaluaciones.
4. Para cada evaluación configura el umbral adaptativo: el porcentaje mínimo y qué lección mostrar si el estudiante no lo alcanza.

#### DIRECTOR

1. Solo puede ver, no modificar cursos ni usuarios.
2. Su página de inicio es `/analytics/dashboard.xhtml`:
   - Tarjetas con totales: usuarios activos, inscripciones, tasa de aprobación.
   - Tabla de cursos más populares.
   - Botones para descargar CSV y PDF de los reportes.

#### ADMIN

1. Acceso a `/admin/users.xhtml`:
   - Lista de todos los usuarios con su rol actual.
   - Puede cambiar el rol de cualquier usuario (STUDENT ↔ INSTRUCTOR ↔ DIRECTOR ↔ ADMIN).
   - Puede desbloquear cuentas bloqueadas por intentos fallidos.

### URLs de la UI

```
http://localhost:8080/register.xhtml               → Registro con consentimiento
http://localhost:8080/login.xhtml                  → Inicio de sesión
http://localhost:8080/dashboard.xhtml              → Dashboard según rol
http://localhost:8080/courses.xhtml                → Catálogo de cursos
http://localhost:8080/groups.xhtml                 → Grupos de estudio y chat
http://localhost:8080/analytics/dashboard.xhtml    → Solo DIRECTOR/ADMIN
http://localhost:8080/admin/users.xhtml            → Solo ADMIN
```

### Ver los correos que llegan

Cada acción importante dispara un correo. En desarrollo local todos caen en **MailHog** (http://localhost:8025):

| Acción | Template |
|---|---|
| Registro | Bienvenida + aviso Ley 1581 |
| Inscripción en curso | Confirmación de matrícula |
| Evaluación calificada | Nota + recomendación adaptativa (si aplica) |
| Completar curso | Felicitaciones + constancia |
| Solicitud de reset de contraseña | Enlace de reset |

---

## 5. Inspeccionar la base de datos

PostgreSQL corre en `localhost:5432`. Usuario `puj_admin`, contraseña `postgres_secret` (como está en `.env`).

### Conectarse desde la terminal

```bash
# Opción 1: psql dentro del contenedor (sin instalar psql localmente)
docker compose exec postgres psql -U puj_admin -d puj_platform

# Opción 2: psql local si lo tienes instalado
psql -h localhost -p 5432 -U puj_admin -d puj_platform
```

### Schemas y tablas principales

La base de datos tiene 5 schemas, uno por dominio:

```sql
-- Ver todos los schemas
\dn

-- Cambiar al schema de usuarios
SET search_path TO users;
\dt   -- lista las tablas
```

### Consultas útiles para verificar que el sistema funciona

**Usuarios registrados y sus roles:**

```sql
SELECT id, email, role, consent_given, created_at,
       failed_login_attempts, locked_until
FROM users.users
ORDER BY created_at DESC;
```

**Verificar que el consentimiento se guardó (Ley 1581):**

```sql
SELECT email, consent_given, consent_given_at
FROM users.users
WHERE consent_given = false;
-- Este resultado debe estar vacío: no se permite registrar sin consentimiento
```

**Inscripciones activas:**

```sql
SELECT u.email, c.title AS curso, e.enrolled_at, e.completed_at
FROM courses.enrollments e
JOIN users.users u ON u.id = e.student_id
JOIN courses.courses c ON c.id = e.course_id
WHERE e.deleted_at IS NULL
ORDER BY e.enrolled_at DESC;
```

**Evaluaciones y notas:**

```sql
SELECT u.email, a.title AS evaluacion, s.score, s.passed, s.submitted_at
FROM assessments.submissions s
JOIN users.users u ON u.id = s.student_id
JOIN assessments.assessments a ON a.id = s.assessment_id
WHERE s.deleted_at IS NULL
ORDER BY s.submitted_at DESC;
```

**Ver reglas adaptativas configuradas:**

```sql
SELECT a.title AS evaluacion, r.threshold_pct, r.action, r.target_lesson_id
FROM assessments.adaptive_rules r
JOIN assessments.assessments a ON a.id = r.assessment_id
WHERE r.deleted_at IS NULL;
```

**Mensajes de chat en grupos de estudio:**

```sql
SELECT g.name AS grupo, m.author_email, m.content, m.sent_at
FROM collaboration.chat_messages m
JOIN collaboration.study_groups g ON g.id = m.group_id
ORDER BY m.sent_at DESC
LIMIT 20;
```

**Métricas de analítica acumuladas:**

```sql
SELECT c.title AS curso, m.metric_type, m.metric_value, m.recorded_at
FROM analytics.course_metrics m
JOIN courses.courses c ON c.id = m.course_id
ORDER BY m.recorded_at DESC
LIMIT 20;
```

**Verificar soft-delete (ningún registro se borra físicamente):**

```sql
SELECT
  COUNT(*) AS total,
  COUNT(*) FILTER (WHERE deleted_at IS NULL) AS activos,
  COUNT(*) FILTER (WHERE deleted_at IS NOT NULL) AS borrados
FROM users.users;
```

### Conectarse a Redis (cache y blacklist de tokens)

```bash
docker compose exec redis redis-cli -a redis_secret

# Ver las claves del motor adaptativo en cache
KEYS adaptive:rule:*

# Ver tokens en blacklist (JTIs después de logout)
KEYS jwt:blacklist:*

# Ver TTL de una clave específica
TTL adaptive:rule:<assessment-uuid>
```

### Ver las colas de RabbitMQ

```bash
# Interfaz web completa
open http://localhost:15672
# Usuario: puj_rabbit / Contraseña: rabbit_secret

# Ver estado de colas desde CLI
docker compose exec rabbitmq rabbitmqctl list_queues name messages consumers
```

| Cola | Qué contiene |
|---|---|
| `email.notifications` | Correos pendientes de enviar |
| `analytics.results` | Eventos de calificaciones para agregar |
| `dead.letter.queue` | Mensajes que fallaron después de 3 reintentos |

---

## 6. Correr en Kubernetes (EKS)

### 6.1 Build y push de imágenes

Asegúrate de tener `REGISTRY` configurado en `.env`:

```bash
# Editar .env y llenar:
# REGISTRY=123456789.dkr.ecr.us-east-1.amazonaws.com
```

Luego un solo comando construye y sube las 7 imágenes:

```bash
bash scripts/ecr-push.sh
```

Para publicar con un tag específico (ej: para una release):

```bash
bash scripts/ecr-push.sh v1.2.0
```

El script hace automáticamente:
- Login a ECR con `aws ecr get-login-password` (extrae la región del URL del registry).
- Build de las 7 imágenes con `--build-arg BUILDKIT_INLINE_CACHE=1`.
- Push con el tag especificado y también con `:latest`.
- Sustituye el placeholder `REGISTRY` en todos los `deployment.yaml` de `infra/k8s/`.

### 6.2 Crear secretos reales (no commitear en git)

```bash
kubectl create secret generic platform-secrets \
  --namespace puj-platform \
  --from-literal=DB_USER=puj_admin \
  --from-literal=DB_PASSWORD=tu_password \
  --from-literal=REDIS_PASSWORD=tu_redis_pass \
  --from-literal=RABBITMQ_USER=puj_rabbit \
  --from-literal=RABBITMQ_PASSWORD=tu_rabbit_pass \
  --from-literal=JWT_PRIVATE_KEY="$(cat jwt_private.pem)" \
  --from-literal=JWT_PUBLIC_KEY="$(cat jwt_public.pem)" \
  --from-literal=AWS_ACCESS_KEY_ID=AKIA... \
  --from-literal=AWS_SECRET_ACCESS_KEY=... \
  --from-literal=SMTP_HOST=smtp.puj.edu.co \
  --from-literal=SMTP_PORT=587 \
  --from-literal=SMTP_USER=no-reply@puj.edu.co \
  --from-literal=SMTP_PASSWORD=tu_smtp_pass
```

> `jwt_private.pem` y `jwt_public.pem` los generó `setup-env.sh` en la raíz del repositorio.

### 6.3 Desplegar todo con un solo comando

```bash
kubectl apply -k infra/k8s/
```

El orden de aplicación lo controla `kustomization.yaml`:
namespace → secrets → configmaps → postgres (con PgBouncer) → redis → rabbitmq → servicios → ingress.

### 6.4 Verificar el despliegue

```bash
# Ver que todos los pods arranquen
kubectl get pods -n puj-platform -w

# Ver el estado de los HPAs
kubectl get hpa -n puj-platform

# Ver la IP del load balancer (puede tardar 1-2 min en asignarse)
kubectl get ingress -n puj-platform

# Logs de un servicio
kubectl logs -n puj-platform deployment/user-service --follow
```

---

## 7. Probar el sistema comando a comando

Todos los ejemplos apuntan al entorno local (Docker Compose).

### 7.1 Registro e inicio de sesión

```bash
# Registrarse (el consentimiento es obligatorio por Ley 1581)
curl -s -X POST http://localhost:8081/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "juan@puj.edu.co",
    "password": "MiPassword123!",
    "firstName": "Juan",
    "lastName": "Alba",
    "consentGiven": true
  }' | jq

# Login
TOKEN=$(curl -s -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"juan@puj.edu.co","password":"MiPassword123!"}' \
  | jq -r '.accessToken')

echo $TOKEN
```

Después del registro, abre **http://localhost:8025** (MailHog) y verás el correo de bienvenida.

### 7.2 Verificar bloqueo por intentos fallidos

```bash
# 5 intentos con contraseña incorrecta
for i in {1..5}; do
  curl -s -X POST http://localhost:8081/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"juan@puj.edu.co","password":"incorrecta"}' | jq .message
done

# El 5to intento bloquea la cuenta por 15 minutos
curl -s -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"juan@puj.edu.co","password":"MiPassword123!"}' | jq
# → "Cuenta bloqueada por intentos fallidos..."
```

### 7.3 Crear un curso e inscribirse

```bash
# Crear curso (requiere rol INSTRUCTOR)
curl -s -X POST http://localhost:8082/api/v1/courses \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Arquitectura de Software",
    "description": "Patrones, estilos y decisiones arquitectónicas",
    "maxStudents": 30
  }' | jq

COURSE_ID=$(curl -s http://localhost:8082/api/v1/courses \
  | jq -r '.[0].id')

# Inscribirse
curl -s -X POST "http://localhost:8082/api/v1/enrollments/courses/$COURSE_ID" \
  -H "Authorization: Bearer $TOKEN" | jq

# Ver el correo de confirmación en MailHog
open http://localhost:8025
```

### 7.4 Probar el motor adaptativo

```bash
ASSESSMENT_ID=<uuid-de-una-evaluacion>

# Crear una regla: si el estudiante saca menos de 60%, ver lección de refuerzo
curl -s -X POST http://localhost:8083/api/v1/adaptive-rules \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "assessmentId": "'"$ASSESSMENT_ID"'",
    "thresholdPct": 60,
    "action": "SUPPLEMENTARY_LESSON",
    "targetLessonId": "<uuid-leccion-refuerzo>"
  }' | jq

# Iniciar y enviar una evaluación
SUBMISSION_ID=$(curl -s -X POST http://localhost:8083/api/v1/submissions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"assessmentId":"'"$ASSESSMENT_ID"'"}' | jq -r '.id')

curl -s -X POST "http://localhost:8083/api/v1/submissions/$SUBMISSION_ID/submit" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"answers":[]}' | jq
# Si la nota < 60 → response incluye adaptiveRecommendation con la lección de refuerzo
```

Verificar el cache Redis (TTL 60s):

```bash
docker compose exec redis redis-cli -a redis_secret \
  KEYS "adaptive:rule:*"

# Invalidar el cache manualmente
docker compose exec redis redis-cli -a redis_secret \
  DEL "adaptive:rule:$ASSESSMENT_ID"
```

### 7.5 Chat en tiempo real (WebSocket)

```bash
# Instalar wscat si no lo tienes
npm install -g wscat

# Terminal 1
wscat -c "ws://localhost:8084/ws/groups/<group-id>?token=$TOKEN"

# Terminal 2 (con otro TOKEN de otro usuario)
wscat -c "ws://localhost:8084/ws/groups/<group-id>?token=$TOKEN2"

# Escribir en terminal 1 y ver cómo aparece en terminal 2 en tiempo real
```

### 7.6 Dashboard de analítica

```bash
# Requiere rol DIRECTOR o ADMIN
curl -s "http://localhost:8085/api/v1/analytics/dashboard/summary" \
  -H "Authorization: Bearer $DIRECTOR_TOKEN" | jq

curl -s "http://localhost:8085/api/v1/analytics/dashboard/top-courses" \
  -H "Authorization: Bearer $DIRECTOR_TOKEN" | jq
```

### 7.7 Exportar reportes

```bash
# CSV de cursos
curl -s "http://localhost:8085/api/v1/analytics/export/courses.csv" \
  -H "Authorization: Bearer $DIRECTOR_TOKEN" \
  -o cursos.csv && cat cursos.csv

# PDF de cursos
curl -s "http://localhost:8085/api/v1/analytics/export/courses.pdf" \
  -H "Authorization: Bearer $DIRECTOR_TOKEN" \
  -o informe_cursos.pdf && open informe_cursos.pdf

# CSV de submissions filtrado por curso y rango de fechas
curl -s "http://localhost:8085/api/v1/analytics/export/submissions.csv?\
courseId=$COURSE_ID&from=2026-01-01&to=2026-12-31" \
  -H "Authorization: Bearer $DIRECTOR_TOKEN" \
  -o submissions.csv
```

### 7.8 Ver correos enviados (MailHog)

```bash
# Interfaz web
open http://localhost:8025

# API REST de MailHog para scripting
curl -s http://localhost:8025/api/v2/messages | jq '.items[0].Content.Headers.Subject'
```

### 7.9 Ver colas de RabbitMQ

```bash
open http://localhost:15672
# Usuario: puj_rabbit / Contraseña: rabbit_secret
# Ir a Queues → ver mensajes pendientes en analytics.results y email.notifications
```

### 7.10 Simular fallo de Redis (fallback adaptativo)

```bash
# Parar Redis
docker compose stop redis

# Hacer una petición que usa el motor adaptativo
# → El servicio debe responder y el log debe mostrar: WARNING: Redis unavailable, fallback to DB
docker compose logs assessment-service | grep "WARNING\|Redis"

# Restaurar
docker compose start redis
```

---

## 8. Ejecutar los tests unitarios

### Todos los módulos Java de una vez

```bash
mvn test
```

### Solo un módulo

```bash
mvn test -pl libs/security
mvn test -pl services/user-service
mvn test -pl services/assessment-service
mvn test -pl services/email-service
```

### Analytics (.NET)

```bash
cd services/analytics-service
dotnet test
```

### Ver qué tests existen

| Módulo | Test | Qué cubre |
|---|---|---|
| `libs/security` | `JwtProviderTest` | Round-trip de token, token expirado, token manipulado |
| `services/user-service` | `AuthServiceTest` | Registro sin consentimiento, email duplicado, login incorrecto, logout blacklistea JTI |
| `services/assessment-service` | `AdaptiveEngineTest` | Nota bajo umbral → recomendación, Redis caído → fallback a DB |
| `services/email-service` | `EmailTemplateRendererTest` | Templates HTML: Ley 1581 en bienvenida, colores por resultado, interpolación |

---

## 9. Mapa de puertos (local)

| Puerto | Servicio | URL |
|---|---|---|
| 8080 | web-ui (JSF) | http://localhost:8080 |
| 8081 | user-service | http://localhost:8081/api/v1 |
| 8082 | course-service | http://localhost:8082/api/v1 |
| 8083 | assessment-service | http://localhost:8083/api/v1 |
| 8084 | collaboration-service + WebSocket | http://localhost:8084/api/v1, ws://localhost:8084/ws |
| 8085 | analytics-service | http://localhost:8085/api/v1 |
| 8086 | email-service | http://localhost:8086/health |
| 8025 | MailHog UI | http://localhost:8025 |
| 15672 | RabbitMQ Management | http://localhost:15672 |
| 5432 | PostgreSQL (directo, solo debug) | localhost:5432 |
| 6379 | Redis (directo, solo debug) | localhost:6379 |
| 5672 | RabbitMQ AMQP | localhost:5672 |

> En producción (Kubernetes), ninguno de estos puertos es accesible desde afuera.
> Todo el tráfico externo entra únicamente por NGINX en `platform.puj.edu.co:443`,
> que enruta exclusivamente hacia `web-ui`. Los servicios de negocio son ClusterIP
> — inaccesibles desde internet por diseño de red, no solo por configuración.
