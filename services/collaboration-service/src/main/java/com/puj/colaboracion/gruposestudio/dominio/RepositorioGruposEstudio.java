package com.puj.colaboracion.gruposestudio.dominio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato del repositorio de grupos de estudio, miembros y mensajes de chat.
 *
 * <p>Define las operaciones de persistencia y consulta necesarias para el
 * dominio de grupos de estudio, desacoplando la lógica de negocio de la
 * implementación JPA concreta
 * ({@link com.puj.colaboracion.gruposestudio.infraestructura.RepositorioGruposEstudioJpa}).</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public interface RepositorioGruposEstudio {

    /**
     * Persiste un nuevo grupo de estudio (INSERT).
     *
     * @param grupo grupo a persistir
     * @return la misma instancia administrada por JPA
     */
    GrupoEstudio guardar(GrupoEstudio grupo);

    /**
     * Busca un grupo por su identificador primario, excluyendo eliminados.
     *
     * @param id UUID del grupo
     * @return {@link Optional} con el grupo, o vacío si no existe o está eliminado
     */
    Optional<GrupoEstudio> buscarPorId(UUID id);

    /**
     * Retorna los grupos activos de un curso.
     *
     * @param idCurso UUID del curso
     * @return lista de grupos activos del curso (puede estar vacía)
     */
    List<GrupoEstudio> buscarPorIdCurso(UUID idCurso);

    /**
     * Persiste una nueva membresía (INSERT).
     *
     * @param miembro membresía a agregar al grupo
     */
    void agregarMiembro(MiembroGrupo miembro);

    /**
     * Actualiza una membresía existente (UPDATE/merge).
     *
     * @param miembro membresía a actualizar
     * @return la instancia administrada resultante del merge
     */
    MiembroGrupo fusionarMiembro(MiembroGrupo miembro);

    /**
     * Retorna los miembros activos de un grupo.
     *
     * @param idGrupo UUID del grupo
     * @return lista de membresías activas (puede estar vacía)
     */
    List<MiembroGrupo> buscarMiembrosActivos(UUID idGrupo);

    /**
     * Busca la membresía activa de un usuario en un grupo.
     *
     * @param idGrupo   UUID del grupo
     * @param idUsuario UUID del usuario
     * @return {@link Optional} con la membresía activa, o vacío si no existe
     */
    Optional<MiembroGrupo> buscarMiembroPorGrupoYUsuario(UUID idGrupo, UUID idUsuario);

    /**
     * Busca cualquier membresía (activa o eliminada) de un usuario en un grupo.
     *
     * @param idGrupo   UUID del grupo
     * @param idUsuario UUID del usuario
     * @return {@link Optional} con la membresía (activa o eliminada),
     *         o vacío si nunca perteneció al grupo
     */
    Optional<MiembroGrupo> buscarMiembroPorGrupoYUsuarioCualquiera(
            UUID idGrupo, UUID idUsuario);

    /**
     * Verifica si un usuario es miembro activo de un grupo.
     *
     * @param idGrupo   UUID del grupo
     * @param idUsuario UUID del usuario
     * @return {@code true} si existe una membresía activa del usuario en el grupo
     */
    boolean esMiembro(UUID idGrupo, UUID idUsuario);

    /**
     * Persiste un mensaje de chat (INSERT).
     *
     * @param mensaje mensaje a persistir en el historial del grupo
     */
    void guardarMensaje(MensajeChat mensaje);

    /**
     * Retorna los mensajes de chat más recientes de un grupo en orden cronológico.
     *
     * @param idGrupo UUID del grupo
     * @param limite  número máximo de mensajes a retornar
     * @return lista de mensajes en orden cronológico ascendente (puede estar vacía)
     */
    List<MensajeChat> buscarMensajesRecientes(UUID idGrupo, int limite);
}
