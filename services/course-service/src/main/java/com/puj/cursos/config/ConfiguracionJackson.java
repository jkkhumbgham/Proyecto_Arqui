package com.puj.cursos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;

/**
 * Proveedor JAX-RS que configura el {@link ObjectMapper} de Jackson para toda la aplicación.
 *
 * <p>Registra el módulo {@code JavaTimeModule} para soporte de tipos {@code java.time}
 * y deshabilita la serialización de fechas como timestamps numéricos, forzando el
 * formato ISO-8601 en todas las respuestas JSON.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Provider
public class ConfiguracionJackson implements ContextResolver<ObjectMapper> {

    /** Instancia compartida del mapper con la configuración aplicada. */
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Retorna el {@link ObjectMapper} configurado para cualquier tipo solicitado.
     *
     * @param  tipo  clase para la que se solicita el mapper (no utilizado)
     * @return el mapper global con soporte de fechas ISO-8601
     */
    @Override
    public ObjectMapper getContext(Class<?> tipo) {
        return mapper;
    }
}
