# language: es
# =============================================================================
# registro.feature — Pruebas de Aceptacion
# Plan Integral de Pruebas · PUJ Arquitectura de Software 2026
# =============================================================================

Característica: Registro de nuevos usuarios

  Escenario: Estudiante se registra exitosamente
    Dado que un visitante accede a la pagina de registro
    Cuando envia el formulario con datos validos y email "nuevo@puj.edu.co"
    Entonces el sistema crea su cuenta con rol STUDENT
    Y despacha un evento UserRegistered en RabbitMQ
    Y se envia un correo de bienvenida

  Escenario: Correo ya registrado
    Dado que el email "existente@puj.edu.co" ya esta registrado en la plataforma
    Cuando un visitante intenta registrarse con ese mismo email
    Entonces recibe un error 409 Conflict
    Y el mensaje indica que el correo ya esta en uso

  Escenario: Contrasena debil
    Dado que un visitante intenta registrarse con la contrasena "123"
    Cuando envia el formulario de registro
    Entonces recibe un error de validacion 400 Bad Request
    Y el mensaje indica que la contrasena no cumple las politicas de seguridad
