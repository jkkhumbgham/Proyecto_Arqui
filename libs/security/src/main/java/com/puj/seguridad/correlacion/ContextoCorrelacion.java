package com.puj.seguridad.correlacion;

import jakarta.enterprise.context.RequestScoped;

/**
 * Mantiene el X-Correlation-ID para la solicitud actual.
 * Es inyectado por {@link FiltroIdCorrelacion}.
 */
@RequestScoped
public class ContextoCorrelacion {

    private String idCorrelacion;

    /**
     * Retorna el identificador de correlación para la solicitud actual.
     *
     * @return idCorrelacion o {@code null} si no fue establecido
     */
    public String obtenerIdCorrelacion()        { return idCorrelacion; }

    /**
     * Establece el identificador de correlación para la solicitud actual.
     *
     * @param id identificador de correlación
     */
    public void   establecerIdCorrelacion(String id) { this.idCorrelacion = id; }
}
