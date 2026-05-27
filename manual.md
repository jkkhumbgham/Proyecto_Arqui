# Manual de ejecución y pruebas
**Plataforma de Aprendizaje Adaptativo y Colaborativo — PUJ 2026**

---

## Tabla de contenidos

1. [Requisitos previos](#1-requisitos-previos)
2. [Configuración inicial (solo una vez)](#2-configuración-inicial-solo-una-vez)
3. [Correr en local (Docker Compose)](#3-correr-en-local-docker-compose)
4. [Correr en Kubernetes local (Minikube)](#4-correr-en-kubernetes-local-minikube)
   - 4.1 [Linux](#41-linux)
   - 4.2 [Windows](#42-windows)
   - 4.3 [Primer despliegue](#43-primer-despliegue)
   - 4.4 [Reset limpio (borrar todo y volver a empezar)](#44-reset-limpio-borrar-todo-y-volver-a-empezar)
   - 4.5 [Actualizar un servicio tras cambios de código](#45-actualizar-un-servicio-tras-cambios-de-código)
   - 4.6 [Acceder a los servicios](#46-acceder-a-los-servicios)
   - 4.7 [Diferencias respecto a Docker Compose](#47-diferencias-respecto-a-docker-compose)
   - 4.8 [Comandos útiles](#48-comandos-útiles)
5. [Ver la UI web](#5-ver-la-ui-web)
6. [Inspeccionar la base de datos](#6-inspeccionar-la-base-de-datos)
7. [Kubernetes con registry gratuito (ghcr.io)](#7-kubernetes-con-registry-gratuito-ghcrio)
8. [Diferencias local vs producción](#8-diferencias-local-vs-producción)
9. [Probar el sistema comando a comando](#9-probar-el-sistema-comando-a-comando)
10. [Ejecutar los tests unitarios](#10-ejecutar-los-tests-unitarios)
11. [Mapa de puertos (local)](#11-mapa-de-puertos-local)

---

## 1. Requisitos previos

### Linux

| Herramienta | Versión mínima | Instalación |
|---|---|---|
| Docker Engine + CLI | 24+ | https://docs.docker.com/engine/install/ |
| Docker Compose plugin | 2.24+ | Incluido con Docker Engine |
| minikube | 1.32+ | https://minikube.sigs.k8s.io/docs/start/ |
| kubectl | 1.28+ | https://kubernetes.io/docs/tasks/tools/install-kubectl-linux/ |
| openssl | cualquiera | `sudo apt install openssl` / `sudo dnf install openssl` |
| curl | cualquiera | Incluido en la mayoría de distros |
| Python 3 | 3.8+ | Incluido en la mayoría de distros |

### Windows

En Windows hay dos caminos. **Se recomienda WSL2** porque todos los scripts del proyecto son bash y funcionan sin modificación.

#### Opción A — WSL2 + Ubuntu (recomendada)

WSL2 ejecuta un kernel Linux real dentro de Windows. Una vez instalado, todo lo que está en la sección Linux aplica sin cambios.

```powershell
# En PowerShell como Administrador
wsl --install -d Ubuntu
# Reiniciar cuando lo pida
```

Luego instalar Docker Desktop con integración WSL2 habilitada:
- Descargar https://www.docker.com/products/docker-desktop/
- En Docker Desktop → Settings → Resources → WSL Integration → activar la distro Ubuntu

Instalar minikube y kubectl **dentro de Ubuntu WSL2**:
```bash
# Dentro de la terminal Ubuntu (WSL2)
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube

curl -LO "https://dl.k8s.io/release/$(curl -sL https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install kubectl /usr/local/bin/kubectl
```

A partir de aquí, **abrir siempre una terminal Ubuntu (WSL2)** y seguir las instrucciones de la sección Linux de cada paso.

#### Opción B — Git Bash + Docker Desktop (sin WSL2)

Instalar en orden:
1. Docker Desktop: https://www.docker.com/products/docker-desktop/
2. Git for Windows (incluye Git Bash): https://git-scm.com/download/win
3. minikube: https://minikube.sigs.k8s.io/docs/start/ → descargar el `.exe` y añadirlo al PATH
4. kubectl: https://kubernetes.io/docs/tasks/tools/install-kubectl-windows/
5. OpenSSL para Windows: https://slproweb.com/products/Win32OpenSSL.html (versión Light es suficiente)

Limitaciones con Git Bash:
- Los scripts `setup-env.sh` y `local-k8s.sh` funcionan en Git Bash.
- `eval "$(minikube docker-env)"` puede requerir el flag `--shell=bash`: `eval "$(minikube docker-env --shell=bash)"`.
- `/etc/hosts` en Windows está en `C:\Windows\System32\drivers\etc\hosts` y requiere permisos de Administrador para editarlo.
- Si algún script falla por saltos de línea (CRLF), convertirlo con: `sed -i 's/\r//' scripts/local-k8s.sh`

---

## 2. Configuración inicial (solo una vez)

Este paso genera las claves RSA para JWT y crea los archivos `.env`, `jwt_private.pem` y `jwt_public.pem`. **Solo hay que ejecutarlo una vez.**

### Linux / WSL2 / Git Bash

```bash
# Desde la raíz del repositorio
bash scripts/setup-env.sh
```

### Qué hace el script

1. Copia `.env.example` → `.env` (si `.env` no existe todavía).
2. Genera un par RSA 2048 con `openssl` y escribe las claves en `.env` y en archivos `.pem` separados.
3. Imprime qué variables todavía necesitan llenarse a mano.

La salida se ve así:

```
=== PUJ Learning Platform — Setup inicial ===
✓ .env creado desde .env.example
  Generando par RSA 2048...
✓ Claves RSA generadas y escritas en .env

══════════════════════════════════════════════════════════
  Rellena manualmente en .env antes de hacer docker compose up:

  ✗ REGISTRY  ← pendiente (ghcr.io, solo para Kubernetes con registry)
  ✓ SMTP_HOST
══════════════════════════════════════════════════════════
```

Las variables de SMTP son opcionales para desarrollo local (MailHog las intercepta automáticamente). `REGISTRY` solo es necesaria para el flujo de § 7.

> `.env`, `jwt_private.pem` y `jwt_public.pem` están en `.gitignore` — nunca se suben al repositorio.

---

## 3. Correr en local (Docker Compose)

Docker Compose levanta todo en contenedores en tu máquina sin Kubernetes. Es la forma más rápida de empezar.

### Linux / WSL2

```bash
# Generar claves (solo la primera vez)
bash scripts/setup-env.sh

# Levantar todo
docker compose up -d

# Seguir el arranque de un servicio Java (WildFly tarda ~60 s)
docker compose logs -f user-service
# Esperar: "WildFly Full 31.0.0.Final started"
```

### Windows (Git Bash o PowerShell)

```bash
# En Git Bash
bash scripts/setup-env.sh
docker compose up -d
```

```powershell
# En PowerShell (alternativa)
docker compose up -d
```

### Verificar que todo está vivo

```bash
# user-service — debe responder 401 (esperado, no hay token)
curl -s http://localhost:8081/api/v1/auth/me

# analytics-service
curl -s http://localhost:8085/health
# → {"status":"UP","timestamp":"..."}

# UI web
# Linux:
xdg-open http://localhost:8080
# Windows:
start http://localhost:8080
```

### Poblar con datos de demostración

```bash
bash scripts/seed-data.sh
```

El seed crea 7 usuarios (1 admin, 1 instructor, 5 estudiantes), 5 cursos con módulos y lecciones, evaluaciones con preguntas, foros e inscripciones. Al final imprime las credenciales de cada usuario.

### Apagar

```bash
docker compose down          # para los contenedores, conserva volúmenes (datos)
docker compose down -v       # para Y borra todos los datos (postgres, redis, rabbit)
```

---

## 4. Correr en Kubernetes local (Minikube)

Minikube levanta un cluster Kubernetes de un nodo en tu máquina y valida los mismos manifiestos de `infra/k8s/` que se usarían en producción.

### 4.1 Linux

No requiere configuración especial. Minikube usa el driver `docker` por defecto, que es el más eficiente en Linux.

Verificar que Docker está corriendo antes de empezar:

```bash
docker info
# Debe responder sin error
```

Si el comando falla, iniciar el servicio Docker:

```bash
# Distros con systemd (Ubuntu, Fedora, Arch, etc.)
sudo systemctl start docker
sudo systemctl enable docker   # para que inicie automáticamente al boot

# Añadir tu usuario al grupo docker (evita usar sudo en cada comando)
sudo usermod -aG docker $USER
newgrp docker
```

### 4.2 Windows

#### Con WSL2 (recomendado)

Abrir **Docker Desktop** en Windows (icono en la barra de tareas) y esperar a que el indicador esté en verde ("Engine running"). Luego abrir la terminal Ubuntu (WSL2) y continuar como si fuera Linux — todos los comandos son idénticos.

```bash
# Verificar desde WSL2
docker info
minikube version
kubectl version --client
```

#### Con Git Bash (sin WSL2)

Abrir Docker Desktop y esperar a que esté corriendo. Luego abrir Git Bash:

```bash
# Verificar
docker info
minikube version
kubectl version --client
```

**Diferencia importante en Windows con Git Bash**: cuando el script ejecuta `eval "$(minikube docker-env)"`, en Git Bash puede ser necesario usar el flag explícito:

```bash
eval "$(minikube docker-env --shell=bash)"
```

Si `local-k8s.sh` falla en este paso, edita la línea 47 del script para añadir `--shell=bash`.

**`/etc/hosts` en Windows**: el script intenta añadir `platform.local` a `/etc/hosts`. En Windows, este archivo está en `C:\Windows\System32\drivers\etc\hosts` y requiere permisos de Administrador. Git Bash no puede escribirlo automáticamente. Si el script no puede editarlo, añade la línea manualmente:

1. Abrir Notepad como Administrador.
2. Abrir `C:\Windows\System32\drivers\etc\hosts`.
3. Añadir al final: `<IP-de-minikube>  platform.local` (obtener la IP con `minikube ip`).

Alternativamente, usar port-forward en lugar de Ingress (ver § 4.6).

### 4.3 Primer despliegue

Asegurarse de haber ejecutado `bash scripts/setup-env.sh` al menos una vez (genera `jwt_private.pem` y `jwt_public.pem` que el script necesita).

```bash
bash scripts/local-k8s.sh
```

El script hace todo automáticamente en este orden:

1. Verifica que minikube, kubectl y docker estén instalados.
2. Inicia minikube con 4 CPUs y 4 GB de RAM (driver `docker`).
3. Habilita el addon `ingress` (nginx).
4. Construye las 7 imágenes Docker **dentro del daemon de minikube** (`eval $(minikube docker-env)`) — así K8s puede usarlas sin registry.
5. Crea el namespace `puj-platform` y el Secret `platform-secrets` con valores locales.
6. Aplica el overlay `infra/overlays/local/` (ajusta StorageClass, `imagePullPolicy: Never`, Ingress y SMTP para MailHog).
7. Espera a que todos los pods estén `Running` (hasta 10 minutos).
8. Intenta añadir `platform.local` a `/etc/hosts`.
9. Abre port-forwards temporales para los 5 servicios que necesita el seed.
10. **Espera a que `analytics-service /health` responda** — esto garantiza que MassTransit está conectado a RabbitMQ y los eventos del seed llegarán a la cola correctamente.
11. Ejecuta `seed-data.sh --k8s` para poblar la plataforma con datos de demostración.
12. Cierra los port-forwards del seed.
13. Imprime el resumen con URLs y comandos útiles.

> **Primera ejecución**: puede tardar 10–15 minutos porque Maven y el SDK de .NET descargan dependencias dentro de Docker. Las siguientes ejecuciones son mucho más rápidas gracias a la caché de capas de Docker.

### 4.4 Reset limpio (borrar todo y volver a empezar)

Cuando quieres datos completamente frescos o probar cambios en los manifiestos de K8s:

```bash
# Paso 1: borrar el namespace (borra pods, services, PVCs y datos de postgres/rabbitmq)
kubectl delete namespace puj-platform

# Paso 2: esperar a que el namespace desaparezca del todo
kubectl wait --for=delete namespace/puj-platform --timeout=120s

# Paso 3: volver a desplegar
bash scripts/local-k8s.sh
```

> **¿Por qué esperar el `delete`?** El namespace entra en estado `Terminating` durante 10–30 segundos mientras K8s limpia todos los recursos. Si lanzas `local-k8s.sh` antes de que termine, `kubectl apply` intentará crear recursos en un namespace que todavía existe y el script fallará.

> **¿Se borran los datos?** Sí. Los PVCs usan `storageClassName: standard` de minikube que tiene `reclaimPolicy: Delete`, por lo que cuando el PVC se borra el PV y sus datos también desaparecen. Esto es el comportamiento deseado para un reset limpio.

> **¿Se borran las imágenes Docker?** No. Las imágenes `puj/*:local` quedan en el daemon de minikube. El siguiente `local-k8s.sh` las reconstruirá igualmente (para incorporar posibles cambios de código), pero si no hubo cambios, Docker reutilizará las capas cacheadas y el build será rápido.

Si también quieres parar minikube y liberar todos los recursos de CPU/RAM:

```bash
kubectl delete namespace puj-platform
minikube stop
# Para borrar minikube completamente (incluidas imágenes):
# minikube delete
```

### 4.5 Actualizar un servicio tras cambios de código

**No necesitas borrar el namespace para actualizar código.** Solo reconstruye la imagen del servicio que cambió y haz un rolling restart:

```bash
# 1. Apuntar Docker al daemon de minikube
eval "$(minikube docker-env)"
# Windows Git Bash:
# eval "$(minikube docker-env --shell=bash)"

# 2. Reconstruir solo el servicio modificado (ejemplo: user-service)
docker build -t puj/user-service:local -f services/user-service/Dockerfile .

# 3. Indicar a Kubernetes que use la imagen nueva (rolling restart sin downtime)
kubectl rollout restart deployment/user-service -n puj-platform

# 4. Seguir el despliegue hasta que esté listo
kubectl rollout status deployment/user-service -n puj-platform
```

Para otros servicios, el patrón es siempre el mismo. Estos son los comandos de build para cada uno:

```bash
# user-service (Java / WildFly)
docker build -t puj/user-service:local -f services/user-service/Dockerfile .

# course-service (Java / WildFly)
docker build -t puj/course-service:local -f services/course-service/Dockerfile .

# assessment-service (Java / WildFly)
docker build -t puj/assessment-service:local -f services/assessment-service/Dockerfile .

# collaboration-service (Java / WildFly)
docker build -t puj/collaboration-service:local -f services/collaboration-service/Dockerfile .

# email-service (Java / WildFly)
docker build -t puj/email-service:local -f services/email-service/Dockerfile .

# analytics-service (.NET 8)
docker build -t puj/analytics-service:local services/analytics-service/

# web-ui (Java / WildFly + JSF)
docker build -t puj/web-ui:local -f frontend/web-ui/Dockerfile .
```

Después del build, hacer el rollout restart del deployment correspondiente:

```bash
kubectl rollout restart deployment/<nombre-del-servicio> -n puj-platform
kubectl rollout status  deployment/<nombre-del-servicio> -n puj-platform
```

> **¿Por qué funciona?** Con `imagePullPolicy: Never`, Kubernetes usa directamente la imagen que está en el daemon local de minikube. El `rollout restart` crea un pod nuevo con la imagen recién construida y termina el pod viejo solo cuando el nuevo está `Running` y pasando los health checks.

### 4.6 Acceder a los servicios

Una vez que `local-k8s.sh` termina:

| Servicio | URL | Cómo |
|---|---|---|
| UI web | http://platform.local | Requiere `/etc/hosts` (el script lo añade automáticamente en Linux) |
| UI web (alternativa sin `/etc/hosts`) | http://localhost:8080 | `kubectl port-forward svc/web-ui 8080:8080 -n puj-platform` |
| MailHog (correos capturados) | http://localhost:8025 | `kubectl port-forward svc/mailhog 8025:8025 -n puj-platform` |
| RabbitMQ Management | http://localhost:15672 | `kubectl port-forward svc/rabbitmq 15672:15672 -n puj-platform` |
| RabbitMQ credenciales | — | `puj_rabbit` / `rabbit_secret` |

En Windows, si el script no pudo editar `/etc/hosts` automáticamente, usa siempre el port-forward para la UI:

```bash
kubectl port-forward svc/web-ui 8080:8080 -n puj-platform
# Luego abrir http://localhost:8080
```

### 4.7 Diferencias respecto a Docker Compose

| Aspecto | Docker Compose | Minikube |
|---|---|---|
| Base de datos | PostgreSQL directo (5432) | PostgreSQL + PgBouncer sidecar (6432) |
| StorageClass | N/A (volúmenes Docker) | `standard` (minikube) en lugar de `gp2` (AWS) |
| Ingress | Sin Ingress — acceso directo por puerto | nginx Ingress Controller → `platform.local` |
| imagePullPolicy | N/A | `Never` — imágenes locales en el daemon de minikube |
| SMTP | MailHog vía red Docker | MailHog desplegado en el mismo namespace K8s |
| Analytics | Puerto 8085 directo | Port-forward `svc/analytics-service 8085:8080` |
| Réplicas | 1 por servicio | 1 inicial, HPA puede escalar (limitado por los 4 GB asignados) |

### 4.8 Comandos útiles

```bash
# Ver estado de todos los pods
kubectl get pods -n puj-platform

# Ver en tiempo real mientras arrancan
kubectl get pods -n puj-platform -w

# Logs de un servicio (en tiempo real)
kubectl logs -n puj-platform deployment/user-service --follow

# Ver el ingress y su IP
kubectl get ingress -n puj-platform

# Ver consumo de CPU y RAM por pod (requiere metrics-server)
kubectl top pods -n puj-platform

# Entrar a la shell del pod de postgres
kubectl exec -it -n puj-platform deploy/postgres -c postgres -- psql -U puj_admin -d learning_platform

# Ver eventos recientes del namespace (útil para diagnosticar fallos)
kubectl get events -n puj-platform --sort-by='.lastTimestamp' | tail -20

# Describir un pod que no arranca
kubectl describe pod -n puj-platform <nombre-del-pod>

# Destruir todo y liberar CPU/RAM
kubectl delete namespace puj-platform
minikube stop
```

---

## 5. Ver la UI web

La UI está en **http://localhost:8080** (Docker Compose) o **http://platform.local** (Minikube con Ingress). Es una aplicación JSF (server-side rendering): el servidor genera el HTML completo.

### Roles y qué ve cada uno

#### STUDENT

1. Registro en `/views/register` — marcar la casilla de consentimiento (Ley 1581).
2. Tras registrarse, abrir MailHog para ver el correo de bienvenida.
3. Iniciar sesión → redirige al dashboard de estudiante.
4. Dashboard muestra: cursos inscritos con progreso y botones de acceso rápido.
5. Desde `/views/courses` explorar el catálogo, buscar por texto o categoría, e inscribirse.

#### INSTRUCTOR

1. El ADMIN asigna el rol. El instructor inicia sesión y va al dashboard.
2. Dashboard muestra sus cursos con indicador DRAFT / PUBLISHED y métricas de estudiantes.
3. Crea cursos → módulos → lecciones → contenidos (texto / video / PDF).
4. Construye evaluaciones desde `/views/assessment-builder?courseId=…`.
5. Configura el umbral adaptativo: porcentaje mínimo y lección de refuerzo si no lo alcanza.

#### DIRECTOR

1. Solo lectura — no puede modificar cursos ni usuarios.
2. Dashboard muestra estadísticas globales: usuarios activos, inscripciones totales, tasa de aprobación, ranking de cursos.
3. Puede descargar reportes CSV y PDF del analytics-service.

#### ADMIN

1. Dashboard muestra las mismas estadísticas que el DIRECTOR más las herramientas de gestión.
2. En el panel de usuarios puede cambiar roles (STUDENT ↔ INSTRUCTOR ↔ DIRECTOR ↔ ADMIN).
3. Acceso a todos los cursos y foros independientemente del instructor.
4. Puede crear usuarios directamente sin que pasen por el flujo de registro.

### URLs principales

```
/views/login               → Inicio de sesión
/views/register            → Registro con consentimiento
/views/dashboard           → Dashboard según rol
/views/courses             → Catálogo con búsqueda
/views/assessments         → Evaluaciones del estudiante
/views/forums              → Foros colaborativos
/views/admin               → Panel de administración (solo ADMIN)
```

### Ver los correos

Cada acción importante dispara un correo. En desarrollo local todos caen en MailHog sin enviarse realmente:

- **Docker Compose**: http://localhost:8025
- **Minikube**: `kubectl port-forward svc/mailhog 8025:8025 -n puj-platform` → http://localhost:8025

| Acción | Template |
|---|---|
| Registro | Bienvenida + aviso Ley 1581 |
| Inscripción en curso | Confirmación de matrícula |
| Evaluación calificada | Nota + recomendación adaptativa (si aplica) |

---

## 6. Inspeccionar la base de datos

### Docker Compose

```bash
# Conectarse desde el contenedor (sin instalar psql localmente)
docker compose exec postgres psql -U puj_admin -d learning_platform

# Desde tu máquina si tienes psql instalado
psql -h localhost -p 5432 -U puj_admin -d learning_platform
# Contraseña: puj_secret
```

### Minikube (kubectl exec)

```bash
# Base de datos principal (servicios Java)
kubectl exec -it -n puj-platform deploy/postgres -c postgres -- \
  psql -U puj_admin -d learning_platform

# Base de datos de analytics (.NET)
kubectl exec -it -n puj-platform deploy/postgres -c postgres -- \
  psql -U puj_admin -d analytics_db
```

### Schemas y tablas principales

La base de datos `learning_platform` tiene 4 schemas, uno por dominio:

```sql
-- Ver todos los schemas
\dn

-- Usuarios registrados y sus roles
SELECT email, role, active, created_at
FROM users.users
ORDER BY created_at DESC;

-- Verificar consentimiento (Ley 1581) — debe estar vacío
SELECT email FROM users.users WHERE consent_given = false;

-- Inscripciones activas
SELECT u.email, c.title AS curso, e.enrolled_at, e.status
FROM courses.enrollments e
JOIN users.users u   ON u.id = e.user_id
JOIN courses.courses c ON c.id = e.course_id
WHERE e.deleted_at IS NULL
ORDER BY e.enrolled_at DESC;

-- Cursos y sus estados
SELECT title, status, max_students, created_at
FROM courses.courses
WHERE deleted_at IS NULL
ORDER BY created_at DESC;

-- Evaluaciones y notas
SELECT u.email, a.title AS evaluacion,
       s.score_pct, s.passed, s.submitted_at
FROM assessments.submissions s
JOIN users.users u               ON u.id = s.student_id
JOIN assessments.assessments a   ON a.id = s.assessment_id
WHERE s.deleted_at IS NULL
ORDER BY s.submitted_at DESC;

-- Verificar soft-delete (ningún registro se borra físicamente)
SELECT
  COUNT(*)                                       AS total,
  COUNT(*) FILTER (WHERE deleted_at IS NULL)     AS activos,
  COUNT(*) FILTER (WHERE deleted_at IS NOT NULL) AS borrados_soft
FROM users.users;
```

### Analytics (base de datos separada)

```sql
-- Conectado a analytics_db:
-- Estadísticas globales (lo que ve el Admin/Director en el dashboard)
SELECT total_users, total_enrollments, total_courses,
       avg_score, pass_rate, updated_at
FROM analytics.platform_stats;

-- Métricas por curso
SELECT course_title, total_enrollments, average_score, pass_rate
FROM analytics.course_metrics
ORDER BY total_enrollments DESC;
```

### Redis

```bash
# Docker Compose
docker compose exec redis redis-cli -a redis_secret

# Minikube
kubectl exec -it -n puj-platform deploy/redis -- redis-cli -a redis_secret
```

```redis
-- Tokens en blacklist (JTIs después de logout)
KEYS jwt:blacklist:*

-- Caché de búsquedas de cursos (TTL 300 s)
KEYS search:*

-- Ver TTL de una clave
TTL search:java::newest:0:12

-- Reglas adaptativas cacheadas (TTL 60 s)
KEYS adaptive:rule:*
```

### RabbitMQ

```bash
# Abrir la interfaz web
# Docker Compose:
xdg-open http://localhost:15672   # Linux
start http://localhost:15672      # Windows

# Minikube:
kubectl port-forward svc/rabbitmq 15672:15672 -n puj-platform
# → http://localhost:15672   usuario: puj_rabbit / contraseña: rabbit_secret

# Ver colas desde CLI (Docker Compose)
docker compose exec rabbitmq rabbitmqctl list_queues name messages consumers

# Ver colas desde CLI (Minikube)
kubectl exec -n puj-platform deploy/rabbitmq -- \
  rabbitmqctl list_queues name messages consumers
```

| Cola | Qué contiene |
|---|---|
| `analytics.results` | USER_REGISTERED, COURSE_ENROLLED, LESSON_COMPLETED, ASSESSMENT_SUBMITTED |
| `email.notifications` | Correos pendientes de enviar |
| `dead.letter.queue` | Mensajes que fallaron después de 3 reintentos |

---

## 7. Kubernetes con registry gratuito (ghcr.io)

En lugar de construir imágenes dentro del daemon de minikube, esta sección las sube a **GitHub Container Registry (ghcr.io)** y minikube las descarga. Valida el flujo real de pull de imágenes sin coste.

### Cuándo usar § 4 vs § 7

| | § 4 — minikube sin registry | § 7 — minikube + ghcr.io |
|---|---|---|
| Registry | Ninguno (imágenes solo en minikube) | ghcr.io (gratuito) |
| Requiere cuenta externa | No | Sí (GitHub, gratuita) |
| Valida el pull de imágenes | No | Sí |
| Primera ejecución | ~10 min | ~15 min |

### Prerrequisitos

- Cuenta GitHub (gratuita)
- Personal Access Token con scope `write:packages`: GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic) → New token

### Configuración

En `.env`, añadir:

```bash
REGISTRY=ghcr.io/tu-usuario-github
```

Editar `infra/overlays/registry/kustomization.yaml` y reemplazar `TU_USUARIO` con tu usuario real de GitHub en cada línea `newName`.

Login al registry (solo una vez):

```bash
# Linux / WSL2 / Git Bash
echo "TU_PAT" | docker login ghcr.io -u TU_USUARIO --password-stdin
```

### Build y push

```bash
bash scripts/ecr-push.sh
# Para un tag específico:
bash scripts/ecr-push.sh v1.0.0
```

El script construye las 7 imágenes y las sube a `ghcr.io/tu-usuario/puj/<servicio>:latest`.

> **Imágenes privadas vs públicas**: ghcr.io las crea privadas por defecto. Para el proyecto de demo es más sencillo hacerlas públicas: GitHub → tu perfil → Packages → seleccionar el paquete → Package settings → Change visibility → Public. Con imágenes públicas no necesitas `imagePullSecret`.

### Despliegue

```bash
# Iniciar minikube
minikube start --cpus=4 --memory=4096 --driver=docker
minikube addons enable ingress

# Namespace y secretos (igual que en § 4)
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
kubectl apply -k infra/overlays/registry/

# Seguir el arranque
kubectl get pods -n puj-platform -w
```

---

## 8. Diferencias local vs producción

| Aspecto | Local (gratis) | Producción |
|---|---|---|
| Cluster K8s | minikube en tu máquina | AWS EKS u otro proveedor |
| Registry de imágenes | Daemon de minikube / ghcr.io | ECR, ghcr.io, Docker Hub, etc. |
| Base de datos acceso | PostgreSQL directo (5432) | PgBouncer sidecar (6432) |
| Correo | MailHog sin auth ni TLS | SMTP real con `SMTP_AUTH=true` y `SMTP_STARTTLS=true` |
| Archivos de curso | Sin S3 (endpoints de upload deshabilitados) | AWS S3 con URLs prefirmadas |
| HTTPS / dominio | Sin TLS, `platform.local` | Certificado TLS en `platform.puj.edu.co` |
| Escalado | 1 réplica por servicio | HPA: 2–8 réplicas según CPU |
| Secretos | Archivo `.env` / Secret con valores dummy | Secret con credenciales reales |
| Sticky sessions (JSF) | Innecesario con 1 réplica | `sessionAffinity: ClientIP` en Service de web-ui |
| WebSocket multi-réplica | Un único pod | Redis Pub/Sub coordina entre pods de collaboration-service |

### Componentes que solo tienen efecto en producción

- **PgBouncer**: corre como sidecar en el pod de postgres (ya incluido en `infra/k8s/postgres/deployment.yaml`). En local postgres escucha directo en 5432.
- **HPA y `terminationGracePeriodSeconds: 60`**: solo tienen efecto con múltiples réplicas.
- **Redis Pub/Sub para WebSocket**: con 1 réplica en local no se necesita coordinación entre pods.

### Qué cambiar para ir a producción

1. **Registry**: `scripts/ecr-push.sh` funciona con cualquier registry OCI (ghcr.io, ECR, Docker Hub). Apuntar `REGISTRY` al registry del proveedor.
2. **Secretos reales**: reemplazar los valores dummy en el Secret de K8s con credenciales reales (SMTP, S3, etc.).
3. **SMTP**: el email-service ya tiene `SMTP_AUTH=true` y `SMTP_STARTTLS=true` en `infra/k8s/email-service/deployment.yaml`. Solo actualizar el Secret con host/usuario/contraseña reales.
4. **DNS**: apuntar `platform.puj.edu.co` a la IP del Ingress del cluster.

---

## 9. Probar el sistema comando a comando

Todos los ejemplos apuntan a Docker Compose local. Para Minikube, activar primero los port-forwards correspondientes (ver § 4.6).

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
  }'

# Login — guardar el token en variable
TOKEN=$(curl -s -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"juan@puj.edu.co","password":"MiPassword123!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

echo $TOKEN
```

### 9.2 Verificar bloqueo por intentos fallidos

```bash
# 5 intentos con contraseña incorrecta
for i in 1 2 3 4 5; do
  curl -s -X POST http://localhost:8081/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"juan@puj.edu.co","password":"incorrecta"}'
done

# El siguiente intento con contraseña correcta también falla: cuenta bloqueada 15 min
curl -s -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"juan@puj.edu.co","password":"MiPassword123!"}'
# → "Cuenta bloqueada por intentos fallidos. Intenta en 15 minutos."
```

### 9.3 Crear un curso e inscribirse

```bash
# Crear curso (requiere rol INSTRUCTOR)
COURSE=$(curl -s -X POST http://localhost:8082/api/v1/courses \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Arquitectura de Software",
    "description": "Patrones, estilos y decisiones arquitectónicas",
    "maxStudents": 30
  }')

COURSE_ID=$(echo $COURSE | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

# Inscribirse (requiere rol STUDENT)
curl -s -X POST "http://localhost:8082/api/v1/enrollments/courses/$COURSE_ID" \
  -H "Authorization: Bearer $TOKEN"
```

### 9.4 Probar el motor adaptativo

```bash
ASSESSMENT_ID=<uuid-de-una-evaluacion>

# Crear una regla: si el estudiante saca menos de 60% ver lección de refuerzo
curl -s -X POST http://localhost:8083/api/v1/adaptive-rules \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "assessmentId": "'"$ASSESSMENT_ID"'",
    "thresholdPct": 60,
    "action": "SUPPLEMENTARY_LESSON",
    "targetLessonId": "<uuid-leccion-refuerzo>"
  }'

# Iniciar una evaluación
SUBMISSION_ID=$(curl -s -X POST http://localhost:8083/api/v1/submissions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"assessmentId":"'"$ASSESSMENT_ID"'"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

# Enviar con respuestas vacías (nota 0 < 60%)
curl -s -X POST "http://localhost:8083/api/v1/submissions/$SUBMISSION_ID/submit" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"answers":[]}'
# → response incluye adaptiveRecommendation con la lección de refuerzo
```

### 9.5 Chat en tiempo real (WebSocket)

```bash
# Instalar wscat si no lo tienes
npm install -g wscat

# Terminal 1
wscat -c "ws://localhost:8084/ws/groups/<group-id>?token=$TOKEN"

# Terminal 2 (con TOKEN de otro usuario)
wscat -c "ws://localhost:8084/ws/groups/<group-id>?token=$TOKEN2"

# Escribir en Terminal 1 y ver cómo aparece en Terminal 2 en tiempo real
```

### 9.6 Dashboard de analítica

```bash
# Requiere rol DIRECTOR o ADMIN
DIRECTOR_TOKEN=<token-de-un-usuario-con-rol-DIRECTOR-o-ADMIN>

curl -s "http://localhost:8085/api/v1/analytics/dashboard/summary" \
  -H "Authorization: Bearer $DIRECTOR_TOKEN"

curl -s "http://localhost:8085/api/v1/analytics/dashboard/top-courses" \
  -H "Authorization: Bearer $DIRECTOR_TOKEN"

curl -s "http://localhost:8085/api/v1/analytics/dashboard/inactive-users?days=30" \
  -H "Authorization: Bearer $DIRECTOR_TOKEN"
```

### 9.7 Exportar reportes

```bash
# CSV de cursos
curl -s "http://localhost:8085/api/v1/analytics/export/courses.csv" \
  -H "Authorization: Bearer $DIRECTOR_TOKEN" \
  -o cursos.csv

# PDF de cursos
curl -s "http://localhost:8085/api/v1/analytics/export/courses.pdf" \
  -H "Authorization: Bearer $DIRECTOR_TOKEN" \
  -o informe_cursos.pdf

# CSV de submissions filtrado por curso y rango de fechas
curl -s "http://localhost:8085/api/v1/analytics/export/submissions.csv?courseId=$COURSE_ID&from=2026-01-01&to=2026-12-31" \
  -H "Authorization: Bearer $DIRECTOR_TOKEN" \
  -o submissions.csv
```

### 9.8 Ver correos enviados (MailHog)

```bash
# API REST de MailHog para scripting
curl -s http://localhost:8025/api/v2/messages \
  | python3 -c "import sys,json; [print(m['Content']['Headers']['Subject']) for m in json.load(sys.stdin)['items']]"
```

### 9.9 Ver colas de RabbitMQ

```bash
# Docker Compose — ver mensajes pendientes por cola
docker compose exec rabbitmq rabbitmqctl list_queues name messages consumers

# Minikube
kubectl exec -n puj-platform deploy/rabbitmq -- \
  rabbitmqctl list_queues name messages consumers
```

### 9.10 Probar búsqueda con caché Redis

```bash
# Primera búsqueda — va a base de datos
curl -s "http://localhost:8082/api/v1/search?q=java&sort=newest&page=0&size=12" \
  -H "Authorization: Bearer $TOKEN"

# Ver la clave creada en Redis (TTL 300 s)
docker compose exec redis redis-cli -a redis_secret KEYS "search:*"
docker compose exec redis redis-cli -a redis_secret TTL "search:java::newest:0:12"

# Segunda búsqueda — viene del cache (más rápida)
curl -s "http://localhost:8082/api/v1/search?q=java&sort=newest&page=0&size=12" \
  -H "Authorization: Bearer $TOKEN"
```

### 9.11 Simular fallo de Redis (fallback adaptativo)

```bash
# Parar Redis
docker compose stop redis

# El servicio debe seguir respondiendo usando base de datos como fallback
curl -s -X POST "http://localhost:8083/api/v1/submissions/$SUBMISSION_ID/submit" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"answers":[]}'

# Verificar en logs que usó el fallback
docker compose logs assessment-service | grep -i "redis\|fallback"

# Restaurar Redis
docker compose start redis
```

---

## 10. Ejecutar los tests unitarios

### Linux / WSL2

```bash
# Todos los módulos Java de una vez (desde la raíz del repositorio)
mvn test

# Solo un módulo específico
mvn test -pl libs/security
mvn test -pl services/user-service
mvn test -pl services/assessment-service
mvn test -pl services/email-service

# Analytics (.NET)
cd services/analytics-service
dotnet test
```

### Windows (Git Bash o PowerShell)

```bash
# Git Bash — igual que Linux
mvn test

# PowerShell — igual que Linux (mvn funciona igual)
mvn test
```

Para `.NET`:

```powershell
# PowerShell
cd services\analytics-service
dotnet test
```

### Tests disponibles

| Módulo | Test | Qué cubre |
|---|---|---|
| `libs/security` | `JwtProviderTest` | Round-trip de token, token expirado, token manipulado |
| `services/user-service` | `AuthServiceTest` | Registro sin consentimiento, email duplicado, login incorrecto, logout blacklistea JTI |
| `services/assessment-service` | `AdaptiveEngineTest` | Nota bajo umbral → recomendación, Redis caído → fallback a DB |
| `services/email-service` | `EmailTemplateRendererTest` | Templates HTML: Ley 1581 en bienvenida, colores por resultado, interpolación |

---

## 11. Mapa de puertos (local)

### Docker Compose

| Puerto | Servicio | URL |
|---|---|---|
| 8080 | web-ui (JSF) | http://localhost:8080 |
| 8081 | user-service | http://localhost:8081/api/v1 |
| 8082 | course-service | http://localhost:8082/api/v1 |
| 8083 | assessment-service | http://localhost:8083/api/v1 |
| 8084 | collaboration-service + WebSocket | http://localhost:8084/api/v1 · ws://localhost:8084/ws |
| 8085 | analytics-service | http://localhost:8085/api/v1 |
| 8086 | email-service | http://localhost:8086/health |
| 8025 | MailHog UI | http://localhost:8025 |
| 15672 | RabbitMQ Management | http://localhost:15672 |
| 5432 | PostgreSQL (directo, solo debug) | localhost:5432 |
| 6379 | Redis (directo, solo debug) | localhost:6379 |
| 5672 | RabbitMQ AMQP | localhost:5672 |

### Minikube (requieren port-forward activo)

```bash
kubectl port-forward svc/web-ui          8080:8080 -n puj-platform
kubectl port-forward svc/user-service    8081:8080 -n puj-platform
kubectl port-forward svc/course-service  8082:8080 -n puj-platform
kubectl port-forward svc/analytics-service 8085:8080 -n puj-platform
kubectl port-forward svc/mailhog         8025:8025 -n puj-platform
kubectl port-forward svc/rabbitmq        15672:15672 -n puj-platform
```

O acceder directamente por Ingress si `/etc/hosts` tiene `platform.local` → `http://platform.local`

> En producción (Kubernetes), ninguno de estos puertos es accesible desde afuera. Todo el tráfico externo entra únicamente por NGINX en `platform.puj.edu.co:443`, que enruta exclusivamente hacia `web-ui`. Los servicios de negocio son ClusterIP — inaccesibles desde internet por diseño de red, no solo por configuración.
