package com.puj.collaboration.repository;

import com.puj.collaboration.entity.ChatMessage;
import com.puj.collaboration.entity.GroupMember;
import com.puj.collaboration.entity.StudyGroup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import jakarta.persistence.NoResultException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JPA para {@link StudyGroup}, {@link GroupMember} y {@link ChatMessage}.
 *
 * <p>Las consultas de membresía filtran por {@code deletedAt IS NULL} para obtener
 * solo miembros activos. El historial de chat se recupera en orden descendente
 * y se invierte en memoria para presentar los mensajes en orden cronológico.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class StudyGroupRepository {

    @PersistenceContext(unitName = "CollaborationServicePU")
    private EntityManager em;

    /**
     * Persiste un nuevo grupo de estudio (INSERT).
     *
     * @param g grupo a persistir
     * @return la misma instancia administrada por JPA
     */
    public StudyGroup save(StudyGroup g) { em.persist(g); return g; }

    /**
     * Busca un grupo por su identificador primario, excluyendo eliminados.
     *
     * @param id UUID del grupo
     * @return {@link Optional} con el grupo, o vacío si no existe o está eliminado
     */
    public Optional<StudyGroup> findById(UUID id) {
        return Optional.ofNullable(em.find(StudyGroup.class, id))
                .filter(g -> !g.isDeleted());
    }

    /**
     * Retorna los grupos activos de un curso.
     *
     * @param courseId UUID del curso
     * @return lista de grupos activos del curso (puede estar vacía)
     */
    public List<StudyGroup> findByCourseId(UUID courseId) {
        return em.createQuery(
                "SELECT g FROM StudyGroup g "
                + "WHERE g.courseId = :c AND g.deletedAt IS NULL",
                StudyGroup.class)
                .setParameter("c", courseId)
                .getResultList();
    }

    /**
     * Persiste una nueva membresía (INSERT).
     *
     * @param member membresía a agregar al grupo
     */
    public void addMember(GroupMember member) { em.persist(member); }

    /**
     * Actualiza una membresía existente (UPDATE/merge).
     *
     * @param m membresía a actualizar
     * @return la instancia administrada resultante del merge
     */
    public GroupMember mergeMember(GroupMember m) { return em.merge(m); }

    /**
     * Retorna los miembros activos de un grupo.
     *
     * @param groupId UUID del grupo
     * @return lista de membresías activas (puede estar vacía)
     */
    public List<GroupMember> findActiveMembers(UUID groupId) {
        return em.createQuery(
                "SELECT m FROM GroupMember m "
                + "WHERE m.group.id = :g AND m.deletedAt IS NULL",
                GroupMember.class)
                .setParameter("g", groupId).getResultList();
    }

    /**
     * Busca la membresía activa de un usuario en un grupo.
     *
     * @param groupId UUID del grupo
     * @param userId  UUID del usuario
     * @return {@link Optional} con la membresía activa, o vacío si no existe
     */
    public Optional<GroupMember> findMemberByGroupAndUser(UUID groupId, UUID userId) {
        try {
            return Optional.of(em.createQuery(
                    "SELECT m FROM GroupMember m "
                    + "WHERE m.group.id = :g "
                    + "AND m.userId = :u "
                    + "AND m.deletedAt IS NULL",
                    GroupMember.class)
                    .setParameter("g", groupId)
                    .setParameter("u", userId)
                    .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /**
     * Busca cualquier membresía (activa o eliminada) de un usuario en un grupo.
     *
     * <p>Se usa para restaurar una membresía eliminada en lugar de crear una
     * nueva y evitar violar la restricción única {@code uq_group_member}.</p>
     *
     * @param groupId UUID del grupo
     * @param userId  UUID del usuario
     * @return {@link Optional} con la membresía (activa o eliminada),
     *         o vacío si nunca perteneció al grupo
     */
    public Optional<GroupMember> findMemberByGroupAndUserAny(
            UUID groupId, UUID userId) {
        try {
            return Optional.of(em.createQuery(
                    "SELECT m FROM GroupMember m "
                    + "WHERE m.group.id = :g AND m.userId = :u",
                    GroupMember.class)
                    .setParameter("g", groupId)
                    .setParameter("u", userId)
                    .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /**
     * Verifica si un usuario es miembro activo de un grupo.
     *
     * @param groupId UUID del grupo
     * @param userId  UUID del usuario
     * @return {@code true} si existe una membresía activa del usuario en el grupo
     */
    public boolean isMember(UUID groupId, UUID userId) {
        Long count = em.createQuery(
                "SELECT COUNT(m) FROM GroupMember m "
                + "WHERE m.group.id = :g "
                + "AND m.userId = :u "
                + "AND m.deletedAt IS NULL",
                Long.class)
                .setParameter("g", groupId)
                .setParameter("u", userId)
                .getSingleResult();
        return count > 0;
    }

    /**
     * Persiste un mensaje de chat (INSERT).
     *
     * @param msg mensaje a persistir en el historial del grupo
     */
    public void saveMessage(ChatMessage msg) { em.persist(msg); }

    /**
     * Retorna los mensajes de chat más recientes de un grupo en orden cronológico.
     *
     * <p>La consulta obtiene los {@code limit} mensajes más recientes en orden
     * descendente y los invierte en memoria para retornarlos en orden ascendente.</p>
     *
     * @param groupId UUID del grupo
     * @param limit   número máximo de mensajes a retornar
     * @return lista de mensajes en orden cronológico ascendente (puede estar vacía)
     */
    public List<ChatMessage> findRecentMessages(UUID groupId, int limit) {
        List<ChatMessage> messages = em.createQuery(
                "SELECT m FROM ChatMessage m "
                + "WHERE m.groupId = :g "
                + "ORDER BY m.sentAt DESC",
                ChatMessage.class)
                .setParameter("g", groupId)
                .setMaxResults(limit)
                .getResultList();
        Collections.reverse(messages);
        return messages;
    }
}
