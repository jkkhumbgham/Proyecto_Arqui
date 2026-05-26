package com.puj.users.service;

import com.puj.events.EmailNotificationEvent;
import com.puj.events.UserRegisteredEvent;
import com.puj.events.publisher.EventPublisher;
import com.puj.security.rbac.Role;
import com.puj.users.dto.AdminCreateUserRequest;
import com.puj.users.dto.UpdateUserRequest;
import com.puj.users.dto.UserResponse;
import com.puj.users.entity.AuditLog;
import com.puj.users.entity.User;
import com.puj.users.repository.AuditLogRepository;
import com.puj.users.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.mindrot.jbcrypt.BCrypt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class UserService {

    @Inject private UserRepository     userRepo;
    @Inject private AuditLogRepository auditRepo;
    @Inject private EventPublisher     eventPublisher;

    public UserResponse findById(UUID id) {
        return userRepo.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + id));
    }

    public List<UserResponse> findAll(int page, int size) {
        return userRepo.findAll(page, size).stream()
                .map(UserResponse::from)
                .toList();
    }

    public long count() {
        return userRepo.countAll();
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest req, UUID performedBy, String ip) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + id));

        if (req.firstName() != null && !req.firstName().isBlank()) {
            user.setFirstName(req.firstName().trim());
        }
        if (req.lastName() != null && !req.lastName().isBlank()) {
            user.setLastName(req.lastName().trim());
        }
        if (req.newPassword() != null && !req.newPassword().isBlank()) {
            user.setPasswordHash(BCrypt.hashpw(req.newPassword(), BCrypt.gensalt(12)));
        }

        userRepo.save(user);
        auditRepo.save(AuditLog.of(performedBy, "USER_UPDATE",
                "/api/v1/users/" + id, ip));

        return UserResponse.from(user);
    }

    @Transactional
    public void changeRole(UUID targetId, Role newRole, UUID adminId, String ip) {
        User user = userRepo.findById(targetId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + targetId));

        user.setRole(newRole);
        userRepo.save(user);
        auditRepo.save(AuditLog.of(adminId, "ROLE_CHANGE",
                "/api/v1/users/" + targetId + "/role", ip));
    }

    @Transactional
    public UserResponse adminCreate(AdminCreateUserRequest req, UUID adminId, String ip) {
        if (userRepo.existsByEmail(req.email())) {
            throw new BadRequestException("El correo electrónico ya está registrado.");
        }

        Role role;
        try {
            role = req.role() != null ? Role.valueOf(req.role().toUpperCase()) : Role.STUDENT;
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Rol inválido: " + req.role());
        }

        User user = new User();
        user.setEmail(req.email().toLowerCase().trim());
        user.setPasswordHash(BCrypt.hashpw(req.password(), BCrypt.gensalt(12)));
        user.setFirstName(req.firstName().trim());
        user.setLastName(req.lastName().trim());
        user.setRole(role);
        user.setConsentGiven(req.consentGiven());
        if (req.consentGiven()) user.setConsentDate(Instant.now());
        userRepo.save(user);
        auditRepo.save(AuditLog.of(adminId, "ADMIN_CREATE_USER", "/api/v1/users", ip));

        // Publicar evento para que analytics cuente al nuevo usuario
        eventPublisher.publishAnalytics(new UserRegisteredEvent(
                user.getId().toString(), user.getEmail(),
                user.getFirstName(), user.getLastName(),
                user.getRole().name()
        ));
        // Notificar al nuevo usuario por correo (misma plantilla que auto-registro)
        eventPublisher.publishEmail(new EmailNotificationEvent(
                user.getEmail(), user.getFirstName(),
                EmailNotificationEvent.EmailType.WELCOME,
                Map.of("firstName", user.getFirstName())
        ));

        return UserResponse.from(user);
    }

    public List<UserResponse> findInactive(int days, int page, int size) {
        Instant threshold = Instant.now().minusSeconds((long) days * 86400);
        return userRepo.findInactive(threshold, page, size).stream()
                .map(UserResponse::from).toList();
    }

    @Transactional
    public void softDelete(UUID id, UUID adminId, String ip) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + id));

        user.softDelete();
        userRepo.save(user);
        auditRepo.save(AuditLog.of(adminId, "USER_DELETE",
                "/api/v1/users/" + id, ip));
    }
}
