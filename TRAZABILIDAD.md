# Matriz de Trazabilidad — Pruebas Automatizadas

Este documento traza cada requerimiento de prueba del **Plan Integral de Pruebas** hacia su implementación automatizada en el repositorio.

## 1. Pruebas de Disponibilidad (TD)
**Tipo:** Scripts de Bash/Batch automatizados (cURL).
**Ubicación:** `/scripts/disponibilidad/`

| ID | Requerimiento / Escenario | Implementación |
|----|---------------------------|----------------|
| TD-001..010 | Verificación de health checks y métricas | `check_availability.sh` / `check_availability.bat` |

---

## 2. Pruebas de Escalabilidad y Rendimiento (TE)
**Tipo:** Plan de pruebas de Apache JMeter (`.jmx`).
**Ubicación:** `/scripts/escalabilidad/`

| ID | Requerimiento / Escenario | Implementación |
|----|---------------------------|----------------|
| TE-001..003 | Carga base, pico y estrés (Login, Cursos, Matrículas) | `carga_plataforma.jmx` (Grupos de Hilos parametrizables) |

---

## 4. Pruebas de Integración (TI)
**Tipo:** Pruebas de integración de capa de dominio (JUnit 5 + H2 in-memory).
**Ubicación:** `/services/*/src/test/...`

| ID | Requerimiento / Escenario | Implementación | Servicio |
|----|---------------------------|----------------|----------|
| TI-001 | Persistencia de usuario y roles en PostgreSQL (vía JPA) | `UsuarioDominioIntegracionTest.java` | `user-service` |
| TI-002 | Fallback de caché Redis a Base de Datos para blacklist | `UsuarioDominioIntegracionTest.java` | `user-service` |
| TI-003 | Integración Matriculas-Cursos (Curso eliminado lógicamente) | `MatriculaIntegracionTest.java` | `course-service` |
| TI-004 | Publicación de evento RabbitMQ al completar módulo | `MatriculaIntegracionTest.java` | `course-service` |
| TI-005 | Restricción de unicidad de matrícula (clave compuesta) | `MatriculaIntegracionTest.java` | `course-service` |
| TI-006 | Calificación automática — Opción múltiple / Única | `EvaluacionesFuncionalTest.java` | `assessment-service` |
| TI-007 | Fallback del motor adaptativo de Redis a BD | `EvaluacionesFuncionalTest.java` | `assessment-service` |
| TI-008 | Motor adaptativo: Evaluación en umbral exacto | `EvaluacionesFuncionalTest.java` | `assessment-service` |

---

## 5. Pruebas Funcionales (TF)
**Tipo:** Pruebas unitarias de servicios de aplicación con repositorios mockeados (Mockito).
**Ubicación:** `/services/*/src/test/java/.../funcional/`

| ID | Requerimiento / Escenario | Implementación | Servicio |
|----|---------------------------|----------------|----------|
| TF-001..003 | Registro de usuario (Éxito, Email duplicado, Password débil) | `AutenticacionFuncionalTest.java` | `user-service` |
| TF-004..006 | Autenticación (Éxito, Token en caché, Lista negra) | `AutenticacionFuncionalTest.java` | `user-service` |
| TF-007..009 | Creación y actualización de cursos (Propiedad) | `CursosFuncionalTest.java` | `course-service` |
| TF-010..011 | Matrículas (Inscripción, Cancelación, Completado auto) | `CursosFuncionalTest.java` | `course-service` |
| TF-012..015 | Entregas y Motor Adaptativo (Calificación, Intentos, Recomendación) | `EvaluacionesFuncionalTest.java` | `assessment-service` |
| TF-017..020 | Foros y Discusiones (Crear hilo, Respuesta, Hilo cerrado) | `ForosFuncionalTest.java` | `collaboration-service` |
| TF-021..022 | Grupos de Estudio (Crear, Unirse a grupo lleno) | `ForosFuncionalTest.java` | `collaboration-service` |

---

## 7. Pruebas de Aceptación de Usuario (TA)
**Tipo:** Escenarios BDD en Gherkin ejecutados mediante Cucumber + JUnit Platform.
**Ubicación:** `/services/*/src/test/resources/features/` y `/services/*/src/test/java/.../aceptacion/steps/`

| ID | Requerimiento / Escenario | Feature File | Servicio |
|----|---------------------------|--------------|----------|
| TA-01..03 | Login y Registro (Credenciales válidas, inválidas, email duplicado) | `login.feature`, `registro.feature` | `user-service` |
| TA-04..05 | Ciclo de vida de un curso e inscripción | `ciclo_curso.feature` | `course-service` |
| TA-06..07 | Motor Adaptativo y límite de intentos de evaluación | `motor_adaptativo.feature` | `assessment-service` |
| TA-08..09 | Foros y Grupos de Estudio | `colaboracion.feature` | `collaboration-service` |
