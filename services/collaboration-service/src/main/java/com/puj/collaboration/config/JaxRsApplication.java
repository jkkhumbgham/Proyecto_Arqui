package com.puj.collaboration.config;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

@ApplicationPath("/api/v1")
@OpenAPIDefinition(info = @Info(
        title = "Collaboration Service API",
        version = "1.0.0",
        description = "Foros, hilos, posts, grupos de estudio y WebSocket en tiempo real"
))
public class JaxRsApplication extends Application {}
