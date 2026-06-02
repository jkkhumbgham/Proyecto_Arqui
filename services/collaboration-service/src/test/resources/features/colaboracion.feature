# language: es
# =============================================================================
# colaboracion.feature — Pruebas de Aceptación (TA-08, TA-09)
# Plan Integral de Pruebas · PUJ Arquitectura de Software 2026
# =============================================================================

Característica: Foros y Grupos de Estudio

  Escenario: Crear un hilo en un foro existente
    Dado que existe un foro activo
    Y el estudiante tiene sesión iniciada
    Cuando el estudiante crea un hilo con título "Duda técnica" y contenido "Tengo una duda"
    Entonces el hilo se crea exitosamente
    Y la primera publicación contiene "Tengo una duda"

  Escenario: Publicar en hilo bloqueado
    Dado que existe un foro activo
    Y el hilo "Duda técnica" ha sido bloqueado por el moderador
    Cuando el estudiante intenta publicar una respuesta en el hilo
    Entonces recibe un error indicando que el hilo está cerrado

  Escenario: Crear grupo de estudio asigna al creador como tutor
    Dado que existe un curso publicado
    Cuando un estudiante crea el grupo de estudio "Scrum Masters"
    Entonces el grupo se crea exitosamente
    Y el creador es asignado como tutor del grupo automáticamente

  Escenario: Unirse a grupo de estudio lleno
    Dado que existe un grupo de estudio con capacidad máxima de 3 miembros
    Y el grupo ya tiene 3 miembros activos
    Cuando otro estudiante intenta unirse al grupo
    Entonces recibe un error indicando que el grupo está lleno
