# Manual de ejecución y pruebas
**Plataforma de Aprendizaje Adaptativo y Colaborativo — PUJ 2026**

---

## Tabla de contenidos

1. [Requisitos previos](#1-requisitos-previos)
2. [Configuración inicial (solo una vez)](#2-configuración-inicial-solo-una-vez)
3. [Correr en local (Docker Compose)](#3-correr-en-local-docker-compose)
4. [Correr en local (Minikube)](#4-correr-en-local-minikube)
5. [Ver la UI web](#5-ver-la-ui-web)
6. [Inspeccionar la base de datos](#6-inspeccionar-la-base-de-datos)
7. [Kubernetes con registry gratuito (ghcr.io + minikube)](#7-kubernetes-con-registry-gratuito-ghcrio--minikube)
8. [Diferencias local vs producción (SAD/SRS)](#8-diferencias-local-vs-producción-sadsrs)
9. [Probar el sistema comando a comando](#9-probar-el-sistema-comando-a-comando)
   - 9.1 Registro e inicio de sesión
   - 9.10 Búsqueda con caché Redis
   - 9.11 Gamificación
   - 9.12 Certificados
   - 9.13 Notificaciones in-app
   - 9.14 Rutas de aprendizaje
10. [Ejecutar los tests unitarios](#10-ejecutar-los-tests-unitarios)
11. [Mapa de puertos (local)](#11-mapa-de-puertos-local)

---

## 1. Requisitos previos

| Herramienta | Versión mínima | Para qué |
|---|---|---|
| Docker + Docker Compose | 24 / 2.24 | Entorno local |
| Java (JDK) | 17 | Compilar los servicios Java si no usas Docker |
| Maven | 3.9 | Build Java fuera de Docker |
| .NET SDK | 8.0 | Compilar analytics-service si no usas Docker |
| kubectl | 1.28 | Desplegar en Kubernetes |
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

  ✗ REGISTRY  ← pendiente (ghcr.io, solo para Kubernetes)
  ✓ SMTP_HOST
══════════════════════════════════════════════════════════
```

Las variables de SMTP son opcionales para desarrollo local:

- **SMTP**: en local el email-service usa MailHog automáticamente (el contenedor apunta a `mailhog:1025` internamente, no requiere configuración).
- **REGISTRY**: solo necesaria para hacer push a ghcr.io. En local no se usa.

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
docker compose up -d user-service course-service assessment-service collaboration-service analytics-service email-service web-ui
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

## 4. Correr en local (Minikube)

Minikube levanta un cluster Kubernetes de un nodo en tu máquina. Permite validar los manifiestos de `infra/k8s/` — los mismos que se usan en producción con EKS — sin necesidad de una cuenta en AWS.

### 4.1 Prerrequisitos

| Herramienta | Versión mínima | Instalación |
|---|---|---|
| Docker | 24+ | https://docs.docker.com/get-docker/ |
| minikube | 1.32+ | https://minikube.sigs.k8s.io/docs/start/ |
| kubectl | 1.28+ | https://kubernetes.io/docs/tasks/tools/ |

No necesitas Maven, .NET SDK ni ninguna otra herramienta: los Dockerfiles hacen el build completo internamente.

Asegúrate también de haber ejecutado `bash scripts/setup-env.sh` al menos una vez para tener `jwt_private.pem` y `jwt_public.pem` en la raíz.

### 4.2 Levantar todo con un solo comando

```bash
bash scripts/local-k8s.sh
```

El script hace todo automáticamente:

1. Inicia minikube con 4 CPUs y 6 GB de RAM (driver Docker)
2. Habilita el addon `ingress` (nginx)
3. Construye las 7 imágenes Docker **dentro del daemon de minikube** con `eval $(minikube docker-env)`
4. Crea el namespace `puj-platform` y el Secret `platform-secrets` con valores locales
5. Aplica el overlay `infra/k8s/overlays/local/` (ajusta StorageClass, imagePullPolicy, Ingress y SMTP)
6. Espera a que todos los pods estén `Running`
7. Añade `platform.local` a `/etc/hosts` apuntando a la IP de minikube

> **Primera ejecución**: puede tardar 10–15 minutos porque Maven y el SDK de .NET corren dentro de Docker descargando dependencias. Las siguientes ejecuciones son mucho más rápidas gracias a la caché de capas.

### 4.3 Acceder a los servicios

Una vez que el script termina:

| Servicio | Cómo acceder |
|---|---|
| UI web | http://platform.local |
| MailHog (correos) | `kubectl port-forward svc/mailhog 8025:8025 -n puj-platform` → http://localhost:8025 |
| RabbitMQ management | `kubectl port-forward svc/rabbitmq 15672:15672 -n puj-platform` → http://localhost:15672 |

### 4.4 Diferencias respecto a docker compose

| Aspecto | docker compose | Minikube |
|---|---|---|
| Base de datos | PostgreSQL directo (5432) | PostgreSQL + PgBouncer sidecar (6432) |
| StorageClass | N/A | `standard` (minikube) en lugar de `gp2` (AWS) |
| Ingress | Sin Ingress — acceso por puerto | nginx Ingress Controller |
| imagePullPolicy | N/A | `Never` — imágenes locales en minikube |
| SMTP | MailHog vía docker network | MailHog desplegado en el mismo namespace K8s |
| Réplicas | 1 por servicio | 1–2 según el HPA (limitado por los 6 GB asignados) |

### 4.5 Comandos útiles

```bash
# Ver estado de todos los pods
kubectl get pods -n puj-platform

# Logs de un servicio
kubectl logs -n puj-platform deployment/user-service --follow

# Ver el ingress y su IP
kubectl get ingress -n puj-platform

# Destruir todo y liberar recursos
kubectl delete namespace puj-platform
minikube stop
```

---

## 5. Ver la UI web

La UI está en **http://localhost:8080**. Es una aplicación JSF (server-side rendering): el servidor genera el HTML completo, el browser no llama a ningún API directamente.

### Roles y qué ve cada uno

Hay cuatro roles en el sistema. Lo que se muestra en pantalla cambia completamente según el rol.

#### STUDENT (el flujo principal)

1. Registro en `/views/register` — marcar la casilla de consentimiento (Ley 1581).
2. Tras registrarse, abrir **http://localhost:8025** (MailHog) para ver el correo de bienvenida.
3. Iniciar sesión → redirige al dashboard de estudiante.
4. Dashboard muestra: cursos inscritos con progreso, rutas de aprendizaje y botones de acceso rápido.
5. Desde `/views/courses` explorar el catálogo, usar la búsqueda por texto o categoría, e inscribirse.
6. Desde `/views/learning-paths` ver e inscribirse en rutas de aprendizaje.
7. Al completar lecciones y aprobar evaluaciones se acumulan puntos y se desbloquean insignias.
8. Al aprobar todas las evaluaciones de un curso se genera automáticamente un certificado PDF descargable desde `/views/certificates`.
9. Las notificaciones in-app llegan automáticamente (lección completada, certificado listo, etc.).

#### INSTRUCTOR

1. El ADMIN asigna el rol. El instructor inicia sesión y va al dashboard.
2. Dashboard muestra sus cursos (DRAFT / PUBLISHED) con acceso directo a gestión.
3. Crea cursos con categoría → módulos → lecciones → contenidos (texto/video/PDF).
4. Crea rutas de aprendizaje desde `/views/learning-path-create` (selecciona cursos en orden).
5. Construye evaluaciones desde `/views/assessment-builder?courseId=…`.
6. Configura el umbral adaptativo: porcentaje mínimo y lección de refuerzo si no lo alcanza.

#### DIRECTOR

1. Solo lectura — no puede modificar cursos ni usuarios.
2. Dashboard muestra estadísticas globales: usuarios activos, inscripciones, tasa de aprobación, ranking de cursos.
3. Puede descargar reportes CSV y PDF del analytics-service.

#### ADMIN

1. Dashboard muestra las mismas estadísticas que el DIRECTOR más las herramientas de gestión.
2. En el panel de usuarios puede cambiar roles (STUDENT ↔ INSTRUCTOR ↔ DIRECTOR ↔ ADMIN).
3. Acceso a todos los cursos y foros independientemente del instructor.

### URLs de la UI

```
http://localhost:8080/views/login               → Inicio de sesión
http://localhost:8080/views/register            → Registro con consentimiento
http://localhost:8080/views/dashboard           → Dashboard según rol
http://localhost:8080/views/courses             → Catálogo con búsqueda
http://localhost:8080/views/learning-paths      → Rutas de aprendizaje
http://localhost:8080/views/assessments         → Evaluaciones del estudiante
http://localhost:8080/views/forums              → Foros colaborativos
http://localhost:8080/views/profile             → Perfil, puntos e insignias
http://localhost:8080/views/certificates        → Mis certificados (STUDENT)
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

## 6. Inspeccionar la base de datos

PostgreSQL corre en `localhost:5432`. Usuario `puj_admin`, contraseña `postgres_secret` (como está en `.env`).

### Conectarse desde la terminal

```bash
# Opción 1: psql dentro del contenedor (sin instalar psql localmente)
docker compose exec postgres psql -U puj_admin -d learning_platform

# Opción 2: psql local si lo tienes instalado
psql -h localhost -p 5432 -U puj_admin -d learning_platform
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
| `analytics.results` | Eventos de calificaciones, inscripciones y registros para métricas |
| `gamification.events` | Eventos de lecciones, evaluaciones y foros para otorgar puntos |
| `notifications.events` | Eventos para generar notificaciones in-app del estudiante |
| `email.notifications` | Correos pendientes de enviar |
| `dead.letter.queue` | Mensajes que fallaron después de 3 reintentos |

---

## 7. Kubernetes con registry gratuito (ghcr.io + minikube)

Esta sección complementa §4: en lugar de construir las imágenes *dentro* del daemon de minikube (`imagePullPolicy: Never`), las sube a **GitHub Container Registry (ghcr.io)**, que es completamente gratuito, y luego minikube las descarga. Esto valida el flujo real de pull de imágenes sin pagar nada.

**Cuándo usar §4 vs §7**

| | §4 — minikube sin registry | §7 — minikube + ghcr.io |
|---|---|---|
| Registry | Ninguno (imágenes solo en minikube) | ghcr.io (gratuito) |
| Requiere cuenta | No | Sí (GitHub, gratuita) |
| Valida el pull de imágenes | No | Sí |
| Primera ejecución | ~10 min | ~15 min |

### 7.1 Prerrequisitos

| Herramienta | Cómo obtenerla | Costo |
|---|---|---|
| minikube | https://minikube.sigs.k8s.io/docs/start/ | Gratis |
| kubectl | https://kubernetes.io/docs/tasks/tools/ | Gratis |
| Docker | https://docs.docker.com/get-docker/ | Gratis |
| Cuenta GitHub | https://github.com/join | Gratis |
| Personal Access Token (PAT) | GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic) → New token → scope: `write:packages` | Gratis |

### 7.2 Configurar el registry

En `.env`, añade tu usuario de GitHub:

```bash
REGISTRY=ghcr.io/tu-usuario-github
```

Luego edita `infra/k8s/overlays/registry/kustomization.yaml` y reemplaza `TU_USUARIO` con tu usuario real de GitHub (son 7 líneas `newName`).

Login al registry (solo una vez, el token se guarda en `~/.docker/config.json`):

```bash
# Sustituye TU_USUARIO y TU_PAT por los valores reales
echo "TU_PAT" | docker login ghcr.io -u TU_USUARIO --password-stdin
```

### 7.3 Build y push de imágenes

```bash
bash scripts/ecr-push.sh
```

Para un tag específico (útil para versionado):

```bash
bash scripts/ecr-push.sh v1.0.0
```

El script construye las 7 imágenes y las sube a `ghcr.io/tu-usuario/puj/<servicio>:latest`.

> **Imágenes privadas vs públicas:** por defecto ghcr.io crea las imágenes como privadas. Para este proyecto de demo puedes hacerlas públicas en GitHub → tu perfil → Packages → selecciona el paquete → Package settings → Change visibility → Public. Con imágenes públicas no necesitas imagePullSecret en el cluster.

### 7.4 Levantar minikube y desplegar

```bash
# Iniciar minikube (si no está corriendo)
minikube start --cpus=4 --memory=6144 --driver=docker
minikube addons enable ingress

# Namespace y secretos
kubectl apply -f infra/k8s/namespace.yaml
kubectl create secret generic platform-secrets \
  --namespace puj-platform \
  --from-literal=DB_USER=puj_admin \
  --from-literal=DB_PASSWORD=puj_secret \
  --from-literal=REDIS_PASSWORD=redis_secret \
  --from-literal=RABBITMQ_USER=puj_rabbit \
  --from-literal=RABBITMQ_PASSWORD=rabbit_secret \
  --from-literal=JWT_PRIVATE_KEY="$(cat jwt_private.pem)" \
  --from-literal=JWT_PUBLIC_KEY="$(cat jwt_public.pem)" \
  --from-literal=AWS_ACCESS_KEY_ID=local-dummy \
  --from-literal=AWS_SECRET_ACCESS_KEY=local-dummy \
  --from-literal=SMTP_HOST=mailhog \
  --from-literal=SMTP_PORT=1025 \
  --from-literal=SMTP_USER="" \
  --from-literal=SMTP_PASSWORD="" \
  --dry-run=client -o yaml | kubectl apply -f -

# Desplegar con el overlay de registry
kubectl apply -k infra/k8s/overlays/registry/
```

> Si las imágenes son **privadas** en ghcr.io, crea también un imagePullSecret antes de desplegar:
> ```bash
> kubectl create secret docker-registry ghcr-secret \
>   --namespace puj-platform \
>   --docker-server=ghcr.io \
>   --docker-username=TU_USUARIO \
>   --docker-password=TU_PAT
> ```
> Luego añade `imagePullSecrets: [{name: ghcr-secret}]` a cada Deployment, o simplemente haz públicas las imágenes (más sencillo para un proyecto de demo).

### 7.5 Verificar el despliegue

```bash
# Ver que todos los pods arranquen (minikube descarga las imágenes de ghcr.io)
kubectl get pods -n puj-platform -w

# Ver la IP de minikube
minikube ip

# Acceder a la UI (si platform.local ya está en /etc/hosts desde §4)
# http://platform.local

# Logs de un servicio
kubectl logs -n puj-platform deployment/user-service --follow
```

---

## 8. Diferencias local vs producción (SAD/SRS)

Esta sección documenta qué cambia entre el entorno local (Docker Compose / minikube) y el despliegue de producción descrito en el SAD/SRS. Es una referencia para cuando el proyecto llegue a producción — **no requiere ningún servicio de pago para desarrollar o demostrar el sistema**.

### 8.1 Qué es diferente

| Aspecto | Local (gratis) | Producción (pagada) |
|---|---|---|
| Cluster K8s | minikube en tu máquina | AWS EKS u otro proveedor |
| Registry de imágenes | ghcr.io (gratis) o daemon de minikube | ECR, ghcr.io, Docker Hub, etc. |
| Base de datos | PostgreSQL directo (puerto 5432) | PostgreSQL 15 + PgBouncer sidecar (puerto 6432) |
| Correo | MailHog local, sin auth ni TLS | SMTP real con `SMTP_AUTH=true` y `SMTP_STARTTLS=true` |
| Archivos de curso | S3 no disponible; endpoints de upload fallan con error | AWS S3 con URLs prefirmadas |
| HTTPS / dominio | Sin TLS, acceso por `platform.local` | Certificado TLS en `platform.puj.edu.co` |
| Escalado | 1 réplica fija por servicio | HPA: 2–8 réplicas según CPU |
| Secretos | Archivo `.env` local | Kubernetes Secret `platform-secrets` |
| Sticky sessions (JSF) | Innecesario con 1 réplica | `sessionAffinity: ClientIP` en Service de web-ui |
| WebSocket multi-réplica | Un único pod, sin coordinación | Redis Pub/Sub coordina entre pods de collaboration-service |

### 8.2 Componentes que solo existen en producción

- **PgBouncer**: en local postgres escucha directo en 5432. En K8s corre como sidecar (puerto 6432); el manifesto `infra/k8s/postgres/deployment.yaml` ya lo incluye.
- **Redis Pub/Sub para WebSocket**: con 1 réplica en local no se necesita. En K8s, el HPA puede escalar collaboration-service a 2+ pods y Redis coordina los mensajes.
- **HPA y `terminationGracePeriodSeconds: 60`**: solo tienen efecto con múltiples réplicas en un cluster real.
- **Cumplimiento Ley 1581**: consentimiento explícito y soft-delete funcionan igual en ambos entornos. En producción los datos deben residir en `us-east-1` (ya configurado en los manifiestos).

### 8.3 Qué habría que cambiar para ir a producción

Todos estos pasos son opcionales para el desarrollo y demo del proyecto:

1. **Registry**: `scripts/ecr-push.sh` funciona con cualquier registry OCI (ghcr.io, ECR, Docker Hub). En producción basta con apuntar `REGISTRY` al registry del proveedor elegido.
2. **Secretos reales**: reemplazar los valores dummy del secret de minikube por credenciales reales (SMTP, S3, etc.).
3. **SMTP real**: el email-service en K8s ya tiene `SMTP_AUTH=true` y `SMTP_STARTTLS=true` en `infra/k8s/email-service/deployment.yaml`. Solo hay que actualizar el secret con el host/usuario/contraseña reales.
4. **DNS**: apuntar `platform.puj.edu.co` a la IP del Ingress del cluster. El `infra/k8s/ingress.yaml` ya tiene la anotación de certificado configurada.
5. **Verificar PgBouncer**:
   ```bash
   kubectl get pods -n puj-platform -l app=postgres
   kubectl describe pod -n puj-platform <nombre-pod-postgres>
   # Debe mostrar dos containers: "postgres" y "pgbouncer"
   ```

---

## 9. Probar el sistema comando a comando

Todos los ejemplos apuntan al entorno local (Docker Compose).

### 9.1 Registro e inicio de sesión

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

### 9.2 Verificar bloqueo por intentos fallidos

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

### 9.3 Crear un curso e inscribirse

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

### 9.4 Probar el motor adaptativo

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

### 9.5 Chat en tiempo real (WebSocket)

```bash
# Instalar wscat si no lo tienes
npm install -g wscat

# Terminal 1
wscat -c "ws://localhost:8084/ws/groups/<group-id>?token=$TOKEN"

# Terminal 2 (con otro TOKEN de otro usuario)
wscat -c "ws://localhost:8084/ws/groups/<group-id>?token=$TOKEN2"

# Escribir en terminal 1 y ver cómo aparece en terminal 2 en tiempo real
```

### 9.6 Dashboard de analítica

```bash
# Requiere rol DIRECTOR o ADMIN
curl -s "http://localhost:8085/api/v1/analytics/dashboard/summary" \
  -H "Authorization: Bearer $DIRECTOR_TOKEN" | jq

curl -s "http://localhost:8085/api/v1/analytics/dashboard/top-courses" \
  -H "Authorization: Bearer $DIRECTOR_TOKEN" | jq
```

### 9.7 Exportar reportes

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

### 9.8 Ver correos enviados (MailHog)

```bash
# Interfaz web
open http://localhost:8025

# API REST de MailHog para scripting
curl -s http://localhost:8025/api/v2/messages | jq '.items[0].Content.Headers.Subject'
```

### 9.9 Ver colas de RabbitMQ

```bash
open http://localhost:15672
# Usuario: puj_rabbit / Contraseña: rabbit_secret
# Ir a Queues → ver mensajes pendientes
```

### 9.10 Probar búsqueda con caché Redis

```bash
# Buscar cursos por texto
curl -s "http://localhost:8082/api/v1/search?q=java&sort=newest&page=0&size=12" \
  -H "Authorization: Bearer $TOKEN" | jq '.total, .data[0].title'

# Buscar por categoría
curl -s "http://localhost:8082/api/v1/search?category=programacion" \
  -H "Authorization: Bearer $TOKEN" | jq '.total'

# Ver la clave en Redis (TTL 300s)
docker compose exec redis redis-cli KEYS "search:*"
docker compose exec redis redis-cli TTL "search:java::newest:0:12"
```

### 9.11 Probar gamificación

```bash
# Ver puntos de un usuario
curl -s "http://localhost:8081/api/v1/users/me/points" \
  -H "Authorization: Bearer $TOKEN" | jq

# Ver insignias
curl -s "http://localhost:8081/api/v1/users/me/badges" \
  -H "Authorization: Bearer $TOKEN" | jq

# Ver reglas en base de datos
docker compose exec postgres psql -U puj_admin -d learning_platform \
  -c "SELECT action_type, points FROM users.gamification_rules ORDER BY points DESC;"
```

### 9.12 Probar certificados

```bash
# Ver certificados de un estudiante
STUDENT_TOKEN=$(curl -s -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"andres.mora@puj.edu.co","password":"Estudiante1!"}' \
  | jq -r '.accessToken')

STUDENT_ID=$(curl -s -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"andres.mora@puj.edu.co","password":"Estudiante1!"}' \
  | jq -r '.user.id')

curl -s "http://localhost:8085/api/v1/certificates/student/$STUDENT_ID" \
  -H "Authorization: Bearer $STUDENT_TOKEN" | jq '.[0].courseTitle'

# Verificar un certificado por código (público)
VERIFICATION_CODE="<uuid-del-certificado>"
curl -s "http://localhost:8085/api/v1/certificates/verify/$VERIFICATION_CODE" | jq

# Listar certificados en MinIO
docker compose exec minio mc ls local/certificates/ --recursive
```

### 9.13 Probar notificaciones in-app

```bash
# Ver mis notificaciones
curl -s "http://localhost:8084/api/v1/notifications" \
  -H "Authorization: Bearer $TOKEN" | jq '.[0]'

# Contar no leídas
curl -s "http://localhost:8084/api/v1/notifications/unread-count" \
  -H "Authorization: Bearer $TOKEN" | jq

# Marcar todas como leídas
curl -s -X PATCH "http://localhost:8084/api/v1/notifications/read-all" \
  -H "Authorization: Bearer $TOKEN"
```

### 9.14 Probar rutas de aprendizaje

```bash
# Listar rutas publicadas
curl -s "http://localhost:8082/api/v1/learning-paths?status=PUBLISHED" \
  -H "Authorization: Bearer $TOKEN" | jq '.[0].title'

# Inscribirse en una ruta
PATH_ID="<uuid-de-la-ruta>"
curl -s -X POST "http://localhost:8082/api/v1/learning-paths/$PATH_ID/enroll" \
  -H "Authorization: Bearer $TOKEN" | jq

# Ver progreso en la ruta
curl -s "http://localhost:8082/api/v1/learning-paths/$PATH_ID/progress" \
  -H "Authorization: Bearer $TOKEN" | jq
```

### 9.15 Simular fallo de Redis (fallback adaptativo)

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

## 10. Ejecutar los tests unitarios

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

## 11. Mapa de puertos (local)

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
