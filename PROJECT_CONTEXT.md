# Contexto del Proyecto — Plataforma de Aprendizaje Adaptativo y Colaborativo
**PUJ 2026 · Arquitectura de Software**
**Equipo:** Laura Juliana Garzón Arias · Juan Camilo Alba Castro · Carlos Manuel Villegas Ruiz · Arley Bernal Muñetón

---

## 1. Descripción General

LMS (Learning Management System) orientado a instituciones educativas colombianas que resuelve dos limitaciones de los LMS tradicionales:

1. **Uniformidad del contenido** — todos los estudiantes reciben lo mismo al mismo ritmo.
2. **Aislamiento del aprendizaje** — sin mecanismos reales de colaboración entre pares.

### Mecanismos diferenciadores
- **Motor adaptativo determinístico basado en reglas**: al completar una evaluación, el sistema compara la calificación contra un umbral configurado por el docente. Si está por debajo → entrega materiales de refuerzo. Si alcanza o supera → desbloquea contenido de mayor complejidad. No usa ML/IA. Las reglas son configurables por el docente sin redespliegue.
- **Herramientas de colaboración entre pares**: foros por curso/módulo, grupos de estudio con chat en tiempo real, identificación automática de tutores (promedio > 85%).

---

## 2. Actores y Roles (RBAC)

| Rol | Descripción | Privilegio |
|---|---|---|
| `STUDENT` | Accede a cursos, presenta evaluaciones, participa en foros/grupos, consulta progreso | Bajo |
| `INSTRUCTOR` | Crea y gestiona cursos, publica materiales, define evaluaciones y reglas adaptativas, modera foros, consulta analítica | Medio |
| `DIRECTOR` | Accede exclusivamente al tablero institucional con métricas agregadas. Sin acceso a detalle de cursos ni estudiantes individuales | Medio-estratégico |
| `ADMIN` | Gestión de cuentas, roles, configuración de la plataforma, logs de auditoría | Alto |

---

## 3. Arquitectura Seleccionada

### Estilo: Service-Based Architecture (5 servicios de grano grueso)

Evaluados 3 candidatos: Monolito Modular, Service-Based y Microservicios. Se seleccionó **Service-Based** por mejor equilibrio entre modularidad, complejidad operativa y atributos de calidad dado el tamaño del equipo (3–4 personas).

### Servicios

| Servicio | Responsabilidad | Componentes internos |
|---|---|---|
| **User Service** | Gestión de usuarios, autenticación JWT RS256, RBAC | UserController, UserService, UserRepository |
| **Course Service** | Administración de cursos, módulos, lecciones, materiales (S3) | CourseController, CourseService, CourseRepository |
| **Assessment & Adaptive Service** | Evaluaciones, calificación automática, motor de reglas adaptativo | AssessmentController, AssessmentService, AssessmentRepository, AdaptiveEngine |
| **Collaboration Service** | Foros, grupos de estudio, chat WebSocket en tiempo real, tutores | CollaborationController, CollaborationService, CollaborationRepository, ChatModule |
| **Analytics Service** | Métricas, reportes, alertas, tablero institucional, exportación CSV/PDF | AnalyticsController, AnalyticsService, AnalyticsRepository |

### Librería compartida
- `libs/security` — módulo Maven inyectado en todos los servicios. JWT RS256, validación de roles, blacklist de tokens en Redis.
- `libs/events` — contratos de eventos para mensajería asíncrona (extensible sin modificar publicadores).

---

## 4. Stack Tecnológico

| Capa | Tecnología | Versión |
|---|---|---|
| Frontend | Jakarta Faces (JSF) — MPA | Jakarta EE 10 |
| Backend | Jakarta EE 10 / WildFly | EE 10 / WildFly 31 |
| Persistencia | PostgreSQL (JPA 3.1 + Hibernate 6 + PgBouncer) | PostgreSQL 15 |
| Caché | Redis (ElastiCache) | 7 |
| Mensajería | RabbitMQ (AMQP 0-9-1) — colas: `analytics.results`, `email.notifications` | 3.13 |
| Almacenamiento de archivos | AWS S3 (URLs prefirmadas) | — |
| Contenedores | Docker + Kubernetes (AWS EKS) | — |
| Despliegue | ECS Fargate + RDS Multi-AZ (Blue-Green) | — |
| Seguridad | JWT RS256, TLS 1.2+, HTTPS, bcrypt | — |
| API | REST / OpenAPI 3.0, versionado `/api/v1/` | — |
| Tiempo real | Jakarta WebSocket (WSS) | — |

---

## 5. Modelo del Dominio (entidades clave)

### Usuario
```
id, nombreCompleto, correo (único), contrasena (bcrypt), rol (STUDENT|INSTRUCTOR|DIRECTOR|ADMIN), estado (ACTIVE|REVOKED), fechaCreacion
```

### Curso
```
id, titulo, descripcion, estado (DRAFT|PUBLISHED), instructorId, fechaCreacion, fechaPublicacion
```

### Módulo / Lección
```
Modulo: id, cursoId, titulo, orden
Leccion: id, moduloId, titulo, orden, contenido
```

### Material
```
id, leccionId, tipo (PDF|VIDEO|IMAGEN), url_s3, nombre
```

### Evaluación / Pregunta / Respuesta
```
Evaluacion: id, leccionId, titulo, umbralAprobacion
Pregunta: id, evaluacionId, enunciado, tipo (OPCION_MULTIPLE|VERDADERO_FALSO|RESPUESTA_CORTA), puntaje
Respuesta: id, preguntaId, texto, esCorrecta
```

### Regla Adaptativa
```
id, evaluacionId, umbral, materialRefuerzoId, leccionAvanceId, activa
```

### Resultado Evaluación
```
id, estudianteId, evaluacionId, calificacion, fecha, accionAdaptativa (REFUERZO|AVANCE|NINGUNA)
```

### Inscripción / Progreso
```
Inscripcion: id, estudianteId, cursoId, fechaInscripcion
Progreso: id, estudianteId, leccionId, estado (PENDIENTE|EN_PROGRESO|COMPLETADO), fechaCompletado
```

### Foro / Hilo / Mensaje
```
Foro: id, cursoId, moduloId (nullable), titulo
Hilo: id, foroId, autorId, titulo, fechaCreacion
Mensaje: id, hiloId, autorId, contenido, fecha
```

### Grupo de Estudio / Chat
```
GrupoEstudio: id, cursoId, nombre, tutorId (nullable)
MiembroGrupo: grupoId, estudianteId, fechaUnion
MensajeChat: id, grupoId, autorId, contenido, timestamp
```

---

## 6. Requerimientos Funcionales (resumen por feature)

| ID | Feature | Prioridad |
|---|---|---|
| RF-01 | Gestión de Usuarios (registro, login JWT, RBAC, gestión por ADMIN) | Alta |
| RF-02 | Gestión de Cursos (CRUD, módulos, lecciones, materiales, publicación) | Alta |
| RF-03 | Evaluaciones y Calificación (quizzes, calificación automática, retroalimentación) | Alta |
| RF-04 | Motor Adaptativo (reglas por lección, umbral, refuerzo, avance, TTL 60s Redis) | Alta |
| RF-05 | Foros de Discusión (hilos, respuestas, moderación, notificaciones asíncronas) | Media |
| RF-06 | Grupos de Estudio (chat WebSocket, identificación tutores >85%, asignación manual) | Media |
| RF-07 | Seguimiento del Progreso (dashboard estudiante, vista por estudiante para docente) | Alta |
| RF-08 | Analítica (reportes por curso, alertas, exportación CSV/PDF, tablero institucional) | Media |

---

## 7. Requerimientos No Funcionales

| Atributo | Métrica |
|---|---|
| **Disponibilidad** | ≥ 99.5% mensual. RTO ≤ 1h. RPO ≤ 15min. Failover Multi-AZ ≤ 60s |
| **Rendimiento** | P95 ≤ 2s para operaciones principales. Motor adaptativo ≤ 3s |
| **Escalabilidad** | Hasta 5.000 concurrentes base; 10.000 con ajuste HPA. Auto-scale en ≤ 5 min |
| **Seguridad** | JWT RS256, TLS 1.2+, bcrypt, RBAC, rate limiting 100 req/min/IP, bloqueo tras 5 intentos fallidos, logs de auditoría 1 año |
| **Mantenibilidad** | Cambio en un servicio no requiere redespliegue de otros. Nuevas reglas adaptativas sin redespliegue |
| **Interoperabilidad** | OpenAPI 3.0 en todos los endpoints. Preparado para xAPI (LRS) en versiones futuras |

---

## 8. Infraestructura y Despliegue

### Vista Física — 3 tiers sobre Kubernetes (EKS)

```
[Browser] --HTTPS--> [Ingress Controller / Load Balancer]
                            |
              ┌─────────────┼─────────────┐
         [User Pod]  [Course Pod]  [Assessment Pod]  [Collaboration Pod]  [Analytics Pod]
              │             │             │                  │                   │
              └─────────────┴─────────────┴──────────────────┴───────────────────┘
                                          │
                      ┌───────────────────┼────────────────────┐
                 [PostgreSQL 15]      [Redis 7]          [RabbitMQ 3.13]
                  (RDS Multi-AZ)   (ElastiCache)              
                                                         [AWS S3]
```

### Comunicación entre servicios
- **HTTP interno** dentro del cluster (DNS interno de Kubernetes)
- **AMQP** para mensajería asíncrona (Assessment → Analytics, Email)
- **WebSocket (WSS)** para chat en tiempo real (Collaboration Service)
- **JDBC/JPA** hacia PostgreSQL
- **Redis** para sesiones, blacklist JWT y caché de reglas adaptativas

### Estrategia de despliegue
- **Blue-Green** en ECS Fargate
- **Rolling Update** en Kubernetes sin downtime
- **HPA** (Horizontal Pod Autoscaler): escala al superar CPU > 70% por 3 min

---

## 9. Seguridad

- **Autenticación**: JWT RS256, expiración máx. 8 horas
- **Revocación**: blacklist en Redis, propagación ≤ 5s
- **Contraseñas**: bcrypt
- **Transporte**: TLS 1.2+ en todo tráfico externo
- **Rate limiting**: 100 req/min/IP en API Gateway
- **Bloqueo de cuenta**: tras 5 intentos fallidos consecutivos (15 min)
- **RBAC**: 4 roles, validado en `libs/security` en cada servicio
- **Auditoría**: log de todos los accesos y cambios de estado de cuenta. Retención 1 año
- **Datos personales**: cumplimiento Ley 1581 de 2012 (Colombia)

---

## 10. Análisis ATAM — Resumen de Hallazgos

### Propuestas Arquitectónicas evaluadas

| ID | Propuesta |
|---|---|
| PA-01 | Service-Based Architecture (5 servicios, schema propio por dominio) |
| PA-02 | Motor de reglas determinístico (AdaptiveEngine + Redis TTL 60s) |
| PA-03 | Módulo de seguridad compartido `libs/security` (JWT RS256, RBAC) |
| PA-04 | Mensajería asíncrona RabbitMQ (colas Resultados y Correos) |
| PA-05 | Despliegue ECS Fargate + RDS Multi-AZ (Blue-Green) |
| PA-06 | Redis Cache (sesiones + reglas adaptativas) |
| PA-07 | WebSocket en Collaboration Service (chat en tiempo real) |

### No Riesgos (fortalezas)
- Schemas lógicos por dominio → cambios en un servicio no afectan otros (PA-01)
- Redis TTL 60s para reglas → sin consultas a BD por cada evaluación (PA-02)
- `libs/security` → políticas de acceso consistentes en los 5 servicios (PA-03)
- Mensajería asíncrona → desacopla notificaciones sin afectar latencia (PA-04)
- RDS Multi-AZ → failover automático ≥ 99.5% (PA-05)
- ECS Fargate → escalado horizontal independiente por servicio (PA-05)

### Riesgos identificados
| ID | Riesgo |
|---|---|
| R-01 | Llamadas inter-servicio frecuentes pueden generar latencia acumulada en el flujo de evaluación |
| R-02 | Sin Redis disponible, motor adaptativo consulta directamente PostgreSQL sin fallback documentado |
| R-03 | Bug en `libs/security` se propaga a los 5 servicios simultáneamente |
| R-04 | Consistencia eventual: dashboards pueden mostrar datos desactualizados post-procesamiento asíncrono |
| R-05 | Entorno de desarrollo (capa gratuita AWS) no replica Multi-AZ, ocultando problemas de producción |

### Tradeoffs principales
| ID | Tradeoff | Gana | Pierde |
|---|---|---|---|
| T-01 | Service-Based vs. Monolito Modular | Escalabilidad, Mantenibilidad | Simplicidad de comunicación |
| T-02 | Motor determinístico vs. ML adaptativo | Predictibilidad, Simplicidad | Adaptación compleja por patrones |
| T-03 | `libs/security` compartido vs. local por servicio | Consistencia de políticas | Despliegue independiente |
| T-04 | Mensajería asíncrona vs. llamadas síncronas | Performance, Disponibilidad | Consistencia inmediata |
| T-05 | Schemas lógicos compartidos vs. BD independientes | Costo, Simplicidad operativa | Aislamiento de fallos en BD |

---

## 11. Escenarios de Calidad

| ID | Escenario | Atributo | Medida |
|---|---|---|---|
| E-01 / ESC-01 | 500 estudiantes envían evaluaciones concurrentemente | Performance | P95 ≤ 2s |
| E-05 / ESC-04 | Carga escala de 1.000 a 5.000 concurrentes en 10 min | Escalabilidad | Auto-scale ≤ 5 min, P95 ≤ 2s |
| E-06 / ESC-02 | Falla del Analytics Service con usuarios activos | Disponibilidad | Servicios críticos mantienen 99.5%, detección ≤ 30s |
| E-07 | Falla de una zona de disponibilidad AWS | Disponibilidad | ≥ 99.5% mensual |
| E-09 / ESC-03 | 100 intentos de login en 1 min sobre mismo correo | Seguridad | 0 accesos no autorizados, bloqueo ≤ 1s tras 5to intento |
| E-12 / ESC-05 | Docente cambia umbral de regla adaptativa | Mantenibilidad | Efecto en ≤ 60s, sin redespliegue, sin interrupción |

---

## 12. Exclusiones del Alcance (v1.0)

- Sistema de registro académico institucional
- Módulos de facturación o pagos
- Certificados digitales
- Soporte SCORM
- Inteligencia Artificial / Machine Learning
- xAPI / LRS (planificado para versiones futuras)

---

## 13. Interfaz de Usuario

- **Tipo**: MPA (Multi-Page Application) con Jakarta Faces (JSF)
- **Responsive**: desde 375px (móvil) hasta 1920px (escritorio)
- **Navegadores**: Chrome, Firefox, Safari, Edge (últimas 2 versiones)
- **Validación**: Bean Validation + FacesMessage en tiempo real
- **Cada rol tiene su propio dashboard** al iniciar sesión

---

## 14. Integraciones Externas

| Sistema | Estado | Protocolo |
|---|---|---|
| AWS S3 | Activo v1 | URLs prefirmadas |
| SMTP institucional | Activo v1 | SMTP asíncrono vía cola RabbitMQ |
| Sistema de registro académico | Futuro | REST API |
| xAPI / LRS | Futuro | xAPI 2.0 |

---

*Documento generado a partir de: SRS v2.0, SAD v1.0, ATAM v1.0 — PUJ 2026*
