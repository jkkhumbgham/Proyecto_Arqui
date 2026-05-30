package com.puj.usuarios.config;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;

/**
 * Punto de entrada JAX-RS del servicio de usuarios.
 *
 * <p>Define la ruta base {@code /api/v1} para todos los endpoints del servicio
 * y provee los metadatos de la especificación OpenAPI.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@ApplicationPath("/api/v1")
@OpenAPIDefinition(info = @Info(
        title       = "User Service API",
        version     = "1.0.0",
        description = "Gestión de usuarios y autenticación — PUJ Learning Platform",
        contact     = @Contact(
                name  = "PUJ Arquitectura",
                email = "arqui@puj.edu.co"
        )
))
public class AplicacionJaxRs extends Application {}
