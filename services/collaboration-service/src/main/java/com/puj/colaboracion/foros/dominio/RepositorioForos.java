package com.puj.colaboracion.foros.dominio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato del repositorio de foros, hilos y publicaciones.
 *
 * <p>Define las operaciones de persistencia y consulta necesarias para el
 * dominio de foros, desacoplando la lógica de negocio de la implementación
 * JPA concreta ({@link com.puj.colaboracion.foros.infraestructura.RepositorioForosJpa}).</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public interface RepositorioForos {

    /**
     * Persiste o actualiza un foro.
     *
     * @param foro foro a persistir o actualizar
     * @return la instancia administrada por JPA
     */
    Foro guardar(Foro foro);

    /**
     * Busca un foro por su identificador primario, excluyendo eliminados.
     *
     * @param id UUID del foro
     * @return {@link Optional} con el foro, o vacío si no existe o está eliminado
     */
    Optional<Foro> buscarPorId(UUID id);

    /**
     * Retorna todos los foros activos ordenados por fecha de creación descendente.
     *
     * @return lista de foros activos (puede estar vacía)
     */
    List<Foro> buscarTodos();

    /**
     * Busca el foro activo de un curso.
     *
     * @param idCurso UUID del curso
     * @return {@link Optional} con el foro del curso, o vacío si no existe
     */
    Optional<Foro> buscarPorCurso(UUID idCurso);

    /**
     * Persiste un nuevo hilo (INSERT).
     *
     * @param hilo hilo a persistir
     * @return la misma instancia administrada por JPA
     */
    Hilo guardarHilo(Hilo hilo);

    /**
     * Actualiza un hilo existente (UPDATE/merge).
     *
     * @param hilo hilo a actualizar
     * @return la instancia administrada resultante del merge
     */
    Hilo fusionarHilo(Hilo hilo);

    /**
     * Busca un hilo por su identificador primario, excluyendo eliminados.
     *
     * @param id UUID del hilo
     * @return {@link Optional} con el hilo, o vacío si no existe o está eliminado
     */
    Optional<Hilo> buscarHiloPorId(UUID id);

    /**
     * Retorna los hilos activos de un foro con el conteo de publicaciones activas.
     *
     * @param idForo UUID del foro
     * @return lista de pares {@code [Hilo, conteoPublicaciones]}
     */
    List<Object[]> buscarHilosConConteoPublicaciones(UUID idForo);

    /**
     * Retorna las publicaciones activas de un hilo en orden cronológico ascendente.
     *
     * @param idHilo UUID del hilo
     * @return lista de publicaciones activas del hilo (puede estar vacía)
     */
    List<Publicacion> buscarPublicacionesPorHilo(UUID idHilo);

    /**
     * Persiste o actualiza una publicación.
     *
     * @param publicacion publicación a persistir o actualizar
     * @return la instancia administrada por JPA
     */
    Publicacion guardarPublicacion(Publicacion publicacion);

    /**
     * Busca una publicación por su identificador primario, excluyendo eliminadas.
     *
     * @param id UUID de la publicación
     * @return {@link Optional} con la publicación, o vacío si no existe o está eliminada
     */
    Optional<Publicacion> buscarPublicacionPorId(UUID id);
}
