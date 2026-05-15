package com.puj.courses.config;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

@ApplicationPath("/api/v1")
@OpenAPIDefinition(info = @Info(
        title = "Course Service API",
        version = "1.0.0",
        description = "Gestión de cursos, módulos, lecciones e inscripciones"
))
public class JaxRsApplication extends Application {}
