# language: es
# =============================================================================
# login.feature â€” Pruebas de Aceptacion
# Plan Integral de Pruebas Â· PUJ Arquitectura de Software 2026
# =============================================================================

Característica: Inicio de sesion

  Escenario: Login exitoso
    Dado que existe un usuario activo con email "estudiante@puj.edu.co"
    Cuando ingresa sus credenciales correctas en el formulario de login
    Entonces el sistema emite un access token JWT y un refresh token
    Y redirige al dashboard del usuario

  Escenario: Bloqueo de cuenta tras intentos fallidos
    Dado que un usuario ha fallado la contrasena 5 veces consecutivas
    Cuando intenta iniciar sesion nuevamente (6to intento)
    Entonces recibe el mensaje "Cuenta bloqueada por intentos fallidos. Intenta en 15 minutos"
    Y el sistema registra el evento LOGIN_BLOCKED en auditoria

  Escenario: Login con correo inexistente
    Dado que no existe ningun usuario con email "noexiste@puj.edu.co"
    Cuando intenta iniciar sesion con ese email
    Entonces recibe un error 401 con el mensaje "Credenciales invalidas"
    Y el sistema registra el evento LOGIN_FAILED en auditoria
