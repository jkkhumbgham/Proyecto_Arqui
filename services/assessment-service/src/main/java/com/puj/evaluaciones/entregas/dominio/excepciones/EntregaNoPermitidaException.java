package com.puj.evaluaciones.entregas.dominio.excepciones;

/**
 * Excepción de dominio lanzada cuando un estudiante intenta realizar una entrega
 * que no está permitida, por ejemplo cuando ha alcanzado el número máximo de intentos.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public class EntregaNoPermitidaException extends RuntimeException {

    /**
     * Construye la excepción con un mensaje descriptivo.
     *
     * @param mensaje mensaje que describe por qué no se permite la entrega
     */
    public EntregaNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
