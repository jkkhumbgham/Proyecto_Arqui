package com.puj.seguridad.rbac;

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
 * anotados con {@link RequiereRol}.
 *
 * <p>Se ejecuta después de que {@link com.puj.seguridad.jwt.FiltroJwt} haya
 * poblado {@link UsuarioAutenticado}. Verifica dos condiciones:</p>
 * <ol>
 *   <li>Que el usuario esté autenticado → HTTP 401 si no</li>
 *   <li>Que su rol esté en la lista de roles permitidos → HTTP 403 si no</li>
 * </ol>
 *
 * <p>Si la anotación {@link RequiereRol} no especifica roles ({@code value} vacío),
 * solo se exige autenticación.</p>
 *
 * @author Plataforma PUJ
 * @since 1.0
 * @see RequiereRol
 * @see UsuarioAutenticado
 */
@Interceptor
@RequiereRol
@Priority(Interceptor.Priority.APPLICATION)
public class InterceptorRbac {

    @Inject
    private UsuarioAutenticado usuarioAutenticado;

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
    public Object verificarRol(InvocationContext ctx) throws Exception {
        if (!usuarioAutenticado.estaAutenticado()) {
            throw new NotAuthorizedException("Bearer realm=\"puj\"");
        }

        Rol[] permitidos = resolverRolesPermitidos(ctx);
        if (permitidos != null && permitidos.length > 0) {
            boolean concedido = Arrays.stream(permitidos)
                    .anyMatch(r -> r == usuarioAutenticado.obtenerRol());
            if (!concedido) {
                throw new ForbiddenException(
                        "Rol insuficiente. Requerido: " + Arrays.toString(permitidos));
            }
        }

        return ctx.proceed();
    }

    /**
     * Resuelve los roles permitidos buscando la anotación {@link RequiereRol}
     * primero en el método y luego en la clase.
     *
     * @param ctx contexto de la invocación
     * @return array de roles permitidos; puede ser vacío si no se especificaron
     */
    private Rol[] resolverRolesPermitidos(InvocationContext ctx) {
        RequiereRol anotacion = ctx.getMethod().getAnnotation(RequiereRol.class);
        if (anotacion == null) {
            anotacion = ctx.getTarget().getClass().getAnnotation(RequiereRol.class);
        }
        return anotacion != null ? anotacion.value() : new Rol[0];
    }
}
