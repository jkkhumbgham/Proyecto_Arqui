package com.puj.seguridad.rbac;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.*;

/**
 * Anotación de binding para control de acceso basado en roles (RBAC).
 *
 * <p>Aplica el {@link InterceptorRbac} al método o clase anotada. Si se especifican
 * roles en {@link #value()}, solo los usuarios con alguno de esos roles pueden
 * acceder. Si no se especifican roles, cualquier usuario autenticado puede acceder.</p>
 *
 * <h3>Uso en método:</h3>
 * <pre>{@code
 * @GET
 * @RequiereRol(Rol.ADMIN)
 * public Response listarUsuarios() { ... }
 * }</pre>
 *
 * <h3>Uso en clase (aplica a todos los métodos):</h3>
 * <pre>{@code
 * @RequiereRol({Rol.INSTRUCTOR, Rol.ADMIN})
 * public class CursoResource { ... }
 * }</pre>
 *
 * @author Plataforma PUJ
 * @since 1.0
 * @see InterceptorRbac
 * @see Rol
 */
@Inherited
@InterceptorBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiereRol {

    /**
     * Roles que tienen acceso al método o clase anotada.
     *
     * <p>Si está vacío, solo se exige autenticación sin restricción de rol.</p>
     *
     * @return array de roles permitidos
     */
    @Nonbinding
    Rol[] value() default {};
}
