# language: es
# =============================================================================
# ciclo_curso.feature — Pruebas de Aceptación (TA-04, TA-05)
# Plan Integral de Pruebas · PUJ Arquitectura de Software 2026
# =============================================================================

Característica: Ciclo de vida de un curso

  Escenario: Instructor crea y publica curso
    Dado que el instructor "prof@puj.edu.co" tiene sesión activa
    Cuando crea un curso con título "Arquitectura de Software" y lo publica
    Entonces el curso aparece en el catálogo público con estado PUBLISHED
    Y los estudiantes pueden inscribirse

  Escenario: Estudiante se inscribe a curso publicado
    Dado que existe el curso "Arquitectura de Software" en estado PUBLISHED
    Y el estudiante no está inscrito previamente
    Cuando el estudiante solicita inscripción
    Entonces se crea una matrícula ACTIVE
    Y se publica el evento CourseEnrolled en RabbitMQ

  Escenario: No se puede publicar un curso sin módulos
    Dado que el instructor tiene un curso en estado DRAFT sin módulos
    Cuando intenta publicar el curso
    Entonces recibe un error 400 con el mensaje "módulo"

  Escenario: Instructor no propietario no puede modificar el curso
    Dado que el instructor "otro@puj.edu.co" no es propietario del curso
    Cuando intenta actualizar el título del curso
    Entonces recibe un error 403 Forbidden
