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

@ApplicationScoped
public class StudyGroupRepository {

    @PersistenceContext(unitName = "CollaborationServicePU")
    private EntityManager em;

    public StudyGroup save(StudyGroup g) { em.persist(g); return g; }

    public Optional<StudyGroup> findById(UUID id) {
        return Optional.ofNullable(em.find(StudyGroup.class, id))
                .filter(g -> !g.isDeleted());
    }

    public List<StudyGroup> findByCourseId(UUID courseId) {
        return em.createQuery(
                "SELECT g FROM StudyGroup g WHERE g.courseId = :c AND g.deletedAt IS NULL",
                StudyGroup.class)
                .setParameter("c", courseId)
                .getResultList();
    }

    public void addMember(GroupMember member) { em.persist(member); }

    public GroupMember mergeMember(GroupMember m) { return em.merge(m); }

    public List<GroupMember> findActiveMembers(UUID groupId) {
        return em.createQuery(
                "SELECT m FROM GroupMember m WHERE m.group.id = :g AND m.deletedAt IS NULL",
                GroupMember.class)
                .setParameter("g", groupId).getResultList();
    }

    public Optional<GroupMember> findMemberByGroupAndUser(UUID groupId, UUID userId) {
        try {
            return Optional.of(em.createQuery(
                    "SELECT m FROM GroupMember m WHERE m.group.id = :g AND m.userId = :u AND m.deletedAt IS NULL",
                    GroupMember.class)
                    .setParameter("g", groupId).setParameter("u", userId)
                    .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public Optional<GroupMember> findMemberByGroupAndUserAny(UUID groupId, UUID userId) {
        try {
            return Optional.of(em.createQuery(
                    "SELECT m FROM GroupMember m WHERE m.group.id = :g AND m.userId = :u",
                    GroupMember.class)
                    .setParameter("g", groupId).setParameter("u", userId)
                    .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public boolean isMember(UUID groupId, UUID userId) {
        Long count = em.createQuery(
                "SELECT COUNT(m) FROM GroupMember m WHERE m.group.id = :g AND m.userId = :u AND m.deletedAt IS NULL",
                Long.class)
                .setParameter("g", groupId).setParameter("u", userId)
                .getSingleResult();
        return count > 0;
    }

    public void saveMessage(ChatMessage msg) { em.persist(msg); }

    public List<ChatMessage> findRecentMessages(UUID groupId, int limit) {
        List<ChatMessage> messages = em.createQuery(
                "SELECT m FROM ChatMessage m WHERE m.groupId = :g ORDER BY m.sentAt DESC",
                ChatMessage.class)
                .setParameter("g", groupId)
                .setMaxResults(limit)
                .getResultList();
        Collections.reverse(messages);
        return messages;
    }
}
