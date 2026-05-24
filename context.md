# Funcionalidades del Sistema — Estado Actual

Plataforma LMS (Learning Management System) con roles diferenciados. Las funcionalidades están implementadas y accesibles en `http://localhost:8080`.

---

## Estudiante (`STUDENT`)

### Autenticación y perfil
- Registrarse con nombre, correo y contraseña (consentimiento obligatorio — Ley 1581)
- Iniciar y cerrar sesión
- Token JWT con duración de 15 minutos + refresh automático de 7 días
- **Perfil enriquecido**: editar nombre para mostrar, biografía y URL de avatar
- Ver puntos acumulados y insignias de gamificación en el perfil

### Catálogo de cursos
- Ver todos los cursos publicados con búsqueda por texto y categoría (con caché Redis)
- Filtrar y ordenar resultados (más recientes / alfabético)
- Inscribirse en un curso disponible
- Ver el detalle de un curso: descripción, módulos y lista de lecciones con duración

### Rutas de Aprendizaje
- Explorar el catálogo de rutas de aprendizaje publicadas
- Ver el detalle de una ruta: descripción, lista de cursos en orden
- Inscribirse en una ruta de aprendizaje (inscripción independiente de cursos individuales)
- Ver sus rutas inscritas con barra de progreso en el dashboard

### Aprendizaje (lecciones)
- Navegar las lecciones de un curso a través del sidebar lateral (por módulo)
- Ver el contenido de cada lección con múltiples ítems:
  - **Texto** — contenido educativo formateado
  - **Video** — incrustado desde YouTube (iframe responsive)
  - **PDF** — visualizado directamente en el navegador
  - **Documento** — enlace de descarga
- Navegar secuencialmente entre lecciones con botones Anterior / Siguiente
- **Marcar una lección como completada** (genera puntos de gamificación)
- El círculo del sidebar se marca con ✓ verde al completar una lección

### Progreso
- Ver la barra de progreso del curso con porcentaje actualizado en tiempo real
- Ver cuántas lecciones ha completado del total (`X / Y completadas`)
- Botón "Continuar →" en el detalle del curso que lleva a la primera lección sin completar

### Evaluaciones
- Ver todas sus evaluaciones agrupadas por curso
- Iniciar una evaluación desde el detalle del curso o desde la sección Evaluaciones
- Responder preguntas de tipo: SINGLE_CHOICE, TRUE_FALSE, MULTIPLE_CHOICE, SHORT_ANSWER
- Ver el resultado inmediato con puntaje en porcentaje y si aprobó o no
- Recibir recomendación adaptativa si no aprueba (lección de refuerzo sugerida)
- Ganar puntos de gamificación al aprobar (50 pts primer intento, 25 pts reintento)

### Gamificación
- Ganar puntos por: completar lecciones (10 pts), aprobar evaluaciones, publicar en foros
- Desbloquear insignias automáticamente al superar umbrales de puntos:
  - 🥇 **PRIMER_PUNTO** — al ganar el primer punto
  - 🧭 **EXPLORADOR** — 100 puntos
  - 📚 **DEDICADO** — 500 puntos
  - ⭐ **EXPERTO** — 1000 puntos
  - 🏆 **MAESTRO** — 5000 puntos
- Ver puntos e insignias en el perfil

### Certificados
- Al completar todos los módulos y evaluaciones de un curso, se genera automáticamente un certificado PDF
- Descargar el PDF desde la sección "Mis Certificados" del perfil
- Cada certificado tiene un código de verificación único

### Notificaciones in-app
- Recibir notificaciones automáticas al:
  - Completar una lección
  - Aprobar una evaluación
  - Obtener un certificado
  - Inscribirse en una ruta de aprendizaje
- Marcar notificaciones como leídas individualmente o todas a la vez

### Colaboración
- Ver los foros de sus cursos inscritos
- Crear hilos de discusión en un foro (genera 5 pts de gamificación)
- Responder hilos existentes (genera 3 pts de gamificación)
- Unirse a grupos de estudio de un curso

### Dashboard personal
- Ver todos los cursos inscritos con su porcentaje de progreso
- Ver rutas de aprendizaje inscritas con progreso
- Acceder directamente a continuar cada curso

---

## Instructor (`INSTRUCTOR`)

### Autenticación
- Iniciar y cerrar sesión (credencial asignada por administrador)
- Editar su propio perfil (nombre para mostrar, bio, avatar)

### Gestión de cursos
- Crear nuevos cursos con título, descripción, categoría y cupo máximo
- Ver lista de sus cursos (con estado: DRAFT, PUBLISHED)
- Publicar un curso (lo hace visible a estudiantes)

### Estructura de contenido
- Crear módulos dentro de un curso (título, descripción, orden)
- Crear lecciones dentro de un módulo (título, duración en minutos)
- Gestionar los contenidos de cada lección (TEXT, VIDEO, PDF, DOCUMENT)

### Rutas de Aprendizaje
- Crear rutas de aprendizaje seleccionando cursos y definiendo su orden
- Publicar rutas para que los estudiantes puedan inscribirse

### Evaluaciones
- Crear evaluaciones con el Constructor de Evaluaciones
- Configurar: título, descripción, porcentaje mínimo, intentos máximos
- Agregar preguntas: SINGLE_CHOICE, TRUE_FALSE, MULTIPLE_CHOICE, SHORT_ANSWER
- Ver resultados de los estudiantes por evaluación

### Colaboración
- Crear foros para sus cursos
- Crear hilos de discusión y responder
- Moderar foros: eliminar posts de estudiantes

### Dashboard
- Ver todos sus cursos con estado (DRAFT / PUBLISHED)
- Acceder al detalle de gestión de cada curso

---

## Administrador (`ADMIN`)

Tiene acceso a **todas las funcionalidades del Instructor** más:

### Gestión de usuarios
- Listar todos los usuarios registrados en la plataforma
- Ver el perfil de cualquier usuario
- Cambiar el rol de un usuario (STUDENT → INSTRUCTOR → DIRECTOR → ADMIN)
- Eliminar usuarios (soft delete)

### Gestión de contenido
- Acceso a todos los cursos (no solo los propios)
- Moderar foros de cualquier curso

### Dashboard administrativo
- Ver estadísticas globales en tiempo real desde analytics-service: total usuarios, inscripciones, promedio de calificaciones, tasa de aprobación
- Top 5 cursos más populares (por inscripciones y promedio de calificación)
- Snapshots históricos mensuales: `GET /api/v1/analytics/monthly`

---

## Directivo (`DIRECTOR`)

Rol de solo lectura orientado a supervisión. No puede crear ni modificar contenido.

### Dashboard de supervisión
- Ver estadísticas globales en tiempo real desde analytics-service: total usuarios, inscripciones, tasa de aprobación
- Ranking de los cursos más populares
- Usuarios inactivos (sin login en los últimos 30 días): `GET /api/v1/users?inactive=true&days=30`

### Analytics (API)
- Progreso individual de un estudiante: `GET /api/v1/analytics/students/{userId}/progress`
- Resumen de métricas de un curso: `GET /api/v1/analytics/courses/{courseId}/summary`
- Snapshots históricos mensuales: `GET /api/v1/analytics/monthly`
- Exportar reporte de cursos en CSV o PDF
- Exportar reporte de entregas de evaluaciones en CSV o PDF

---

## Funcionalidades transversales (todos los roles)

| Funcionalidad | Descripción |
|---|---|
| Autenticación JWT | Login seguro con access token (15 min) y refresh token (7 días) |
| RBAC | Cada endpoint y vista del UI valida el rol. Acceso no autorizado retorna 403 |
| Notificaciones por correo | Al registrarse, el sistema envía correo de bienvenida (capturado en MailHog: `http://localhost:8025`) |
| Eventos asíncronos | Las acciones importantes disparan eventos a RabbitMQ procesados por analytics, gamificación y notificaciones |
| Sistema adaptativo | Al reprobar una evaluación, el motor adaptativo genera una recomendación con lección de refuerzo |
| Búsqueda con caché | Búsqueda de cursos por texto y categoría con caché Redis (TTL 300s) |
| Gamificación | Sistema de puntos e insignias integrado en todas las acciones del estudiante |
| Certificados PDF | Generación automática de PDF al completar un curso; almacenados en MinIO |

---

## Estado de las vistas (páginas disponibles)

| Vista | URL | Roles con acceso |
|---|---|---|
| Login | `/views/login` | Todos (no autenticado) |
| Registro | `/views/register` | Todos (no autenticado) |
| Dashboard | `/views/dashboard` | Todos (autenticado) |
| Catálogo de cursos | `/views/courses` | Todos (autenticado) |
| Detalle de curso | `/views/course-detail?courseId=…` | Todos (autenticado) |
| Visor de lección | `/views/lesson-viewer?lessonId=…&courseId=…` | Todos (autenticado) |
| Crear módulo | `/views/module-create?courseId=…` | INSTRUCTOR, ADMIN |
| Crear lección | `/views/lesson-create?moduleId=…&courseId=…` | INSTRUCTOR, ADMIN |
| Editar lección (contenidos) | `/views/lesson-edit?lessonId=…&courseId=…` | INSTRUCTOR, ADMIN |
| Evaluaciones | `/views/assessments` | Todos (autenticado) |
| Tomar evaluación | `/views/assessment-take?assessmentId=…` | STUDENT |
| Resultado de evaluación | `/views/assessment-result` | STUDENT |
| Constructor de evaluación | `/views/assessment-builder?courseId=…` | INSTRUCTOR, ADMIN |
| Foros | `/views/forums` | Todos (autenticado) |
| Rutas de aprendizaje | `/views/learning-paths` | Todos (autenticado) |
| Detalle de ruta | `/views/learning-path-detail?pathId=…` | Todos (autenticado) |
| Crear ruta | `/views/learning-path-create` | INSTRUCTOR, ADMIN |
| Perfil | `/views/profile` | Todos (autenticado) |
| Editar perfil | `/views/profile-edit` | Todos (autenticado) |
| Certificados | `/views/certificates` | STUDENT |

---

## Datos de prueba cargados

### Usuarios (8 en total)

| Rol | Cantidad |
|---|---|
| ADMIN | 1 (admin@puj.edu.co) |
| DIRECTOR | 1 (director@puj.edu.co) |
| INSTRUCTOR | 1 (prof.garcia@puj.edu.co) |
| STUDENT | 5 |

### Cursos (3 publicados, 36 lecciones en total)

| Curso | Módulos | Lecciones | Evaluaciones |
|---|---|---|---|
| Fundamentos de Programación en Java | 3 | 12 (9 contenido + 3 evaluación) | 3 |
| Bases de Datos Relacionales | 3 | 12 (9 contenido + 3 evaluación) | 3 |
| Arquitectura de Software | 3 | 12 (9 contenido + 3 evaluación) | 3 |

- Cada lección de contenido tiene entre 2 y 3 ítems (texto + video YouTube + PDF)
- Las evaluaciones tienen entre 8 y 10 preguntas auto-calificables
- Los 5 estudiantes de prueba están inscritos en los 3 cursos
