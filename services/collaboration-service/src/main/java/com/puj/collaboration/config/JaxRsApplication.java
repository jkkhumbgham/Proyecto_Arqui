package com.puj.collaboration.config;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

/**
 * Punto de entrada JAX-RS del servicio de colaboración.
 *
 * <p>Define el prefijo de ruta {@code /api/v1} para todos los recursos REST
 * del servicio y expone la documentación OpenAPI con metadatos del API.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationPath("/api/v1")
@OpenAPIDefinition(info = @Info(
        title       = "Collaboration Service API",
        version     = "1.0.0",
        description = "Foros, hilos, posts, grupos de estudio y WebSocket en tiempo real"
))
public class JaxRsApplication extends Application {}
