package com.puj.cursos.matriculas.dominio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato del repositorio de {@link Matricula matrículas}.
 *
 * <p>Define las operaciones de persistencia necesarias para la capa de dominio,
 * desacoplando la lógica de negocio de la implementación concreta JPA.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public interface RepositorioMatriculas {

    /**
     * Persiste una nueva matrícula.
     *
     * @param  matricula entidad a guardar; no debe ser {@code null}
     * @return la entidad gestionada tras la persistencia
     */
    Matricula guardar(Matricula matricula);

    /**
     * Fusiona los cambios de una matrícula existente con el contexto de persistencia.
     *
     * @param  matricula entidad con los cambios a aplicar; no debe ser {@code null}
     * @return la entidad gestionada tras la fusión
     */
    Matricula fusionar(Matricula matricula);

    /**
     * Busca la matrícula activa de un usuario en un curso específico.
     *
     * @param  idUsuario identificador del estudiante
     * @param  idCurso  identificador del curso
     * @return {@link Optional} con la matrícula si existe y no fue eliminada
     */
    Optional<Matricula> buscarPorUsuarioYCurso(UUID idUsuario, UUID idCurso);

    /**
     * Devuelve matrículas activas de un usuario con paginación.
     *
     * @param  idUsuario identificador del estudiante
     * @param  pagina    número de página (base 0)
     * @param  cantidad  máximo de resultados
     * @return lista paginada de matrículas del estudiante
     */
    List<Matricula> buscarPorUsuario(UUID idUsuario, int pagina, int cantidad);

    /**
     * Verifica si un usuario ya está matriculado en un curso.
     *
     * @param  idUsuario identificador del estudiante
     * @param  idCurso  identificador del curso
     * @return {@code true} si existe una matrícula activa
     */
    boolean estaMatriculado(UUID idUsuario, UUID idCurso);

    /**
     * Cuenta el número de matrículas activas en un curso.
     *
     * @param  idCurso identificador del curso
     * @return número de matrículas activas (no borradas lógicamente)
     */
    long contarPorCurso(UUID idCurso);

    /**
     * Calcula el promedio del porcentaje de progreso de todos los estudiantes
     * matriculados activamente en un curso.
     *
     * @param  idCurso identificador del curso
     * @return promedio de progreso (0.0 si no hay matrículas)
     */
    double promedioProgresoPorCurso(UUID idCurso);

    /**
     * Devuelve las filas agregadas de progreso por (usuario, módulo) para un curso.
     *
     * @param  idCurso identificador del curso
     * @return lista de filas de progreso agrupadas por (usuario, módulo)
     */
    List<Object[]> ultimoModuloPorUsuario(UUID idCurso);

    /**
     * Cuenta los estudiantes únicos matriculados en al menos uno de los cursos indicados.
     *
     * @param  idsCurso lista de identificadores de cursos
     * @return número de estudiantes únicos
     */
    long contarEstudiantesUnicosEnCursos(List<UUID> idsCurso);

    /**
     * Cuenta los estudiantes matriculados en un curso que aún no han completado ninguna lección.
     *
     * @param  idCurso identificador del curso
     * @return número de estudiantes sin progreso registrado
     */
    long contarSinIniciar(UUID idCurso);

    /**
     * Cuenta el total de matrículas activas en la plataforma.
     *
     * @return total de matrículas activas
     */
    long contarTodas();

    /**
     * Cuenta el total de matrículas con estado {@code COMPLETED} en la plataforma.
     *
     * @return total de matrículas completadas
     */
    long contarCompletadas();

    /**
     * Devuelve los {@code limite} cursos con mayor cantidad de matrículas activas.
     *
     * @param  limite número máximo de resultados
     * @return lista de cursos más populares por matrículas
     */
    List<Object[]> cursosMasPopulares(int limite);

    /**
     * Devuelve los {@code limite} cursos con mayor cantidad de matrículas completadas.
     *
     * @param  limite número máximo de resultados
     * @return lista de cursos con más finalizaciones
     */
    List<Object[]> cursosMasCompletados(int limite);
}
