package com.puj.colaboracion.aspectos.registro;

import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotación de binding para el interceptor de registro (logging) automático.
 *
 * <p>Clases o métodos marcados con {@code @Registrable} tendrán sus
 * invocaciones interceptadas por {@link InterceptorRegistro}, que registra
 * automáticamente la entrada, salida y duración de cada llamada a método.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Inherited
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Registrable {
}
