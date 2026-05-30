package com.puj.security.rbac;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;

import java.util.Arrays;

/**
 * Interceptor CDI que aplica RBAC (Role-Based Access Control) en los métodos
 * anotados con {@link RequiresRole}.
 *
 * <p>Se ejecuta después de que {@link com.puj.security.jwt.JwtFilter} haya
 * poblado {@link AuthenticatedUser}. Verifica dos condiciones:</p>
 * <ol>
 *   <li>Que el usuario esté autenticado → HTTP 401 si no</li>
 *   <li>Que su rol esté en la lista de roles permitidos → HTTP 403 si no</li>
 * </ol>
 *
 * <p>Si la anotación {@link RequiresRole} no especifica roles ({@code value} vacío),
 * solo se exige autenticación.</p>
 *
 * @author Plataforma PUJ
 * @since 1.0
 * @see RequiresRole
 * @see AuthenticatedUser
 */
@Interceptor
@RequiresRole
@Priority(Interceptor.Priority.APPLICATION)
public class RbacInterceptor {

    @Inject
    private AuthenticatedUser authenticatedUser;

    /**
     * Intercepta la invocación y verifica autenticación y autorización por rol.
     *
     * @param ctx contexto de la invocación CDI
     * @return resultado del método interceptado si la autorización es exitosa
     * @throws NotAuthorizedException si el usuario no está autenticado (HTTP 401)
     * @throws ForbiddenException     si el rol del usuario no tiene acceso (HTTP 403)
     * @throws Exception              cualquier excepción del método interceptado
     */
    @AroundInvoke
    public Object checkRole(InvocationContext ctx) throws Exception {
        if (!authenticatedUser.isAuthenticated()) {
            throw new NotAuthorizedException("Bearer realm=\"puj\"");
        }

        Role[] allowed = resolveAllowedRoles(ctx);
        if (allowed != null && allowed.length > 0) {
            boolean granted = Arrays.stream(allowed)
                    .anyMatch(r -> r == authenticatedUser.getRole());
            if (!granted) {
                throw new ForbiddenException(
                        "Rol insuficiente. Requerido: " + Arrays.toString(allowed));
            }
        }

        return ctx.proceed();
    }

    /**
     * Resuelve los roles permitidos buscando la anotación {@link RequiresRole}
     * primero en el método y luego en la clase.
     *
     * @param ctx contexto de la invocación
     * @return array de roles permitidos; puede ser vacío si no se especificaron
     */
    private Role[] resolveAllowedRoles(InvocationContext ctx) {
        RequiresRole annotation = ctx.getMethod().getAnnotation(RequiresRole.class);
        if (annotation == null) {
            annotation = ctx.getTarget().getClass().getAnnotation(RequiresRole.class);
        }
        return annotation != null ? annotation.value() : new Role[0];
    }
}
