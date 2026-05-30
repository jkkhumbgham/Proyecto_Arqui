package com.puj.usuarios.autenticacion.dominio.excepciones;

import jakarta.ejb.ApplicationException;

/**
 * Excepción de dominio lanzada cuando no se encuentra un usuario activo
 * con el identificador o correo proporcionado.
 *
 * <p>Marcada con {@code @ApplicationException(rollback=true)} para que el
 * contenedor Jakarta EE revierta automáticamente la transacción activa al
 * propagarse esta excepción.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@ApplicationException(rollback = true)
public class UsuarioNoEncontradoException extends RuntimeException {

    /**
     * Construye la excepción con el mensaje descriptivo indicado.
     *
     * @param mensaje descripción del error.
     */
    public UsuarioNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
