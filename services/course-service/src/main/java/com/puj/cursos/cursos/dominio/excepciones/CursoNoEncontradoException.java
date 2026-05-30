package com.puj.cursos.cursos.dominio.excepciones;

import jakarta.ejb.ApplicationException;

/**
 * Excepción de dominio lanzada cuando no se encuentra un curso con el identificador indicado.
 *
 * <p>Al estar anotada con {@link ApplicationException}, el contenedor EJB no la envuelve
 * en una {@code EJBException} y la propaga directamente hasta la capa REST.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationException(rollback = false)
public class CursoNoEncontradoException extends RuntimeException {

    /**
     * Construye la excepción con el mensaje indicado.
     *
     * @param mensaje descripción del error
     */
    public CursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
