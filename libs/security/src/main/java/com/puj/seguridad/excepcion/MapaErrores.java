package com.puj.seguridad.excepcion;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mapea excepciones JAX-RS a respuestas JSON uniformes en todos los servicios.
 *
 * <p>Esta clase está en {@code libs/security} y es compartida por todos los
 * microservicios vía Maven. Elimina la duplicación de lógica de manejo de errores
 * que anteriormente existía en cada servicio por separado.</p>
 *
 * <p>Formato de respuesta de error:</p>
 * <pre>{@code
 * {
 *   "error":     "NOT_FOUND",
 *   "message":   "Recurso no encontrado",
 *   "timestamp": "2026-05-29T12:00:00Z"
 * }
 * }</pre>
 *
 * <p>Excepciones mapeadas:</p>
 * <ul>
 *   <li>{@link BadRequestException}                    → HTTP 400</li>
 *   <li>{@link NotAuthorizedException}                 → HTTP 401</li>
 *   <li>{@link ForbiddenException}                     → HTTP 403</li>
 *   <li>{@link NotFoundException}                      → HTTP 404</li>
 *   <li>{@link jakarta.validation.ConstraintViolationException} → HTTP 400</li>
 *   <li>Cualquier otra {@link Exception}               → HTTP 500</li>
 * </ul>
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@Provider
public class MapaErrores implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(MapaErrores.class.getName());

    /**
     * Convierte una excepción en una respuesta HTTP JSON estructurada.
     *
     * @param ex excepción capturada por el runtime JAX-RS
     * @return respuesta HTTP con código de estado apropiado y cuerpo JSON
     */
    @Override
    public Response toResponse(Exception ex) {
        if (ex instanceof BadRequestException e)
            return error(400, "BAD_REQUEST", e.getMessage());
        if (ex instanceof NotAuthorizedException)
            return error(401, "UNAUTHORIZED", "Autenticación requerida.");
        if (ex instanceof ForbiddenException)
            return error(403, "FORBIDDEN", "Acceso denegado.");
        if (ex instanceof NotFoundException e)
            return error(404, "NOT_FOUND", e.getMessage());
        if (ex instanceof jakarta.validation.ConstraintViolationException e) {
            String msg = construirMensajeValidacion(e);
            return error(400, "VALIDATION_ERROR", msg);
        }
        LOG.log(Level.SEVERE, "Error no manejado", ex);
        return error(500, "INTERNAL_ERROR", "Error interno del servidor.");
    }

    /**
     * Construye el cuerpo JSON de error con timestamp.
     *
     * @param status  código de estado HTTP
     * @param codigo  código de error legible por máquina
     * @param mensaje mensaje descriptivo del error
     * @return respuesta JAX-RS con el cuerpo JSON y el Content-Type correcto
     */
    private Response error(int status, String codigo, String mensaje) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(String.format(
                        "{\"error\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                        codigo, mensaje, Instant.now()))
                .build();
    }

    /**
     * Concatena todos los mensajes de violación de constraints en un solo string.
     *
     * @param ex excepción de validación Bean Validation
     * @return string con todas las violaciones separadas por {@code "; "}
     */
    private String construirMensajeValidacion(
            jakarta.validation.ConstraintViolationException ex) {
        return ex.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Datos de entrada inválidos.");
    }
}
