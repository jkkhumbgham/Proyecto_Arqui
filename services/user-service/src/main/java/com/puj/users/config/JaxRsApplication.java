package com.puj.users.config;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;

@ApplicationPath("/api/v1")
@OpenAPIDefinition(info = @Info(
        title = "User Service API",
        version = "1.0.0",
        description = "Gestión de usuarios y autenticación — PUJ Learning Platform",
        contact = @Contact(name = "PUJ Arquitectura", email = "arqui@puj.edu.co")
))
public class JaxRsApplication extends Application {}
