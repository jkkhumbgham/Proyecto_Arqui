package com.puj.evaluaciones.evaluaciones.dominio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida (repositorio) para la entidad {@link Evaluacion}.
 *
 * <p>Define el contrato de acceso a datos para evaluaciones. Las implementaciones
 * concretas residen en la capa de infraestructura.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public interface RepositorioEvaluaciones {

    /**
     * Persiste o actualiza una evaluación.
     *
     * @param evaluacion evaluación a persistir o actualizar
     * @return la instancia administrada por JPA
     */
    Evaluacion guardar(Evaluacion evaluacion);

    /**
     * Busca una evaluación por su identificador primario, excluyendo eliminadas.
     *
     * @param id UUID de la evaluación
     * @return {@link Optional} con la evaluación, o vacío si no existe o está eliminada
     */
    Optional<Evaluacion> buscarPorId(UUID id);

    /**
     * Retorna todas las evaluaciones activas ordenadas por fecha de creación.
     *
     * @return lista de evaluaciones activas (puede estar vacía)
     */
    List<Evaluacion> buscarTodas(int pagina, int cantidad);

    /**
     * Retorna las evaluaciones activas de un curso ordenadas por fecha de creación.
     *
     * @param idCurso UUID del curso
     * @param pagina  número de página (base 0)
     * @param cantidad máximo de resultados
     * @return lista de evaluaciones del curso (puede estar vacía)
     */
    List<Evaluacion> buscarPorCurso(UUID idCurso, int pagina, int cantidad);

    /**
     * Retorna las evaluaciones activas creadas por un instructor.
     *
     * @param idInstructor UUID del instructor
     * @param pagina  número de página (base 0)
     * @param cantidad máximo de resultados
     * @return lista de evaluaciones del instructor (puede estar vacía)
     */
    List<Evaluacion> buscarPorInstructor(UUID idInstructor, int pagina, int cantidad);

    /**
     * Cuenta los intentos completados o calificados de un estudiante en una evaluación.
     *
     * @param idUsuario    UUID del estudiante
     * @param idEvaluacion UUID de la evaluación
     * @return número de intentos en estado SUBMITTED o GRADED
     */
    long contarIntentos(UUID idUsuario, UUID idEvaluacion);
}
