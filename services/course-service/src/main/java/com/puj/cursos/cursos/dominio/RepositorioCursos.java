package com.puj.cursos.cursos.dominio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato del repositorio de {@link Curso cursos}.
 *
 * <p>Define las operaciones de persistencia necesarias para la capa de dominio,
 * desacoplando la lógica de negocio de la implementación concreta JPA.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public interface RepositorioCursos {

    /**
     * Persiste o actualiza un curso.
     *
     * @param  curso entidad a guardar; no debe ser {@code null}
     * @return la entidad gestionada tras la operación
     */
    Curso guardar(Curso curso);

    /**
     * Busca un curso por su identificador, excluyendo los que tienen borrado lógico.
     *
     * @param  id identificador del curso
     * @return {@link Optional} con el curso si existe y no fue eliminado
     */
    Optional<Curso> buscarPorId(UUID id);

    /**
     * Devuelve la página indicada de cursos en estado {@code PUBLISHED}.
     *
     * @param  pagina número de página (base 0)
     * @param  tamano cantidad máxima de resultados
     * @return lista de cursos publicados
     */
    List<Curso> buscarPublicados(int pagina, int tamano);

    /**
     * Devuelve la página indicada de cursos pertenecientes a un instructor.
     *
     * @param  idInstructor identificador del instructor propietario
     * @param  pagina       número de página (base 0)
     * @param  tamano       cantidad máxima de resultados
     * @return lista de cursos del instructor
     */
    List<Curso> buscarPorInstructor(UUID idInstructor, int pagina, int tamano);

    /**
     * Cuenta el total de cursos en estado {@code PUBLISHED} y sin borrado lógico.
     *
     * @return número de cursos publicados
     */
    long contarPublicados();

    /**
     * Verifica si un instructor es propietario de un curso determinado.
     *
     * @param  idCurso      identificador del curso
     * @param  idInstructor identificador del instructor
     * @return {@code true} si el instructor es propietario del curso activo
     */
    boolean esPropietario(UUID idCurso, UUID idInstructor);
}
