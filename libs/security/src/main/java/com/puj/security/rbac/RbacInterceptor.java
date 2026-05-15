package com.puj.security.rbac;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;

import java.util.Arrays;

@Interceptor
@RequiresRole
@Priority(Interceptor.Priority.APPLICATION)
public class RbacInterceptor {

    @Inject
    private AuthenticatedUser authenticatedUser;

    @AroundInvoke
    public Object checkRole(InvocationContext ctx) throws Exception {
        if (!authenticatedUser.isAuthenticated()) {
            throw new NotAuthorizedException("Bearer realm=\"puj\"");
        }

        RequiresRole annotation = ctx.getMethod().getAnnotation(RequiresRole.class);
        if (annotation == null) {
            annotation = ctx.getTarget().getClass().getAnnotation(RequiresRole.class);
        }

        if (annotation != null && annotation.value().length > 0) {
            Role[] allowed = annotation.value();
            boolean granted = Arrays.stream(allowed)
                    .anyMatch(r -> r == authenticatedUser.getRole());
            if (!granted) {
                throw new ForbiddenException("Rol insuficiente. Requerido: "
                        + Arrays.toString(allowed));
            }
        }

        return ctx.proceed();
    }
}
