package com.puj.cursos.lecciones.dominio;

import java.util.Optional;
import java.util.UUID;

/**
 * Contrato del repositorio de {@link Leccion lecciones}.
 *
 * <p>Define las operaciones de persistencia necesarias para la capa de dominio,
 * desacoplando la lógica de negocio de la implementación concreta JPA.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public interface RepositorioLecciones {

    /**
     * Persiste una nueva lección o fusiona los cambios de una existente.
     *
     * @param  leccion entidad a guardar; no debe ser {@code null}
     * @return la entidad gestionada tras la operación
     */
    Leccion guardar(Leccion leccion);

    /**
     * Busca una lección por su identificador, excluyendo las que tienen borrado lógico.
     *
     * @param  id identificador de la lección
     * @return {@link Optional} con la lección si existe y no fue eliminada
     */
    Optional<Leccion> buscarPorId(UUID id);
}
