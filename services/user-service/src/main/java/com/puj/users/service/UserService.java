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

/**
 * Servicio de gestión de usuarios que expone operaciones de consulta,
 * actualización, creación administrativa y eliminación lógica.
 *
 * <p>Las operaciones de escritura generan entradas de auditoría en la misma
 * transacción mediante {@link AuditLogRepository} directamente, sin delegar en
 * {@link AuditService} (que usa {@code REQUIRES_NEW}) para mantener la
 * atomicidad con la operación principal.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@ApplicationScoped
public class UserService {

    @Inject private UserRepository     userRepo;
    @Inject private AuditLogRepository auditRepo;
    @Inject private EventPublisher     eventPublisher;

    // =========================================================================
    // Public API — queries
    // =========================================================================

    /**
     * Busca un usuario activo por su identificador.
     *
     * @param id UUID del usuario a buscar.
     * @return representación pública del usuario.
     * @throws NotFoundException si no existe un usuario activo con ese ID.
     */
    public UserResponse findById(UUID id) {
        return userRepo.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() ->
                        new NotFoundException("Usuario no encontrado: " + id));
    }

    /**
     * Devuelve una página de usuarios activos ordenados por fecha de creación.
     *
     * @param page número de página (basado en cero).
     * @param size número máximo de resultados por página.
     * @return lista de representaciones públicas de usuarios.
     */
    public List<UserResponse> findAll(int page, int size) {
        return userRepo.findAll(page, size).stream()
                .map(UserResponse::from)
                .toList();
    }

    /**
     * Cuenta el total de usuarios activos en la plataforma.
     *
     * @return número total de usuarios activos.
     */
    public long count() {
        return userRepo.countAll();
    }

    /**
     * Devuelve una página de usuarios que no han iniciado sesión en los últimos
     * {@code days} días o que nunca han iniciado sesión.
     *
     * @param days número de días de inactividad para considerar un usuario inactivo.
     * @param page número de página (basado en cero).
     * @param size número máximo de resultados por página.
     * @return lista de usuarios inactivos.
     */
    public List<UserResponse> findInactive(int days, int page, int size) {
        Instant threshold = Instant.now().minusSeconds((long) days * 86400L);
        return userRepo.findInactive(threshold, page, size).stream()
                .map(UserResponse::from)
                .toList();
    }

    // =========================================================================
    // Public API — mutations
    // =========================================================================

    /**
     * Actualiza los datos personales y/o la contraseña de un usuario.
     *
     * <p>Los campos nulos o en blanco en {@code req} son ignorados.
     *
     * @param id          UUID del usuario a actualizar.
     * @param req         datos a actualizar; los campos nulos se omiten.
     * @param performedBy UUID del actor que realiza la operación (para auditoría).
     * @param ip          dirección IP del cliente (para auditoría).
     * @return representación pública del usuario actualizado.
     * @throws NotFoundException si no existe un usuario activo con ese ID.
     */
    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest req,
                               UUID performedBy, String ip) {
        User user = requireExistingUser(id);
        applyProfileChanges(user, req);
        userRepo.save(user);
        auditRepo.save(AuditLog.of(performedBy, "USER_UPDATE",
                "/api/v1/users/" + id, ip));
        return UserResponse.from(user);
    }

    /**
     * Cambia el rol de seguridad de un usuario.
     *
     * @param targetId UUID del usuario cuyo rol se cambia.
     * @param newRole  nuevo rol RBAC a asignar.
     * @param adminId  UUID del administrador que realiza el cambio (para auditoría).
     * @param ip       dirección IP del cliente (para auditoría).
     * @throws NotFoundException si no existe un usuario activo con ese ID.
     */
    @Transactional
    public void changeRole(UUID targetId, Role newRole, UUID adminId, String ip) {
        User user = requireExistingUser(targetId);
        user.setRole(newRole);
        userRepo.save(user);
        auditRepo.save(AuditLog.of(adminId, "ROLE_CHANGE",
                "/api/v1/users/" + targetId + "/role", ip));
    }

    /**
     * Crea un nuevo usuario con rol configurable desde el panel de administración.
     *
     * <p>Al finalizar publica un evento de analítica y envía el correo de
     * bienvenida al nuevo usuario.
     *
     * @param req     datos del nuevo usuario incluyendo rol opcional.
     * @param adminId UUID del administrador que crea el usuario (para auditoría).
     * @param ip      dirección IP del cliente (para auditoría).
     * @return representación pública del usuario creado.
     * @throws BadRequestException si el correo ya está registrado o el rol es
     *                             inválido.
     */
    @Transactional
    public UserResponse adminCreate(AdminCreateUserRequest req,
                                    UUID adminId, String ip) {
        validateAdminCreatePreconditions(req);
        Role role = resolveRole(req.role());
        User user = buildAdminUser(req, role);
        userRepo.save(user);
        auditRepo.save(AuditLog.of(adminId, "ADMIN_CREATE_USER",
                "/api/v1/users", ip));
        publishAdminCreateEvents(user);
        return UserResponse.from(user);
    }

    /**
     * Realiza el borrado lógico de un usuario desactivando su cuenta.
     *
     * @param id      UUID del usuario a eliminar.
     * @param adminId UUID del administrador que solicita la eliminación.
     * @param ip      dirección IP del cliente (para auditoría).
     * @throws NotFoundException si no existe un usuario activo con ese ID.
     */
    @Transactional
    public void softDelete(UUID id, UUID adminId, String ip) {
        User user = requireExistingUser(id);
        user.softDelete();
        userRepo.save(user);
        auditRepo.save(AuditLog.of(adminId, "USER_DELETE",
                "/api/v1/users/" + id, ip));
    }

    // =========================================================================
    // Private helpers — update
    // =========================================================================

    /**
     * Aplica los cambios de perfil no nulos del request sobre la entidad usuario.
     *
     * @param user entidad a modificar.
     * @param req  datos opcionales de actualización.
     */
    private void applyProfileChanges(User user, UpdateUserRequest req) {
        if (req.firstName() != null && !req.firstName().isBlank()) {
            user.setFirstName(req.firstName().trim());
        }
        if (req.lastName() != null && !req.lastName().isBlank()) {
            user.setLastName(req.lastName().trim());
        }
        if (req.newPassword() != null && !req.newPassword().isBlank()) {
            user.setPasswordHash(BCrypt.hashpw(req.newPassword(), BCrypt.gensalt(12)));
        }
    }

    // =========================================================================
    // Private helpers — adminCreate
    // =========================================================================

    /**
     * Valida que el correo no esté duplicado antes de crear el usuario.
     *
     * @param req datos de la solicitud de creación administrativa.
     * @throws BadRequestException si el correo ya está registrado.
     */
    private void validateAdminCreatePreconditions(AdminCreateUserRequest req) {
        if (userRepo.existsByEmail(req.email())) {
            throw new BadRequestException("El correo electrónico ya está registrado.");
        }
    }

    /**
     * Resuelve el rol a partir del nombre de cadena proporcionado.
     *
     * @param roleName nombre del rol en cualquier combinación de mayúsculas/minúsculas;
     *                 si es {@code null} se usa {@code STUDENT}.
     * @return rol RBAC resuelto.
     * @throws BadRequestException si el nombre no corresponde a ningún rol válido.
     */
    private Role resolveRole(String roleName) {
        try {
            return roleName != null
                    ? Role.valueOf(roleName.toUpperCase())
                    : Role.STUDENT;
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Rol inválido: " + roleName);
        }
    }

    /**
     * Construye la entidad {@link User} con los datos de la solicitud administrativa.
     *
     * @param req  datos del formulario de creación administrativa.
     * @param role rol RBAC ya resuelto y validado.
     * @return nueva entidad de usuario sin persistir.
     */
    private User buildAdminUser(AdminCreateUserRequest req, Role role) {
        User user = new User();
        user.setEmail(req.email().toLowerCase().trim());
        user.setPasswordHash(BCrypt.hashpw(req.password(), BCrypt.gensalt(12)));
        user.setFirstName(req.firstName().trim());
        user.setLastName(req.lastName().trim());
        user.setRole(role);
        user.setConsentGiven(req.consentGiven());
        if (req.consentGiven()) {
            user.setConsentDate(Instant.now());
        }
        return user;
    }

    /**
     * Publica los eventos de analítica y correo de bienvenida tras la creación
     * administrativa de un usuario.
     *
     * @param user usuario recién persistido.
     */
    private void publishAdminCreateEvents(User user) {
        eventPublisher.publishAnalytics(new UserRegisteredEvent(
                user.getId().toString(), user.getEmail(),
                user.getFirstName(), user.getLastName(),
                user.getRole().name()));

        eventPublisher.publishEmail(new EmailNotificationEvent(
                user.getEmail(), user.getFirstName(),
                EmailNotificationEvent.EmailType.WELCOME,
                Map.of("firstName", user.getFirstName())));
    }

    // =========================================================================
    // Private helpers — shared
    // =========================================================================

    /**
     * Localiza un usuario activo por ID o lanza excepción si no existe.
     *
     * @param id UUID del usuario a buscar.
     * @return entidad {@link User} activa.
     * @throws NotFoundException si no existe un usuario activo con ese ID.
     */
    private User requireExistingUser(UUID id) {
        return userRepo.findById(id)
                .orElseThrow(() ->
                        new NotFoundException("Usuario no encontrado: " + id));
    }
}
