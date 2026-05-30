package com.puj.email.rest;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Punto de entrada JAX-RS para el servicio de correo electrónico.
 *
 * <p>Configura la raíz de la aplicación REST en {@code "/"}, de modo que
 * todos los recursos JAX-RS del servicio se sirven directamente desde la
 * raíz del contexto del servidor de aplicaciones.</p>
 *
 * <p>No es necesario sobreescribir {@link Application#getClasses()} ni
 * {@link Application#getSingletons()} porque el servidor de aplicaciones
 * detecta los recursos anotados con {@code @Path} mediante escaneo CDI.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationPath("/")
public class JaxRsApplication extends Application {}
