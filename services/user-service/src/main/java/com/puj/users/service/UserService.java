package com.puj.users.service;

import com.puj.security.rbac.Role;
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

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class UserService {

    @Inject private UserRepository    userRepo;
    @Inject private AuditLogRepository auditRepo;

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
    public void softDelete(UUID id, UUID adminId, String ip) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + id));

        user.softDelete();
        userRepo.save(user);
        auditRepo.save(AuditLog.of(adminId, "USER_DELETE",
                "/api/v1/users/" + id, ip));
    }
}
