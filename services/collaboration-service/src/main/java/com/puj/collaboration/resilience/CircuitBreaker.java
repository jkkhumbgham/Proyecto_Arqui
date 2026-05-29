package com.puj.collaboration.resilience;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Circuit Breaker simple para proteger llamadas HTTP inter-servicio.
 * Estados: CLOSED → OPEN (tras N fallos) → HALF_OPEN → CLOSED (si el siguiente intento es exitoso).
 *
 * Requerido por RNF-04b: aislamiento de fallos entre servicios.
 */
@ApplicationScoped
public class CircuitBreaker {

    private static final Logger LOG = Logger.getLogger(CircuitBreaker.class.getName());

    private static final int      FAILURE_THRESHOLD  = 5;
    private static final Duration RECOVERY_TIMEOUT   = Duration.ofSeconds(30);

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final AtomicReference<State>   state        = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger            failureCount = new AtomicInteger(0);
    private volatile Instant               openedAt;
    private final String                   name;

    public CircuitBreaker() { this.name = "default"; }

    /** Returns true if a request should be allowed through. */
    public boolean allowRequest() {
        State s = state.get();
        if (s == State.CLOSED) return true;

        if (s == State.OPEN) {
            if (openedAt != null && Instant.now().isAfter(openedAt.plus(RECOVERY_TIMEOUT))) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    LOG.info("[CircuitBreaker:" + name + "] OPEN → HALF_OPEN — trying recovery request");
                }
                return true;
            }
            return false;
        }

        return true; // HALF_OPEN: allow one request
    }

    public void recordSuccess() {
        State prev = state.get();
        failureCount.set(0);
        state.set(State.CLOSED);
        if (prev != State.CLOSED) {
            LOG.info("[CircuitBreaker:" + name + "] " + prev + " → CLOSED (recovery successful)");
        }
    }

    public void recordFailure() {
        int count = failureCount.incrementAndGet();
        if (state.get() == State.HALF_OPEN || count >= FAILURE_THRESHOLD) {
            openedAt = Instant.now();
            state.set(State.OPEN);
            LOG.warning("[CircuitBreaker:" + name + "] → OPEN after " + count + " consecutive failures");
        }
    }

    public State getState() { return state.get(); }
}
