package com.puj.evaluaciones.adaptativo.dominio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida (repositorio) para la entidad {@link ReglaAdaptativa}.
 *
 * <p>Define el contrato de acceso a datos para las reglas adaptativas.
 * Las implementaciones concretas residen en la capa de infraestructura.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public interface RepositorioReglasAdaptativas {

    /**
     * Persiste o actualiza una regla adaptativa.
     *
     * @param regla regla a persistir o actualizar
     * @return la instancia administrada por JPA
     */
    ReglaAdaptativa guardar(ReglaAdaptativa regla);

    /**
     * Busca una regla por su identificador primario, excluyendo eliminadas.
     *
     * @param id UUID de la regla
     * @return {@link Optional} con la regla, o vacío si no existe o está eliminada
     */
    Optional<ReglaAdaptativa> buscarPorId(UUID id);

    /**
     * Busca la regla activa asociada a una evaluación específica.
     *
     * @param idEvaluacion UUID de la evaluación
     * @return {@link Optional} con la regla activa, o vacío si no existe
     */
    Optional<ReglaAdaptativa> buscarPorEvaluacion(UUID idEvaluacion);

    /**
     * Retorna todas las reglas activas de un curso.
     *
     * @param idCurso UUID del curso
     * @return lista de reglas activas del curso (puede estar vacía)
     */
    List<ReglaAdaptativa> buscarPorCurso(UUID idCurso);
}
