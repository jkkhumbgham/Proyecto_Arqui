package com.puj.collaboration.service;

import com.puj.collaboration.entity.Notification;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class NotificationService {

    @PersistenceContext(unitName = "CollaborationServicePU")
    private EntityManager em;

    @Transactional
    public void persist(UUID userId, String type, String title, String body, String referenceId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setReferenceId(referenceId);
        em.persist(n);
    }
}
