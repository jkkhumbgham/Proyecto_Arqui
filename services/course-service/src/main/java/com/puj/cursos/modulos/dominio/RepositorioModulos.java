package com.puj.cursos.modulos.dominio;

import java.util.Optional;
import java.util.UUID;

/**
 * Contrato del repositorio de {@link Modulo módulos}.
 *
 * <p>Define las operaciones de persistencia necesarias para la capa de dominio,
 * desacoplando la lógica de negocio de la implementación concreta JPA.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public interface RepositorioModulos {

    /**
     * Persiste un nuevo módulo o fusiona los cambios de uno existente.
     *
     * @param  modulo entidad a guardar; no debe ser {@code null}
     * @return la entidad gestionada tras la operación
     */
    Modulo guardar(Modulo modulo);

    /**
     * Busca un módulo por su identificador, excluyendo los que tienen borrado lógico.
     *
     * @param  id identificador del módulo
     * @return {@link Optional} con el módulo si existe y no fue eliminado
     */
    Optional<Modulo> buscarPorId(UUID id);
}
