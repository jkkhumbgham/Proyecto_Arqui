# Plataforma de Aprendizaje Adaptativo y Colaborativo
**Pontificia Universidad Javeriana · 2026**  
Equipo: Laura Juliana Garzón Arias · Juan Camilo Alba Castro · Carlos Manuel Villegas Ruiz · Arley Bernal Muñetón

---

## Tabla de contenidos

1. [Qué es esto](#1-qué-es-esto)
2. [Correr en local](#2-correr-en-local)
3. [Del local a producción](#3-del-local-a-producción)
4. [Estructura del repositorio](#4-estructura-del-repositorio)
5. [Decisiones de implementación](#5-decisiones-de-implementación)

---

## 1. Qué es esto

Un LMS (Learning Management System) con dos características que los LMS tradicionales no tienen:

- **Motor adaptativo determinístico**: cuando un estudiante termina una evaluación, el sistema compara su nota contra un umbral que el docente configuró. Si queda por debajo entrega material de refuerzo; si supera o iguala desbloquea el siguiente contenido. No hay ML, no hay caja negra: el comportamiento es predecible y auditable.
- **Colaboración real entre pares**: grupos de estudio con chat en tiempo real (WebSocket), foros de discusión por curso, e identificación automática de tutores (estudiantes con promedio > 85%).

La plataforma es para instituciones colombianas, lo que impone cumplir la **Ley 1581 de 2012** sobre tratamiento de datos personales.

---

## 2. Correr en local

### Prerrequisitos

| Herramienta | Versión mínima | Para qué |
|---|---|---|
| Docker + Docker Compose | 24 / 2.24 | Único requisito obligatorio para correr el stack |
| openssl | cualquiera | Generar par RSA para JWT (lo usa el script de setup) |
| Java 17 + Maven 3.9 | — | Solo si compilas los servicios Java fuera de Docker |
| .NET SDK 8 | — | Solo si compilas analytics-service fuera de Docker |

### Paso 1 — Configuración inicial (solo una vez)

```bash
bash scripts/setup-env.sh
```

El script crea el archivo `.env` y genera automáticamente el par RSA 2048 para JWT. La salida indica qué variables están listas y cuáles quedan pendientes.

Las variables de AWS y SMTP **son opcionales en local**:

| Variable | Qué pasa si se deja vacía |
|---|---|
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | Solo fallan los endpoints de subida de archivos a S3; todo lo demás funciona |
| `REGISTRY` | Solo para push a ECR (producción); irrelevante en local |
| `SMTP_USER` / `SMTP_PASSWORD` | En local el email-service usa MailHog automáticamente; nunca se envían correos reales |

### Paso 2 — Levantar el stack

```bash
docker compose up -d
```

Los servicios Java (WildFly) tardan ~60 segundos en arrancar. Para seguir el progreso:

```bash
docker compose logs -f user-service
# Esperar: "WildFly Full 31.0.0.Final started"
```

### Paso 3 — Verificar que todo está vivo

```bash
curl -s http://localhost:8081/api/v1/auth/me   # → {"error":"UNAUTHORIZED",...}
curl -s http://localhost:8085/health            # → {"status":"UP"}
curl -s http://localhost:8086/health            # → {"status":"UP"}
```

### Accesos rápidos

| URL | Qué es |
|---|---|
| http://localhost:8080 | UI web (JSF) |
| http://localhost:8025 | MailHog — todos los correos enviados en local |
| http://localhost:15672 | RabbitMQ management (`puj_rabbit` / `rabbit_secret`) |
| http://localhost:8081/api/v1 | user-service API |
| http://localhost:8082/api/v1 | course-service API |
| http://localhost:8083/api/v1 | assessment-service API |
| http://localhost:8084/api/v1 | collaboration-service API + WebSocket |
| http://localhost:8085/api/v1 | analytics-service API |

> Para instrucciones detalladas (pruebas con curl, base de datos, WebSocket, RabbitMQ, Kubernetes) ver **[manual.md](manual.md)**.

---

## 3. Del local a producción

El SAD y SRS plantean un despliegue en **AWS EKS** con PostgreSQL, Redis y RabbitMQ gestionados. Esta sección describe exactamente qué cambia y en qué orden habilitarlo.

### Diferencias entre entornos

| Aspecto | Local (docker compose) | Producción (AWS EKS) |
|---|---|---|
| Base de datos | PostgreSQL directo en contenedor (puerto 5432) | PostgreSQL 15 + PgBouncer sidecar (puerto 6432) |
| Conexiones DB | Directas desde cada servicio | Pool de conexiones vía PgBouncer en modo `transaction` |
| Correo | MailHog local (puerto 1025, sin auth ni TLS) | SMTP institucional con `SMTP_AUTH=true` y `SMTP_STARTTLS=true` (puerto 587) |
| Archivos de curso | S3 no disponible | AWS S3 con URLs prefirmadas (15 min PUT, 1 h GET) |
| HTTPS / dominio | Sin TLS, acceso por puertos | AWS ALB + ACM certificate en `platform.puj.edu.co` |
| Escalado | 1 réplica por servicio | HPA: 2–8 réplicas por CPU; `terminationGracePeriodSeconds: 60` en collaboration-service |
| Secretos | Archivo `.env` en la máquina | Kubernetes Secret `platform-secrets` en namespace `puj-platform` |
| Imágenes Docker | Build local por docker compose | Build + push a ECR con `scripts/ecr-push.sh` |
| Sticky sessions (JSF) | Innecesario (1 réplica) | `sessionAffinity: ClientIP` en el Service de web-ui |
| Componentes que no existen en local | — | PgBouncer, ALB Ingress Controller, HPA, Redis Pub/Sub multi-réplica |

### Pasos para desplegar en producción

#### 1. Completar el `.env` con valores reales

Después de `bash scripts/setup-env.sh`, editar `.env` y llenar:

```
AWS_ACCESS_KEY_ID=AKIA...
AWS_SECRET_ACCESS_KEY=...
REGISTRY=123456789.dkr.ecr.us-east-1.amazonaws.com
SMTP_USER=no-reply@puj.edu.co
SMTP_PASSWORD=...
```

#### 2. Crear el cluster EKS

```bash
eksctl create cluster --name puj-platform --region us-east-1 --nodes 3 --node-type t3.medium
```

PostgreSQL, Redis y RabbitMQ pueden usarse como deployments K8s (ya incluidos en `infra/k8s/`) o como servicios gestionados de AWS (RDS, ElastiCache, AmazonMQ).

#### 3. Crear secretos en Kubernetes

```bash
kubectl create namespace puj-platform

kubectl create secret generic platform-secrets \
  --namespace puj-platform \
  --from-literal=DB_USER=puj_admin \
  --from-literal=DB_PASSWORD=<contraseña_segura> \
  --from-literal=REDIS_PASSWORD=<contraseña_redis> \
  --from-literal=RABBITMQ_USER=puj_rabbit \
  --from-literal=RABBITMQ_PASSWORD=<contraseña_rabbit> \
  --from-literal=JWT_PRIVATE_KEY="$(cat jwt_private.pem)" \
  --from-literal=JWT_PUBLIC_KEY="$(cat jwt_public.pem)" \
  --from-literal=AWS_ACCESS_KEY_ID=AKIA... \
  --from-literal=AWS_SECRET_ACCESS_KEY=... \
  --from-literal=SMTP_HOST=smtp.puj.edu.co \
  --from-literal=SMTP_PORT=587 \
  --from-literal=SMTP_USER=no-reply@puj.edu.co \
  --from-literal=SMTP_PASSWORD=...
```

> `jwt_private.pem` y `jwt_public.pem` los generó `setup-env.sh` en la raíz del repositorio. **Nunca commitear estos archivos.**

#### 4. Build y push de imágenes a ECR

```bash
bash scripts/ecr-push.sh          # usa tag :latest
bash scripts/ecr-push.sh v1.0.0   # usa tag :v1.0.0 y :latest
```

El script hace login a ECR, construye las 7 imágenes y hace push. También reemplaza el placeholder `REGISTRY` en los `deployment.yaml`.

#### 5. Desplegar

```bash
kubectl apply -k infra/k8s/
kubectl get pods -n puj-platform -w   # esperar a que todos estén Running
kubectl get ingress -n puj-platform   # obtener la IP del ALB
```

El orden lo controla `infra/k8s/kustomization.yaml`: namespace → secrets → configmaps → postgres (con PgBouncer) → redis → rabbitmq → servicios → ingress.

#### 6. Apuntar el DNS

Una vez que el Ingress tenga IP asignada (~2 min), crear un registro DNS tipo `A` o `CNAME` que apunte `platform.puj.edu.co` a esa IP.

### Componentes del SAD que solo aplican en producción

- **PgBouncer**: en local postgres corre directo (puerto 5432). En K8s corre como sidecar en el pod de postgres (puerto 6432) — los manifiestos ya lo incluyen en `infra/k8s/postgres/deployment.yaml`.
- **Redis Pub/Sub para WebSocket**: con 1 réplica en local no se necesita. En K8s, cuando el HPA escala collaboration-service a 2+ réplicas, los pods se coordinan vía Redis para entregar mensajes a sesiones WebSocket en otros pods.
- **HPA y `terminationGracePeriodSeconds: 60`**: solo tienen efecto con múltiples réplicas en K8s.
- **Disponibilidad 99.5%**: requiere HPA + readiness probes (ya configurados) + RDS Multi-AZ o el PVC con `ReadWriteOnce` del deployment de postgres.
- **Cumplimiento Ley 1581**: el consentimiento explícito y el soft-delete funcionan igual en ambos entornos. En producción, los datos deben residir en región América (`us-east-1` ya está configurado en los manifiestos).

---

## 4. Estructura del repositorio

```
.
├── libs/
│   ├── security/          JWT RS256, RBAC, blacklist Redis — compartido por todos los servicios Java
│   └── events/            Contratos de eventos RabbitMQ (BaseEvent y subtipos)
│
├── services/
│   ├── user-service/      Autenticación, usuarios, roles        → WildFly 31 / Jakarta EE 10
│   ├── course-service/    Cursos, módulos, lecciones, S3         → WildFly 31 / Jakarta EE 10
│   ├── assessment-service/Evaluaciones, calificación, adaptativo → WildFly 31 / Jakarta EE 10
│   ├── collaboration-service/ Foros, grupos, WebSocket          → WildFly 31 / Jakarta EE 10
│   ├── analytics-service/ Métricas, dashboards, exportación     → .NET 8 / ASP.NET Core
│   └── email-service/     Consumer SMTP, templates HTML         → WildFly 31 / Jakarta EE 10
│
├── frontend/
│   └── web-ui/            JSF (MPA), un bean por pantalla        → WildFly 31 / Jakarta EE 10
│
├── infra/
│   ├── db/init.sql        Crea los 5 schemas de PostgreSQL
│   ├── rabbitmq/          Exchange topic + colas con DLX
│   ├── wildfly/           Módulo JDBC para PostgreSQL en WildFly
│   └── k8s/               Un directorio por servicio con Deployment + Service + HPA
│
├── scripts/
│   ├── setup-env.sh       Configuración inicial: genera par RSA y crea .env
│   └── ecr-push.sh        Build y push de todas las imágenes a ECR
│
├── .env.example           Plantilla de variables de entorno (segura para git)
├── manual.md              Instrucciones detalladas de ejecución y pruebas
├── docker-compose.yml     Entorno local completo
└── pom.xml                Parent Maven multi-módulo
```

---

## 5. Decisiones de implementación

### Service-Based en lugar de Microservicios

Con un equipo de 4 personas, los microservicios añaden trabajo que no aporta valor: un registry de servicios, circuit breakers distribuidos, tracing entre 15 deploys, gestión de N bases de datos separadas. Service-Based da los mismos beneficios de despliegue independiente con mucho menos overhead. El límite elegido fue que cada servicio tenga su propia base de datos lógica (schema), no física — lo que elimina la coordinación de migraciones entre equipos pero evita tener que operar 5 instancias de PostgreSQL.

### WildFly 31 en lugar de Spring Boot para los servicios Java

Spring Boot es más popular, pero tiene un problema en este proyecto: las dependencias transitivas. Con 5 servicios compartiendo librerías internas (`libs/security`, `libs/events`), Spring Boot añade autoconfiguration que puede entrar en conflicto entre servicios. WildFly con CDI es explícito: nada se configura solo, lo que hace el comportamiento más predecible. Además, WildFly implementa Jakarta EE de forma más fiel, lo que facilita que los contratos de las interfaces sean estables.

### .NET 8 para analytics-service

El analytics-service tiene un patrón de trabajo diferente al resto: no sirve requests síncronos de usuarios, sino que consume eventos de una cola y los agrega. La combinación de MassTransit (el mejor framework .NET para RabbitMQ, con competing consumers nativo) con EF Core Minimal API resultó en menos código que el equivalente Jakarta EE. La clave fue que MassTransit sabe automáticamente cómo distribuir mensajes entre réplicas del servicio sin configuración extra.

### JSF en lugar de React/Vue para la UI

JSF es server-side rendering: el estado vive en el servidor, el browser solo recibe HTML. Esto simplifica radicalmente la seguridad porque el token JWT nunca sale del servidor hacia el browser (no hay localStorage, no hay riesgo de XSS con tokens). La contrapartida es que JSF necesita sticky sessions para funcionar con múltiples réplicas, lo cual se resolvió con `sessionAffinity: ClientIP` en el Service de Kubernetes.

### Redis Pub/Sub para escalar WebSocket

El problema con WebSocket y múltiples réplicas es que cada pod tiene su propio mapa de sesiones en memoria. Si el usuario A está en el Pod 1 y el usuario B en el Pod 2, y A envía un mensaje, B nunca lo recibe porque el Pod 1 no sabe que B existe. Redis Pub/Sub resuelve esto: cada pod publica el mensaje en un canal Redis y todos los pods suscritos lo reciben y lo entregan a sus sesiones locales. El trade-off es una latencia extra de ~1ms por el salto adicional, que es completamente aceptable para un chat educativo.

### PgBouncer como sidecar en modo transaction

Con 6 servicios Java (5 de negocio + email) cada uno con un pool JPA de ~10 conexiones, más 2-8 réplicas por servicio, podríamos tener hasta 80 conexiones simultáneas a PostgreSQL. PostgreSQL 15 tiene un límite de 100 por defecto y cada conexión cuesta ~5MB de RAM en el servidor. PgBouncer en modo `transaction` multiplexea estas conexiones: los 80 clientes comparten 20 conexiones reales. El modo `transaction` (en lugar de `session`) fue elegido porque los servicios no usan `SET LOCAL` ni advisory locks que requieran que una sesión sea continua entre transacciones.

### JWT RS256 en lugar de HS256

HS256 usa una clave simétrica: todos los servicios que verifican tokens necesitan conocer el secreto. Si hay un bug en cualquier servicio y ese secreto se filtra, un atacante puede generar tokens arbitrarios. RS256 usa par asimétrico: solo el user-service tiene la clave privada (y puede firmar), el resto solo tienen la pública (y solo pueden verificar). Un compromiso en course-service no permite generar tokens.

### Soft delete en lugar de DELETE físico

Los datos académicos tienen valor histórico: si un estudiante completa un curso y luego se da de baja, los registros de inscripción y evaluación deben seguir existiendo para los reportes del DIRECTOR. Con DELETE físico, borrar un usuario rompería los dashboards históricos. Soft delete (`deleted_at`) preserva la integridad referencial sin necesidad de ON DELETE SET NULL en todas las tablas. El costo es que todas las queries deben filtrar por `deleted_at IS NULL`, lo que se garantiza a nivel de repositorio.

### Jakarta Mail (Angus Mail) para el email-service

El email-service usa `jakarta.mail` con Angus Mail como implementación de referencia. La configuración del Session se controla por variables de entorno:

- **Local (MailHog)**: `SMTP_AUTH=false`, `SMTP_STARTTLS=false`, host `mailhog`, puerto `1025`. MailHog no soporta autenticación ni TLS, por lo que ambas opciones se deshabilitan. Todos los correos quedan capturados en `http://localhost:8025`.
- **Producción (SMTP institucional)**: `SMTP_AUTH=true`, `SMTP_STARTTLS=true`, host y credenciales del servidor institucional. Los manifiestos K8s de `email-service` ya los tienen configurados.

El mismo código funciona en ambos entornos; solo cambian las variables de entorno.

### MailHog en entorno local

MailHog es un servidor SMTP falso que captura todos los correos sin enviarlos y los muestra en una UI web. Esto evita enviar correos reales durante el desarrollo (lo que podría resultar en spam a usuarios reales o cargos en la cuenta SMTP). El email-service conecta a MailHog en local (puerto 1025) y al SMTP institucional en producción — la misma variable de entorno `SMTP_HOST` controla ambos sin cambiar código.

### Sin HPA en email-service

Los otros servicios tienen HPA porque su cuello de botella es CPU o RAM: más réplicas = más capacidad. El email-service está limitado por el rate limit del servidor SMTP externo (típicamente 100-500 correos/minuto para cuentas institucionales). Añadir más réplicas solo causaría que múltiples pods intenten enviar simultáneamente y empiecen a recibir `Too Many Connections` del SMTP. La solución correcta para alto volumen de correos es RabbitMQ con prefetch=1 en una sola réplica activa, que ya está configurado.

### DLX (Dead Letter Exchange) en RabbitMQ

Cuando un mensaje falla después de 3 reintentos (el consumer hace NACK con requeue=false), va automáticamente al exchange `platform.dlx`, que lo enruta a `dead.letter.queue`. Esto permite inspeccionar mensajes fallidos sin perderlos, diagnosticar problemas de serialización o SMTP, y re-procesar manualmente si es necesario. Sin DLX, los mensajes que fallan se pierden para siempre.

### QuestPDF para exportación PDF

La alternativa obvia era iText 7, pero su licencia AGPL obliga a liberar el código fuente de cualquier proyecto que lo use. QuestPDF usa licencia MIT sin restricciones. Su API fluent también produce código más legible: en lugar de coordinar manualmente posiciones X,Y en puntos, se trabaja con filas, columnas y celdas.

### terminationGracePeriodSeconds: 60 en collaboration-service

Cuando Kubernetes mata un pod (por un deploy nuevo, por un scale-down, o por fallo), el proceso recibe SIGTERM. Sin un grace period, el pod muere instantáneamente y todas las conexiones WebSocket activas se cortan. Con 60 segundos, el pod termina de entregar los mensajes pendientes y los clientes WebSocket tienen tiempo de reconectar al nuevo pod. Los 60 segundos vienen de considerar que la mayoría de los mensajes de chat son instantáneos y que 60s es el timeout estándar de los proxies HTTP.
