package com.puj.courses.exception;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

@Provider
public class ErrorMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(ErrorMapper.class.getName());

    @Override
    public Response toResponse(Exception ex) {
        if (ex instanceof BadRequestException e) return error(400, "BAD_REQUEST", e.getMessage());
        if (ex instanceof NotAuthorizedException)  return error(401, "UNAUTHORIZED", "Autenticación requerida.");
        if (ex instanceof ForbiddenException)      return error(403, "FORBIDDEN", "Acceso denegado.");
        if (ex instanceof NotFoundException e)     return error(404, "NOT_FOUND", e.getMessage());
        if (ex instanceof jakarta.validation.ConstraintViolationException e) {
            String msg = e.getConstraintViolations().stream()
                    .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                    .reduce((a, b) -> a + "; " + b).orElse("Datos inválidos.");
            return error(400, "VALIDATION_ERROR", msg);
        }
        LOG.log(Level.SEVERE, "Error no manejado", ex);
        return error(500, "INTERNAL_ERROR", "Error interno del servidor.");
    }

    private Response error(int status, String code, String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON)
                .entity(String.format("{\"error\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                        code, message, Instant.now()))
                .build();
    }
}
