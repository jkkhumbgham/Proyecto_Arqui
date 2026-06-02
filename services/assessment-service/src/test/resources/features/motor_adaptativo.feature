# language: es
# =============================================================================
# motor_adaptativo.feature — Pruebas de Aceptación (TA-06, TA-07)
# Plan Integral de Pruebas · PUJ Arquitectura de Software 2026
# =============================================================================

Característica: Motor Adaptativo de evaluaciones

  Escenario: Estudiante con puntaje bajo recibe recomendación de contenido suplementario
    Dado que existe una evaluación con umbral de aprobación del 60%
    Y el estudiante responde correctamente solo el 35% de las preguntas
    Cuando el motor evaluación finaliza la calificación
    Entonces el resultado incluye una recomendación adaptativa con lección suplementaria
    Y el mensaje contiene "Te recomendamos repasar el material"

  Escenario: Estudiante con puntaje sobre el umbral no recibe recomendación
    Dado que existe una evaluación con umbral de aprobación del 60%
    Y el estudiante obtiene el 75% del puntaje máximo
    Cuando el motor evaluación finaliza la calificación
    Entonces el resultado no contiene ninguna recomendación

  Escenario: Puntaje exactamente igual al umbral no genera recomendación
    Dado que existe una evaluación con umbral de aprobación del 60%
    Y el estudiante obtiene exactamente el 60% del puntaje máximo
    Cuando el motor evaluación finaliza la calificación
    Entonces el resultado no contiene ninguna recomendación

  Escenario: Redis no disponible no interrumpe la calificación
    Dado que existe una evaluación con umbral de aprobación del 60%
    Y el servicio Redis no está disponible
    Y el estudiante obtiene el 40% del puntaje máximo
    Cuando el motor evaluación finaliza la calificación
    Entonces el resultado incluye una recomendación obtenida desde la base de datos
    Y el sistema no lanza ninguna excepción al estudiante

  Escenario: Entrega superando el límite de intentos es rechazada
    Dado que existe una evaluación con 3 intentos máximos
    Y el estudiante ya realizó 3 intentos
    Cuando el estudiante intenta realizar un cuarto intento
    Entonces el sistema rechaza la entrega con mensaje "intentos"
