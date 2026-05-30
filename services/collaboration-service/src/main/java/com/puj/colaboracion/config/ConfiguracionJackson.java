package com.puj.colaboracion.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;

/**
 * Configuración global de Jackson para el servicio de colaboración.
 *
 * <p>Registra el módulo {@link JavaTimeModule} para soportar tipos
 * {@code java.time.*} y deshabilita la serialización de fechas como
 * timestamps numéricos, forzando el formato ISO-8601.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Provider
public class ConfiguracionJackson implements ContextResolver<ObjectMapper> {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Devuelve la instancia compartida de {@link ObjectMapper} configurada
     * para la serialización JSON del servicio.
     *
     * @param type tipo de clase para el que se solicita el mapper (no utilizado)
     * @return instancia de {@link ObjectMapper} con soporte para tipos {@code java.time}
     */
    @Override
    public ObjectMapper getContext(Class<?> type) {
        return mapper;
    }
}
