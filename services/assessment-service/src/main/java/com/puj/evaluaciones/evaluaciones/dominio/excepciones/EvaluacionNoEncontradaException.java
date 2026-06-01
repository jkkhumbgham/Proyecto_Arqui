package com.puj.evaluaciones.evaluaciones.dominio.excepciones;

import java.util.UUID;

/**
 * Excepción de dominio lanzada cuando no se encuentra una evaluación activa
 * con el identificador solicitado.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public class EvaluacionNoEncontradaException extends RuntimeException {

    /**
     * Construye la excepción con un mensaje que incluye el identificador buscado.
     *
     * @param id UUID de la evaluación no encontrada
     */
    public EvaluacionNoEncontradaException(UUID id) {
        super("Evaluación no encontrada: " + id);
    }

    /**
     * Construye la excepción con un mensaje personalizado.
     *
     * @param mensaje mensaje descriptivo del error
     */
    public EvaluacionNoEncontradaException(String mensaje) {
        super(mensaje);
    }
}
