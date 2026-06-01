package com.puj.usuarios.aspectos.registro;

import jakarta.annotation.Priority;
import jakarta.interceptor.*;
import java.util.logging.*;

/**
 * Interceptor CDI que registra automáticamente la invocación, el resultado y
 * los errores de los métodos marcados con {@link Registrable}.
 *
 * <p>Cada invocación genera una entrada en el log del sistema con el nombre
 * de la clase y el método, así como el tiempo de ejecución en milisegundos.
 * Los errores se registran como advertencias sin suprimir la excepción original.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@Interceptor
@Registrable
@Priority(Interceptor.Priority.APPLICATION + 1)
public class InterceptorRegistro {

    private static final Logger LOG = Logger.getLogger(InterceptorRegistro.class.getName());

    /**
     * Intercepta la invocación del método, registra inicio y fin, y propaga
     * cualquier excepción lanzada por el método destino.
     *
     * @param ctx contexto de invocación del interceptor.
     * @return resultado del método interceptado.
     * @throws Exception cualquier excepción lanzada por el método destino.
     */
    @AroundInvoke
    public Object registrar(InvocationContext ctx) throws Exception {
        String metodo = ctx.getTarget().getClass().getSimpleName()
                + "." + ctx.getMethod().getName();
        long inicio = System.currentTimeMillis();
        try {
            Object resultado = ctx.proceed();
            LOG.log(Level.INFO, "OK [{0}] {1}ms",
                    new Object[]{metodo, System.currentTimeMillis() - inicio});
            return resultado;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "ERROR [{0}]: {1}",
                    new Object[]{metodo, e.getMessage()});
            throw e;
        }
    }
}
