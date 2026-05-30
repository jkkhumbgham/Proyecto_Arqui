package com.puj.usuarios.autenticacion.dominio;

import java.util.List;
import java.util.UUID;

/**
 * Contrato de repositorio para la entidad {@link RegistroAuditoria}.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
public interface RepositorioAuditoria {

    /**
     * Persiste una nueva entrada de auditoría.
     *
     * @param registro entrada de auditoría a guardar.
     */
    void guardar(RegistroAuditoria registro);

    /**
     * Devuelve una página de entradas de auditoría para un usuario dado.
     *
     * @param idUsuario UUID del usuario cuyos eventos se desean consultar.
     * @param pagina    número de página (basado en cero).
     * @param cantidad  número máximo de resultados por página.
     * @return lista de entradas de auditoría del usuario.
     */
    List<RegistroAuditoria> buscarPorUsuario(UUID idUsuario, int pagina, int cantidad);
}
