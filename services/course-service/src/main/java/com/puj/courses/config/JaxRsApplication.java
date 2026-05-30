package com.puj.courses.config;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

/**
 * Punto de entrada JAX-RS del Course Service.
 *
 * <p>Define la ruta base {@code /api/v1} para todos los recursos REST del servicio
 * y expone los metadatos OpenAPI usados por la documentación interactiva (Swagger UI).
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationPath("/api/v1")
@OpenAPIDefinition(info = @Info(
        title       = "Course Service API",
        version     = "1.0.0",
        description = "Gestión de cursos, módulos, lecciones e inscripciones"
))
public class JaxRsApplication extends Application {}
