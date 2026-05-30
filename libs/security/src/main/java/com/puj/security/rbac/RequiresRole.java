package com.puj.security.rbac;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.*;

/**
 * Anotación de binding para control de acceso basado en roles (RBAC).
 *
 * <p>Aplica el {@link RbacInterceptor} al método o clase anotada. Si se especifican
 * roles en {@link #value()}, solo los usuarios con alguno de esos roles pueden
 * acceder. Si no se especifican roles, cualquier usuario autenticado puede acceder.</p>
 *
 * <h3>Uso en método:</h3>
 * <pre>{@code
 * @GET
 * @RequiresRole(Role.ADMIN)
 * public Response listUsers() { ... }
 * }</pre>
 *
 * <h3>Uso en clase (aplica a todos los métodos):</h3>
 * <pre>{@code
 * @RequiresRole({Role.INSTRUCTOR, Role.ADMIN})
 * public class CourseResource { ... }
 * }</pre>
 *
 * @author Plataforma PUJ
 * @since 1.0
 * @see RbacInterceptor
 * @see Role
 */
@Inherited
@InterceptorBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {

    /**
     * Roles que tienen acceso al método o clase anotada.
     *
     * <p>Si está vacío, solo se exige autenticación sin restricción de rol.</p>
     *
     * @return array de roles permitidos
     */
    @Nonbinding
    Role[] value() default {};
}
