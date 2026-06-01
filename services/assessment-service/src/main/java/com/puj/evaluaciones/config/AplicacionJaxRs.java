package com.puj.evaluaciones.config;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

/**
 * Punto de entrada JAX-RS del servicio de evaluaciones.
 *
 * <p>Define el prefijo de ruta {@code /api/v1} para todos los recursos REST
 * del servicio y expone la documentación OpenAPI con metadatos del API.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationPath("/api/v1")
@OpenAPIDefinition(info = @Info(
        title       = "Servicio de Evaluaciones API",
        version     = "1.0.0",
        description = "Evaluaciones, entregas y motor adaptativo determinístico"
))
public class AplicacionJaxRs extends Application {}
