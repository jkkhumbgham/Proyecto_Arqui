package com.puj.colaboracion.resiliencia;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Disyuntor de circuito simple para proteger llamadas HTTP inter-servicio.
 *
 * <p>Implementa el patrón de tres estados requerido por RNF-04b (aislamiento de
 * fallos entre servicios):</p>
 * <pre>
 *   CERRADO ──(N fallos)──► ABIERTO ──(timeout)──► SEMIABIERTO ──(éxito)──► CERRADO
 *                                                               └─(fallo)──► ABIERTO
 * </pre>
 *
 * <p>Cuando el disyuntor está {@code ABIERTO}, las llamadas se rechazan
 * inmediatamente sin intentar la petición. Tras el período de recuperación
 * ({@value #SEGUNDOS_RECUPERACION} segundos) pasa a {@code SEMIABIERTO} y permite
 * una petición de prueba. Si tiene éxito, vuelve a {@code CERRADO}; si falla,
 * regresa a {@code ABIERTO}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class DisyuntorCircuito {

    private static final Logger  LOG                  =
            Logger.getLogger(DisyuntorCircuito.class.getName());
    private static final int     UMBRAL_FALLOS        = 5;
    private static final int     SEGUNDOS_RECUPERACION = 30;
    private static final Duration TIMEOUT_RECUPERACION =
            Duration.ofSeconds(SEGUNDOS_RECUPERACION);

    /**
     * Estado del disyuntor de circuito.
     */
    public enum Estado {
        /** Todas las peticiones pasan normalmente. */
        CERRADO,
        /** Las peticiones se rechazan hasta que expire el período de recuperación. */
        ABIERTO,
        /** Se permite una petición de prueba para verificar si el servicio recuperó. */
        SEMIABIERTO
    }

    private final AtomicReference<Estado> estado       = new AtomicReference<>(Estado.CERRADO);
    private final AtomicInteger           contadorFallos = new AtomicInteger(0);
    private volatile Instant              abiertoEn;
    private final String                  nombre;

    /**
     * Constructor por defecto: crea un disyuntor con nombre {@code "default"}.
     */
    public DisyuntorCircuito() { this.nombre = "default"; }

    /**
     * Determina si una nueva petición debe ser permitida según el estado actual.
     *
     * <p>Cuando el estado es {@code ABIERTO} y ha transcurrido el período de
     * recuperación, transiciona automáticamente a {@code SEMIABIERTO} y permite
     * la petición de prueba.</p>
     *
     * @return {@code true} si la petición puede ejecutarse, {@code false} si debe
     *         ser rechazada (disyuntor abierto y período de recuperación no expirado)
     */
    public boolean permitirPeticion() {
        Estado s = estado.get();
        if (s == Estado.CERRADO) return true;

        if (s == Estado.ABIERTO) {
            if (abiertoEn != null
                    && Instant.now().isAfter(abiertoEn.plus(TIMEOUT_RECUPERACION))) {
                if (estado.compareAndSet(Estado.ABIERTO, Estado.SEMIABIERTO)) {
                    LOG.info("[DisyuntorCircuito:" + nombre
                            + "] ABIERTO → SEMIABIERTO — probando recuperación");
                }
                return true;
            }
            return false;
        }

        return true; // SEMIABIERTO: permite una petición
    }

    /**
     * Registra una petición exitosa y transiciona al estado {@code CERRADO}.
     *
     * <p>Reinicia el contador de fallos.</p>
     */
    public void registrarExito() {
        Estado prev = estado.get();
        contadorFallos.set(0);
        estado.set(Estado.CERRADO);
        if (prev != Estado.CERRADO) {
            LOG.info("[DisyuntorCircuito:" + nombre
                    + "] " + prev + " → CERRADO (recuperación exitosa)");
        }
    }

    /**
     * Registra una petición fallida e incrementa el contador de fallos.
     *
     * <p>Transiciona al estado {@code ABIERTO} si el número de fallos alcanza
     * el umbral configurado o si el estado actual es {@code SEMIABIERTO}.</p>
     */
    public void registrarFalla() {
        int conteo = contadorFallos.incrementAndGet();
        if (estado.get() == Estado.SEMIABIERTO || conteo >= UMBRAL_FALLOS) {
            abiertoEn = Instant.now();
            estado.set(Estado.ABIERTO);
            LOG.warning("[DisyuntorCircuito:" + nombre
                    + "] → ABIERTO tras " + conteo + " fallos consecutivos");
        }
    }

    /**
     * Retorna el estado actual del disyuntor de circuito.
     *
     * @return uno de {@link Estado#CERRADO}, {@link Estado#ABIERTO} o
     *         {@link Estado#SEMIABIERTO}
     */
    public Estado getEstado() { return estado.get(); }

    /**
     * Indica si el disyuntor está actualmente abierto (rechazando peticiones).
     *
     * @return {@code true} si el estado es {@link Estado#ABIERTO}
     */
    public boolean estaAbierto() { return estado.get() == Estado.ABIERTO; }
}
