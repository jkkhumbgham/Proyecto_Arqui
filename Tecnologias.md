# Tecnologías del Proyecto

## Resumen de la arquitectura

Plataforma de Aprendizaje Adaptativo y Colaborativo (PUJ 2026) construida como **arquitectura de microservicios**. Seis servicios backend independientes, un frontend monolítico JSF, y cinco componentes de infraestructura orquestados con Docker Compose.

---

## Frontend

| Tecnología | Versión | Uso |
|---|---|---|
| Jakarta Faces (JSF / Mojarra) | 4.0 | Framework de componentes UI (server-side rendering) |
| WildFly | 31.0.0.Final | Servidor de aplicaciones Jakarta EE que sirve el WAR |
| Java | 17 | Lenguaje de los Managed Beans (`@RequestScoped`, `@SessionScoped`) |
| Jackson Databind | 2.17.1 | Deserialización JSON en las llamadas HTTP a los microservicios |
| Java HttpClient | JDK 11+ | Cliente HTTP nativo para llamar a los microservicios desde los beans |
| Maven | 3.9.6 | Build y empaquetado del WAR |

**Puerto:** `8080`  
**Acceso:** `http://localhost:8080`

---

## Servicios Backend (Java)

Todos usan el mismo stack base:

| Tecnología | Versión | Uso |
|---|---|---|
| Jakarta EE | 10.0.0 | API base (JPA, JAX-RS, CDI, Bean Validation) |
| MicroProfile | 6.1 | OpenAPI, Health, Config |
| WildFly | 31.0.0.Final | Runtime de despliegue |
| Java | 17 | Lenguaje |
| JAX-RS (RESTEasy) | 6.2.7 | Endpoints REST |
| JPA / Hibernate | incluido en WildFly | ORM y acceso a base de datos |
| Jackson Databind | 2.17.1 | Serialización/deserialización JSON |
| JJWT | 0.12.5 | Generación y validación de JWT (RS256) |
| jBCrypt | 0.4 | Hash de contraseñas |
| Jedis | 5.1.2 | Cliente Java para Redis |
| RabbitMQ Java Client | 5.21.0 | Publicación de eventos a la cola |
| PostgreSQL JDBC | 42.7.3 | Driver de base de datos |
| Maven | 3.9.6 | Build |

### Librerías internas compartidas

| Librería | Descripción |
|---|---|
| `libs/security` | RBAC: anotación `@RequiresRole`, interceptor CDI, `AuthenticatedUser` inyectable, filtro JWT |
| `libs/events` | DTOs de eventos compartidos entre servicios (`AssessmentSubmittedEvent`, `CourseEnrolledEvent`, `UserRegisteredEvent`) |

---

## Servicio de Analíticas (diferente stack)

| Tecnología | Versión | Uso |
|---|---|---|
| .NET | 8.0 | Runtime y lenguaje (C#) |
| ASP.NET Core | 8.0 | Minimal API para endpoints REST |
| Entity Framework Core | 8.0.4 | ORM |
| Npgsql EF Core | 8.0.4 | Proveedor PostgreSQL para EF Core |
| MassTransit | 8.2.3 | Consumer de mensajes RabbitMQ |
| MassTransit.RabbitMQ | 8.2.3 | Transporte RabbitMQ para MassTransit |
| JWT Bearer Auth | 8.0.4 | Validación del JWT emitido por user-service |
| Swashbuckle (Swagger) | 6.6.2 | Documentación OpenAPI |
| QuestPDF | 2024.3.4 | Generación de reportes en PDF |

**Puerto:** `8085`

---

## Infraestructura

| Componente | Imagen Docker | Versión | Puerto | Descripción |
|---|---|---|---|---|
| PostgreSQL | `postgres:15-alpine` | 15 | 5432 | Un servidor, **dos bases de datos**: `learning_platform` (4 esquemas Java: `users`, `courses`, `assessments`, `collaboration`) y `analytics_db` (esquema `analytics`, exclusivo del analytics-service .NET) |
| Redis | `redis:7-alpine` | 7 | 6379 | Caché de sesiones y tokens. Auth `redis_secret`. Límite 256 MB, política `allkeys-lru` |
| RabbitMQ | `rabbitmq:3.13-management-alpine` | 3.13 | 5672 (AMQP) / 15672 (Admin UI) | Mensajería asíncrona entre servicios. Virtual host `puj_vhost` |
| MinIO | `minio/minio:latest` | latest | 9000 (API) / 9001 (Console) | Almacenamiento de objetos S3-compatible para archivos de cursos. Bucket `course-files` público |
| MailHog | `mailhog/mailhog:latest` | latest | 1025 (SMTP) / 8025 (Web UI) | Servidor SMTP local que captura correos sin enviarlos. Reemplaza SES en local |

---

## Flujo de mensajería (RabbitMQ)

Los servicios Java publican eventos al exchange `platform.events` (topic) usando `EventPublisher` de `libs/events`. El analytics-service los consume con MassTransit desde la cola `analytics.results`:

```
user-service        → UserRegisteredEvent      (analytics.user_registered)      → analytics-service
course-service      → CourseEnrolledEvent      (analytics.course_enrolled)      → analytics-service
assessment-service  → AssessmentSubmittedEvent (analytics.assessment_submitted) → analytics-service
course-service      → LessonCompletedEvent     (analytics.lesson_completed)     → analytics-service
```

El `EventPublisher` envuelve los eventos en el formato MassTransit v8:
```json
{
  "messageType": ["urn:message:Puj.Analytics.Messages:CourseEnrolledMessage"],
  "message": { ... },
  "messageId": "...",
  "sentTime": "..."
}
```
con `Content-Type: application/vnd.masstransit+json`.

Mensajes que fallan tras 3 reintentos van al DLX `platform.dlx` → cola `dead.letter.queue`.

## Job de snapshots mensuales (analytics-service)

El `MonthlySnapshotJob` (`BackgroundService` .NET) corre automáticamente:
- **Al arrancar**: genera el snapshot del mes anterior si no existe (recuperación de gaps)
- **El día 1 de cada mes a las 00:05 UTC**: agrega métricas de ese mes en `analytics.monthly_snapshots`

Campos del snapshot: `total_users`, `total_enrollments`, `total_submissions`, `total_courses`, `avg_score`, `pass_rate`, `new_users_this_month`, `new_enrollments_this_month`, `new_submissions_this_month`.

Endpoint: `GET /api/v1/analytics/monthly` (DIRECTOR, ADMIN).

---

## Seguridad

- **JWT RS256**: user-service genera el token con clave privada RSA. Todos los demás servicios validan con la clave pública (`JWT_PUBLIC_KEY` como variable de entorno).
- **Tokens**: Access token 15 min (`JWT_ACCESS_TTL_SECONDS=900`), Refresh token 7 días.
- **RBAC**: Anotación `@RequiresRole` + interceptor CDI en `libs/security`. Roles disponibles: `STUDENT`, `INSTRUCTOR`, `DIRECTOR`, `ADMIN`.
- **Contraseñas**: Almacenadas con BCrypt.

---

## Puertos de los servicios

| Servicio | Puerto local | Puerto interno |
|---|---|---|
| web-ui (frontend) | 8080 | 8080 |
| user-service | 8081 | 8080 |
| course-service | 8082 | 8080 |
| assessment-service | 8083 | 8080 |
| collaboration-service | 8084 | 8080 |
| analytics-service | 8085 | 8080 |
| email-service | 8086 | 8080 |
| PostgreSQL | 5432 | 5432 |
| Redis | 6379 | 6379 |
| RabbitMQ AMQP | 5672 | 5672 |
| RabbitMQ Admin | 15672 | 15672 |
| MinIO API | 9000 | 9000 |
| MinIO Console | 9001 | 9001 |
| MailHog Web | 8025 | 8025 |
| MailHog SMTP | 1025 | 1025 |

---

## Red Docker

Todos los contenedores están en la red `platform-net` (bridge). Se comunican por nombre de contenedor (`puj-course-service`, `puj-postgres`, etc.).

---

## Herramientas de desarrollo y datos de prueba

| Herramienta | Uso |
|---|---|
| Python 3 + `urllib.request` | Scripts de seed (`seed_courses.py`, `seed_assessments.py`, `seed_extra_content.py`) — sin dependencias externas |
| Docker Compose | Orquestación local completa |
| Maven Multi-Module | Build del proyecto completo desde la raíz |
