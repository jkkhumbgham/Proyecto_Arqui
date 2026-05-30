package com.puj.usuarios.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;

/**
 * Proveedor JAX-RS que configura el {@link ObjectMapper} global de Jackson.
 *
 * <p>Registra el módulo {@link JavaTimeModule} para serializar correctamente
 * los tipos {@code java.time.*} (ej. {@code Instant}, {@code LocalDate}) y
 * desactiva la escritura de fechas como timestamps numéricos, emitiendo en su
 * lugar cadenas ISO-8601.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@Provider
public class ConfiguracionJackson implements ContextResolver<ObjectMapper> {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Devuelve el {@link ObjectMapper} configurado para el tipo solicitado.
     *
     * @param type clase para la que se solicita el mapper; no se usa en esta
     *             implementación porque se devuelve siempre el mismo mapper.
     * @return instancia compartida de {@link ObjectMapper} con configuración
     *         de fecha ISO-8601.
     */
    @Override
    public ObjectMapper getContext(Class<?> type) {
        return mapper;
    }
}
