# Plataforma de Aprendizaje Adaptativo y Colaborativo
**Pontificia Universidad Javeriana · 2026**  
Equipo: Laura Juliana Garzón Arias · Juan Camilo Alba Castro · Carlos Manuel Villegas Ruiz · Arley Bernal Muñetón

> Instrucciones de configuración, ejecución, pruebas y despliegue en **[manual.md](manual.md)**.

---

## Tabla de contenidos

1. [Qué es esto](#1-qué-es-esto)
2. [Arquitectura del sistema](#2-arquitectura-del-sistema)
3. [Correr en local — resumen rápido](#3-correr-en-local--resumen-rápido)
4. [Del local a producción](#4-del-local-a-producción)
5. [Estructura del repositorio](#5-estructura-del-repositorio)
6. [Decisiones de implementación](#6-decisiones-de-implementación)

---

## 1. Qué es esto

Un LMS (Learning Management System) con dos características que los LMS tradicionales no tienen:

- **Motor adaptativo determinístico**: cuando un estudiante termina una evaluación, el sistema compara su nota contra un umbral que el docente configuró. Si queda por debajo entrega material de refuerzo; si supera o iguala desbloquea el siguiente contenido. No hay ML, no hay caja negra: el comportamiento es predecible y auditable.
- **Colaboración real entre pares**: grupos de estudio con chat en tiempo real (WebSocket), foros de discusión por curso, e identificación automática de tutores (estudiantes con promedio > 85%).

La plataforma es para instituciones colombianas, lo que impone cumplir la **Ley 1581 de 2012** sobre tratamiento de datos personales.

---

## 2. Arquitectura del sistema

```
                         ┌────────────────────────────────────────────┐
                         │                 NAVEGADOR                  │
                         │           http://localhost:8080            │
                         └──────────────────────┬─────────────────────┘
                                                │ HTTP :8080
                                                ▼
        ┌───────────────────────────────────────────────────────────────────────────────────┐
        │  FRONTEND  ·  WildFly 31  ·  :8080                                                │
        │  web-ui  ·  JSF 4.0  ·  Mojarra                                                   │
        └──────┬────────────┬────────────┬────────────┬────────────┬────────────────────────┘
               │HTTP        │HTTP        │HTTP        │HTTP        │HTTP
               │            │            │            │            │
               ▼            ▼            ▼            ▼            ▼
  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐
  │ user-service │  │course-service│  │ assessment-  │  │  collab-     │  │   analytics-service      │
  │    :8081     │  │    :8082     │  │   service    │  │   service    │  │   ASP.NET Core 8         │
  │  WildFly 31  │  │  WildFly 31  │  │    :8083     │  │    :8084     │  │   MassTransit 8  :8085   │
  └──────┬───────┘  └──────┬───────┘  │  WildFly 31  │  │  WildFly 31  │  └────────┬─────────────────┘
         │                 │          └──────┬───────┘  └──────┬───────┘           │
         │           HTTP──►user-svc         │                 │                   │ consume
         │                          HTTP──►user-svc      HTTP──►user-svc           │ (RabbitMQ)
         │                          HTTP──►course-svc                              │
         │                                                                         │ EF Core
  ┌──────────────────────────────────────────────────────────────────────┐         ▼
  │  email-service  ·  :8086  ·  WildFly 31                             │  ┌──────────────────────────┐
  │  consume RabbitMQ (email.#)  ──►  envía a MailHog :1025 SMTP        │  │  analytics_db            │
  └──────────────────────────────────────────────────────────────────────┘  │  PostgreSQL 15  ·  :5432 │
                                                                             │  schema: analytics       │
  ─────────────────────────── JDBC (todos los servicios Java) ──────────    └──────────────────────────┘
         │
         ▼
  ┌───────────────────────────────────────────────────────────────────────────────────────────┐
  │  PostgreSQL 15  ·  :5432  ·  base de datos: learning_platform                            │
  │                                                                                           │
  │  ├── schema: users          ◄──  user-service                                            │
  │  ├── schema: courses        ◄──  course-service                                          │
  │  ├── schema: assessments    ◄──  assessment-service                                      │
  │  └── schema: collaboration  ◄──  collab-service                                          │
  └───────────────────────────────────────────────────────────────────────────────────────────┘

  ┌───────────────────────────────────────────────────────────────────────────────────────────┐
  │  RabbitMQ 3.13  ·  :5672  ·  vhost: puj_vhost                                            │
  │                                                                                           │
  │  exchange: platform.events (topic)                                                        │
  │      │                                                                                    │
  │      ├── cola: analytics.results    routing: analytics.#  ──►  analytics-service consume  │
  │      │         UserRegistered · CourseEnrolled · LessonCompleted · AssessmentSubmitted    │
  │      │                                                                                    │
  │      ├── cola: email.notifications  routing: email.#      ──►  email-service consume      │
  │      │                                                                                    │
  │      └── dead.letter.queue  (DLX: platform.dlx)  ◄──  NACK × 3 de cualquier cola        │
  │                                                                                           │
  │  Publicadores (envelope MassTransit v8  ·  application/vnd.masstransit+json):             │
  │  user-svc    ──►  UserRegistered                                                          │
  │  course-svc  ──►  CourseEnrolled  ·  LessonCompleted                                     │
  │  assess-svc  ──►  AssessmentSubmitted                                                     │
  └───────────────────────────────────────────────────────────────────────────────────────────┘

  ┌───────────────────────────────────────────────────────────────────────────────────────────┐
  │  Redis 7  ·  :6379                                                                        │
  │                                                                                           │
  │  user-svc    ──►  token blacklist  ·  almacén de refresh tokens                          │
  │  course-svc  ──►  caché de búsquedas de cursos  (TTL 300 s)                              │
  │  assess-svc  ──►  caché de resultados de evaluaciones                                    │
  │  collab-svc  ──►  pub/sub para sincronizar mensajes WebSocket entre réplicas             │
  └───────────────────────────────────────────────────────────────────────────────────────────┘

  ┌──────────────────────────────────────────────────────┐  ┌────────────────────────────────────────┐
  │  MinIO  ·  :9000  ·  consola: :9001                  │  │  MailHog  ·  :1025 SMTP  ·  :8025 UI  │
  │                                                      │  │                                        │
  │  bucket: course-files                                │  │  email-service envía aquí por SMTP     │
  │    course-svc escribe  ·  web-ui lee (URL pública)   │  │  todos los correos quedan capturados   │
  │                                                      │  │  sin enviarse en entorno local         │
  │  bucket: certificates                                │  └────────────────────────────────────────┘
  │    analytics-svc escribe PDFs  ·  web-ui descarga    │
  └──────────────────────────────────────────────────────┘
```

---

## 3. Correr en local — resumen rápido

```bash
# 1. Generar claves JWT y crear .env (solo la primera vez)
bash scripts/setup-env.sh

# 2. Levantar todo
docker compose up -d
```

En local, el almacenamiento de archivos usa **MinIO** (S3-compatible, sin cuenta AWS) y los correos quedan capturados en **MailHog** — http://localhost:8025 — sin enviarse realmente. No se necesitan credenciales externas para correr el stack completo.

Ver **[manual.md](manual.md)** para la guía completa: puertos, pruebas con curl, base de datos, WebSocket, RabbitMQ y despliegue en Kubernetes.

---

## 4. Del local a producción

El SAD y SRS plantean un despliegue en AWS EKS. Esta tabla resume qué cambia entre entornos; los pasos exactos están en el [manual.md § 6](manual.md#6-correr-en-kubernetes-eks) y [§ 7 — Del local a producción](manual.md#7-del-local-a-producción-sadsr).

| Aspecto | Local (`docker compose`) | Producción (AWS EKS) |
|---|---|---|
| Base de datos | PostgreSQL directo (puerto 5432) | PostgreSQL 15 + PgBouncer sidecar (puerto 6432) |
| Correo | MailHog local (sin auth ni TLS) | SMTP institucional con `SMTP_AUTH=true` y `SMTP_STARTTLS=true` |
| Archivos de curso | S3 no disponible | AWS S3 con URLs prefirmadas |
| HTTPS / dominio | Sin TLS, acceso directo por puertos | AWS ALB + ACM certificate en `platform.puj.edu.co` |
| Escalado | 1 réplica por servicio | HPA: 2–8 réplicas; `terminationGracePeriodSeconds: 60` en collaboration-service |
| Secretos | Archivo `.env` en la máquina | Kubernetes Secret `platform-secrets` en namespace `puj-platform` |
| Imágenes Docker | Build local | Push a ECR con `scripts/ecr-push.sh` |
| Sticky sessions (JSF) | Innecesario (1 réplica) | `sessionAffinity: ClientIP` en el Service de web-ui |

---

## 5. Estructura del repositorio

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
│   └── email-service/     Consumer RabbitMQ + SMTP Jakarta Mail → WildFly 31 / Jakarta EE 10
│
├── frontend/
│   └── web-ui/            JSF (MPA), un bean por pantalla        → WildFly 31 / Jakarta EE 10
│
├── infra/
│   ├── db/init.sql        Crea los 4 schemas Java en learning_platform + base de datos analytics_db
│   ├── rabbitmq/          Exchange topic + colas con DLX
│   ├── wildfly/           Módulo JDBC para PostgreSQL en WildFly
│   └── k8s/               Un directorio por servicio con Deployment + Service + HPA
│
├── scripts/
│   ├── setup-env.sh       Configuración inicial: genera par RSA y crea .env
│   └── ecr-push.sh        Build y push de todas las imágenes a ECR
│
├── .env.example           Plantilla de variables de entorno (segura para git)
├── manual.md              Instrucciones completas de ejecución y despliegue
├── docker-compose.yml     Entorno local completo
└── pom.xml                Parent Maven multi-módulo
```

---

## 6. Decisiones de implementación

### Service-Based en lugar de Microservicios

Con un equipo de 4 personas, los microservicios añaden trabajo que no aporta valor: un registry de servicios, circuit breakers distribuidos, tracing entre 15 deploys, gestión de N bases de datos separadas. Service-Based da los mismos beneficios de despliegue independiente con mucho menos overhead. El límite elegido fue que cada servicio Java tenga su propia base de datos lógica (schema) dentro de `learning_platform`, no física — lo que elimina la coordinación de migraciones entre equipos pero evita tener que operar 5 instancias de PostgreSQL. La excepción es analytics-service (.NET), que usa una base de datos física separada (`analytics_db`) dentro del mismo servidor para aislar su esquema de EF Core del esquema Hibernate de los servicios Java.

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

### Jakarta Mail para el email-service

El email-service usa `jakarta.mail` con Angus Mail como implementación. La sesión SMTP se configura por variables de entorno, lo que permite que el mismo binario funcione en ambos entornos sin cambiar código:

- **Local**: `SMTP_AUTH=false`, `SMTP_STARTTLS=false`, apuntando a MailHog en `mailhog:1025`
- **Producción**: `SMTP_AUTH=true`, `SMTP_STARTTLS=true`, apuntando al SMTP institucional en el puerto 587

### MailHog en entorno local

MailHog es un servidor SMTP falso que captura todos los correos sin enviarlos y los muestra en una UI web. Esto evita enviar correos reales durante el desarrollo. El email-service conecta a MailHog en local (puerto 1025) y al SMTP institucional en producción — la misma variable `SMTP_HOST` controla ambos.

### Sin HPA en email-service

Los otros servicios tienen HPA porque su cuello de botella es CPU o RAM: más réplicas = más capacidad. El email-service está limitado por el rate limit del servidor SMTP externo (típicamente 100-500 correos/minuto para cuentas institucionales). Añadir más réplicas solo causaría que múltiples pods intenten enviar simultáneamente y empiecen a recibir `Too Many Connections` del SMTP. La solución correcta para alto volumen de correos es RabbitMQ con prefetch=1 en una sola réplica activa, que ya está configurado.

### terminationGracePeriodSeconds: 60 en collaboration-service

Cuando Kubernetes mata un pod (por un deploy nuevo, por un scale-down, o por fallo), el proceso recibe SIGTERM. Sin un grace period, el pod muere instantáneamente y todas las conexiones WebSocket activas se cortan. Con 60 segundos, el pod termina de entregar los mensajes pendientes y los clientes WebSocket tienen tiempo de reconectar al nuevo pod. Los 60 segundos vienen de considerar que la mayoría de los mensajes de chat son instantáneos y que 60s es el timeout estándar de los proxies HTTP.

### DLX (Dead Letter Exchange) en RabbitMQ

Cuando un mensaje falla después de 3 reintentos (el consumer hace NACK con requeue=false), va automáticamente al exchange `platform.dlx`, que lo enruta a `dead.letter.queue`. Esto permite inspeccionar mensajes fallidos sin perderlos, diagnosticar problemas de serialización o SMTP, y re-procesar manualmente si es necesario. Sin DLX, los mensajes que fallan se pierden para siempre.

### QuestPDF para exportación PDF

La alternativa obvia era iText 7, pero su licencia AGPL obliga a liberar el código fuente de cualquier proyecto que lo use. QuestPDF usa licencia MIT sin restricciones. Su API fluent también produce código más legible: en lugar de coordinar manualmente posiciones X,Y en puntos, se trabaja con filas, columnas y celdas.
