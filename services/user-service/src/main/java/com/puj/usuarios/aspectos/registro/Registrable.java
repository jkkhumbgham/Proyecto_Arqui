package com.puj.usuarios.aspectos.registro;

import jakarta.interceptor.InterceptorBinding;
import java.lang.annotation.*;

/**
 * Anotación de interceptor CDI para activar el registro automático de
 * invocaciones de métodos mediante {@link InterceptorRegistro}.
 *
 * <p>Puede aplicarse a nivel de clase o de método. Cuando se aplica a una clase,
 * todos sus métodos quedan registrados automáticamente.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@InterceptorBinding
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Registrable {}
