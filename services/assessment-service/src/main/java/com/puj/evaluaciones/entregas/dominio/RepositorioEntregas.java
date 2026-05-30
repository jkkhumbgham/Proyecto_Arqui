package com.puj.evaluaciones.entregas.dominio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida (repositorio) para la entidad {@link Entrega}.
 *
 * <p>Define el contrato de acceso a datos para las entregas de evaluaciones.
 * Las implementaciones concretas residen en la capa de infraestructura.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public interface RepositorioEntregas {

    /**
     * Persiste una nueva entrega (INSERT).
     *
     * @param entrega nueva entrega a persistir
     * @return la misma instancia administrada por JPA
     */
    Entrega guardar(Entrega entrega);

    /**
     * Actualiza una entrega existente (UPDATE/merge).
     *
     * @param entrega entrega a actualizar
     * @return la instancia administrada resultante del merge
     */
    Entrega actualizar(Entrega entrega);

    /**
     * Busca una entrega por su identificador primario.
     *
     * @param id UUID de la entrega
     * @return {@link Optional} con la entrega, o vacío si no existe
     */
    Optional<Entrega> buscarPorId(UUID id);

    /**
     * Retorna todos los intentos de un estudiante en una evaluación.
     *
     * @param idUsuario    UUID del estudiante
     * @param idEvaluacion UUID de la evaluación
     * @return lista de entregas del estudiante en la evaluación (puede estar vacía)
     */
    List<Entrega> buscarPorUsuarioYEvaluacion(UUID idUsuario, UUID idEvaluacion);

    /**
     * Retorna los intentos de un estudiante paginados.
     *
     * @param idUsuario UUID del estudiante
     * @param pagina    número de página (base 0)
     * @param tamano    tamaño de página
     * @return lista paginada de entregas del estudiante
     */
    List<Entrega> buscarPorUsuario(UUID idUsuario, int pagina, int tamano);

    /**
     * Retorna los intentos completados o calificados de una evaluación.
     *
     * @param idEvaluacion UUID de la evaluación
     * @return lista de entregas en estado SUBMITTED o GRADED
     */
    List<Entrega> buscarPorEvaluacion(UUID idEvaluacion);

    /**
     * Retorna los intentos completados de un estudiante, paginados.
     *
     * @param idUsuario UUID del estudiante
     * @param pagina    número de página (base 0)
     * @param tamano    tamaño de página
     * @return lista paginada de entregas en estado SUBMITTED o GRADED
     */
    List<Entrega> buscarCompletadasPorUsuario(UUID idUsuario, int pagina, int tamano);

    /**
     * Retorna los intentos completados de un estudiante en un conjunto de evaluaciones.
     *
     * @param idUsuario     UUID del estudiante
     * @param idEvaluaciones lista de UUIDs de las evaluaciones a consultar
     * @return lista de entregas completadas en las evaluaciones indicadas
     */
    List<Entrega> buscarPorUsuarioYEvaluaciones(UUID idUsuario, List<UUID> idEvaluaciones);
}
