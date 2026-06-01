package com.puj.evaluaciones.aspectos.registro;

import jakarta.interceptor.InterceptorBinding;
import java.lang.annotation.*;

/**
 * Anotación de enlace de interceptor que habilita el registro automático de llamadas
 * en los servicios de aplicación marcados con esta anotación.
 *
 * <p>Al anotar un método o tipo con {@code @Registrable}, el contenedor CDI aplica
 * automáticamente el {@link InterceptorRegistro}, que registra el tiempo de ejecución
 * y cualquier excepción lanzada.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@InterceptorBinding
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Registrable {}
