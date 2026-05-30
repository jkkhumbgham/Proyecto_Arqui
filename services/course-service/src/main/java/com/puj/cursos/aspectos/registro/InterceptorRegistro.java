package com.puj.cursos.aspectos.registro;

import jakarta.annotation.Priority;
import jakarta.interceptor.*;
import java.util.logging.*;

/**
 * Interceptor CDI que registra el tiempo de ejecución y los errores de los métodos
 * anotados con {@link Registrable}.
 *
 * <p>Para cada invocación registra: nombre del método, tiempo transcurrido en milisegundos
 * en caso de éxito; o el mensaje de error en caso de excepción. El nivel de prioridad
 * {@code APPLICATION + 1} garantiza que se aplique después de los interceptores del framework.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Interceptor
@Registrable
@Priority(Interceptor.Priority.APPLICATION + 1)
public class InterceptorRegistro {

    private static final Logger LOG = Logger.getLogger(InterceptorRegistro.class.getName());

    /**
     * Intercepta la invocación del método, registra el resultado y propaga la excepción
     * si ocurre alguna.
     *
     * @param  ctx contexto de invocación proporcionado por el contenedor CDI
     * @return el resultado del método interceptado
     * @throws Exception si el método interceptado lanza una excepción
     */
    @AroundInvoke
    public Object registrar(InvocationContext ctx) throws Exception {
        String metodo = ctx.getTarget().getClass().getSimpleName() + "." + ctx.getMethod().getName();
        long inicio = System.currentTimeMillis();
        try {
            Object resultado = ctx.proceed();
            LOG.log(Level.INFO, "OK [{0}] {1}ms", new Object[]{metodo, System.currentTimeMillis() - inicio});
            return resultado;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "ERROR [{0}]: {1}", new Object[]{metodo, e.getMessage()});
            throw e;
        }
    }
}
