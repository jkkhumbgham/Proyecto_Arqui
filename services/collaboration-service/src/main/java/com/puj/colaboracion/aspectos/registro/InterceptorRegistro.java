package com.puj.colaboracion.aspectos.registro;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Interceptor CDI que registra automáticamente la entrada, salida y duración
 * de cada método en clases anotadas con {@link Registrable}.
 *
 * <p>Los errores se registran con nivel {@code SEVERE} antes de ser
 * relanzados para no suprimir excepciones de negocio.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Registrable
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class InterceptorRegistro {

    /**
     * Intercepta la invocación de un método y registra entrada, salida y duración.
     *
     * @param ctx contexto de invocación CDI
     * @return resultado del método interceptado
     * @throws Exception si el método interceptado lanza una excepción
     */
    @AroundInvoke
    public Object registrar(InvocationContext ctx) throws Exception {
        Logger log = Logger.getLogger(ctx.getTarget().getClass().getName());
        String metodo = ctx.getMethod().getName();

        log.fine(() -> "[INICIO] " + metodo);
        long inicio = System.currentTimeMillis();

        try {
            Object resultado = ctx.proceed();
            long duracion = System.currentTimeMillis() - inicio;
            log.fine(() -> "[FIN] " + metodo + " — " + duracion + " ms");
            return resultado;
        } catch (Exception e) {
            log.log(Level.SEVERE, "[ERROR] " + metodo + " lanzó excepción", e);
            throw e;
        }
    }
}
