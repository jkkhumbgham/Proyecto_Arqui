package com.puj.collaboration.resilience;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Circuit Breaker simple para proteger llamadas HTTP inter-servicio.
 *
 * <p>Implementa el patrón de tres estados requerido por RNF-04b (aislamiento de
 * fallos entre servicios):</p>
 * <pre>
 *   CLOSED ──(N fallos)──► OPEN ──(timeout)──► HALF_OPEN ──(éxito)──► CLOSED
 *                                                          └─(fallo)──► OPEN
 * </pre>
 *
 * <p>Cuando el circuit breaker está {@code OPEN}, las llamadas se rechazan
 * inmediatamente sin intentar la petición. Tras el período de recuperación
 * ({@value #RECOVERY_TIMEOUT_SECONDS} segundos) pasa a {@code HALF_OPEN} y permite
 * una petición de prueba. Si tiene éxito, vuelve a {@code CLOSED}; si falla,
 * regresa a {@code OPEN}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class CircuitBreaker {

    private static final Logger   LOG                     =
            Logger.getLogger(CircuitBreaker.class.getName());
    private static final int      FAILURE_THRESHOLD       = 5;
    private static final int      RECOVERY_TIMEOUT_SECONDS = 30;
    private static final Duration RECOVERY_TIMEOUT        =
            Duration.ofSeconds(RECOVERY_TIMEOUT_SECONDS);

    /**
     * Estado del circuit breaker.
     */
    public enum State {
        /** Todas las peticiones pasan normalmente. */
        CLOSED,
        /** Las peticiones se rechazan hasta que expire el período de recuperación. */
        OPEN,
        /** Se permite una petición de prueba para verificar si el servicio recuperó. */
        HALF_OPEN
    }

    private final AtomicReference<State> state        = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger          failureCount  = new AtomicInteger(0);
    private volatile Instant             openedAt;
    private final String                 name;

    /**
     * Constructor por defecto: crea un circuit breaker con nombre {@code "default"}.
     */
    public CircuitBreaker() { this.name = "default"; }

    /**
     * Determina si una nueva petición debe ser permitida según el estado actual.
     *
     * <p>Cuando el estado es {@code OPEN} y ha transcurrido el período de
     * recuperación, transiciona automáticamente a {@code HALF_OPEN} y permite
     * la petición de prueba.</p>
     *
     * @return {@code true} si la petición puede ejecutarse, {@code false} si debe
     *         ser rechazada (circuit abierto y período de recuperación no expirado)
     */
    public boolean allowRequest() {
        State s = state.get();
        if (s == State.CLOSED) return true;

        if (s == State.OPEN) {
            if (openedAt != null
                    && Instant.now().isAfter(openedAt.plus(RECOVERY_TIMEOUT))) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    LOG.info("[CircuitBreaker:" + name
                            + "] OPEN → HALF_OPEN — trying recovery request");
                }
                return true;
            }
            return false;
        }

        return true; // HALF_OPEN: allow one request
    }

    /**
     * Registra una petición exitosa y transiciona al estado {@code CLOSED}.
     *
     * <p>Reinicia el contador de fallos. Si el estado anterior era distinto de
     * {@code CLOSED}, registra la recuperación en el log.</p>
     */
    public void recordSuccess() {
        State prev = state.get();
        failureCount.set(0);
        state.set(State.CLOSED);
        if (prev != State.CLOSED) {
            LOG.info("[CircuitBreaker:" + name
                    + "] " + prev + " → CLOSED (recovery successful)");
        }
    }

    /**
     * Registra una petición fallida e incrementa el contador de fallos.
     *
     * <p>Transiciona al estado {@code OPEN} si el número de fallos alcanza
     * el umbral configurado o si el estado actual es {@code HALF_OPEN}.</p>
     */
    public void recordFailure() {
        int count = failureCount.incrementAndGet();
        if (state.get() == State.HALF_OPEN || count >= FAILURE_THRESHOLD) {
            openedAt = Instant.now();
            state.set(State.OPEN);
            LOG.warning("[CircuitBreaker:" + name
                    + "] → OPEN after " + count + " consecutive failures");
        }
    }

    /**
     * Retorna el estado actual del circuit breaker.
     *
     * @return uno de {@link State#CLOSED}, {@link State#OPEN} o {@link State#HALF_OPEN}
     */
    public State getState() { return state.get(); }
}
