package com.puj.assessments.config;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

@ApplicationPath("/api/v1")
@OpenAPIDefinition(info = @Info(
        title = "Assessment Service API",
        version = "1.0.0",
        description = "Evaluaciones, submissions y motor adaptativo determinístico"
))
public class JaxRsApplication extends Application {}
