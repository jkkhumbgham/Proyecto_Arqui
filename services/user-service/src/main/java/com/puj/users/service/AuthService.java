package com.puj.users.service;

import com.puj.events.EmailNotificationEvent;
import com.puj.events.UserLoggedInEvent;
import com.puj.events.UserRegisteredEvent;
import com.puj.events.publisher.EventPublisher;
import com.puj.security.blacklist.TokenBlacklistService;
import com.puj.security.jwt.JwtClaims;
import com.puj.security.jwt.JwtProvider;
import com.puj.users.dto.LoginRequest;
import com.puj.users.dto.LoginResponse;
import com.puj.users.dto.RegisterRequest;
import com.puj.users.dto.UserResponse;
import com.puj.users.entity.RefreshToken;
import com.puj.users.entity.User;
import com.puj.users.repository.RefreshTokenRepository;
import com.puj.users.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;
import org.mindrot.jbcrypt.BCrypt;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de autenticación que gestiona el ciclo de vida de las sesiones de
 * usuario: registro, inicio de sesión, renovación de tokens y cierre de sesión.
 *
 * <p>Los tiempos de vida de los tokens se leen de variables de entorno al
 * arrancar la aplicación:
 * <ul>
 *   <li>{@code JWT_ACCESS_TTL_SECONDS}  — duración del access token (por defecto 900 s).</li>
 *   <li>{@code JWT_REFRESH_TTL_SECONDS} — duración del refresh token (por defecto 604 800 s).</li>
 * </ul>
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@ApplicationScoped
public class AuthService {

    /** Tiempo de vida del access token en segundos (por defecto 15 minutos). */
    private static final long ACCESS_TTL_SECONDS =
            Long.parseLong(System.getenv()
                    .getOrDefault("JWT_ACCESS_TTL_SECONDS", "900"));

    /** Tiempo de vida del refresh token en segundos (por defecto 7 días). */
    private static final long REFRESH_TTL_SECONDS =
            Long.parseLong(System.getenv()
                    .getOrDefault("JWT_REFRESH_TTL_SECONDS", "604800"));

    @Inject private UserRepository         userRepo;
    @Inject private RefreshTokenRepository refreshRepo;
    @Inject private JwtProvider            jwtProvider;
    @Inject private TokenBlacklistService  blacklistService;
    @Inject private EventPublisher         eventPublisher;
    @Inject private AuditService           auditService;

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Registra un nuevo usuario con rol {@code STUDENT} y publica los eventos
     * de analítica y bienvenida por correo.
     *
     * @param req datos del nuevo usuario incluyendo consentimiento de datos.
     * @return representación pública del usuario recién creado.
     * @throws BadRequestException si el consentimiento no fue otorgado o si el
     *                             correo electrónico ya está registrado.
     */
    @Transactional
    public UserResponse register(RegisterRequest req) {
        validateRegistrationPreconditions(req);
        User user = buildNewUser(req);
        userRepo.save(user);
        publishRegistrationEvents(user);
        return UserResponse.from(user);
    }

    /**
     * Autentica al usuario con sus credenciales y emite un par de tokens.
     *
     * <p>El método verifica en orden: existencia del usuario, estado activo,
     * bloqueo temporal y contraseña. Cualquier fallo incrementa el contador de
     * intentos y genera una entrada de auditoría sin revelar si la cuenta existe.
     *
     * @param req       credenciales de inicio de sesión.
     * @param ipAddress dirección IP del cliente para auditoría.
     * @return respuesta con access token, refresh token y datos del usuario.
     * @throws NotAuthorizedException si las credenciales son inválidas o la
     *                                cuenta está bloqueada o inactiva.
     */
    @Transactional
    public LoginResponse login(LoginRequest req, String ipAddress) {
        User user = resolveActiveUser(req, ipAddress);
        verifyPassword(req, user, ipAddress);
        user.recordSuccessfulLogin();
        String accessToken     = issueAccessToken(user);
        String[] refreshIdRaw  = createRefreshToken(user);
        userRepo.save(user);
        auditService.log(user.getId(), "LOGIN_SUCCESS", user.getEmail(), ipAddress);
        publishLoginEvent(user);
        return buildLoginResponse(accessToken, refreshIdRaw, user);
    }

    /**
     * Renueva el access token a partir de un refresh token válido.
     *
     * <p>El refresh token se recibe como una cadena compuesta por el UUID del
     * registro y el valor aleatorio separados por dos puntos
     * ({@code "<uuid>:<raw>"}).
     *
     * @param refreshTokenValue token de renovación en formato {@code "<uuid>:<raw>"}.
     * @return nueva respuesta con el access token renovado.
     * @throws NotAuthorizedException si el formato, el identificador o el hash
     *                                del refresh token no son válidos.
     */
    @Transactional
    public LoginResponse refreshAccessToken(String refreshTokenValue) {
        String[] parts = refreshTokenValue.split(":", 2);
        if (parts.length != 2) {
            throw new NotAuthorizedException("Refresh token inválido.");
        }

        UUID tokenId = UUID.fromString(parts[0]);
        String raw   = parts[1];

        RefreshToken stored = refreshRepo.findById(tokenId)
                .orElseThrow(() ->
                        new NotAuthorizedException("Refresh token no encontrado."));

        if (!stored.isValid() || !BCrypt.checkpw(raw, stored.getTokenHash())) {
            throw new NotAuthorizedException("Refresh token inválido o expirado.");
        }

        User user = stored.getUser();
        String newAccess = jwtProvider.generateAccessToken(
                user.getId().toString(), user.getEmail(), user.getRole().name(),
                Duration.ofSeconds(ACCESS_TTL_SECONDS));

        return LoginResponse.of(newAccess, refreshTokenValue,
                ACCESS_TTL_SECONDS, UserResponse.from(user));
    }

    /**
     * Cierra la sesión del usuario añadiendo el JTI del access token a la lista
     * negra y revocando todos sus refresh tokens activos.
     *
     * @param claims    claims del JWT del usuario que solicita el cierre de sesión.
     * @param ipAddress dirección IP del cliente para auditoría.
     */
    @Transactional
    public void logout(JwtClaims claims, String ipAddress) {
        long ttl = jwtProvider.getRemainingTtlSeconds(claims.jti());
        blacklistService.blacklist(claims.jti(), ttl);
        refreshRepo.revokeAllForUser(UUID.fromString(claims.userId()));
        auditService.log(
                UUID.fromString(claims.userId()), "LOGOUT",
                claims.email(), ipAddress);
    }

    // =========================================================================
    // Private helpers — register
    // =========================================================================

    /**
     * Valida las precondiciones del registro: consentimiento y unicidad del correo.
     *
     * @param req datos del formulario de registro.
     * @throws BadRequestException si el consentimiento no fue otorgado o el
     *                             correo ya está registrado.
     */
    private void validateRegistrationPreconditions(RegisterRequest req) {
        if (!Boolean.TRUE.equals(req.consentGiven())) {
            throw new BadRequestException(
                    "Se requiere consentimiento de tratamiento de datos (Ley 1581/2012).");
        }
        if (userRepo.existsByEmail(req.email())) {
            throw new BadRequestException("El correo electrónico ya está registrado.");
        }
    }

    /**
     * Construye y devuelve una entidad {@link User} con los datos del registro.
     *
     * @param req datos del formulario de registro validados.
     * @return nueva entidad de usuario sin persistir.
     */
    private User buildNewUser(RegisterRequest req) {
        User user = new User();
        user.setEmail(req.email().toLowerCase().trim());
        user.setPasswordHash(BCrypt.hashpw(req.password(), BCrypt.gensalt(12)));
        user.setFirstName(req.firstName().trim());
        user.setLastName(req.lastName().trim());
        user.setRole(com.puj.security.rbac.Role.STUDENT);
        user.setConsentGiven(true);
        user.setConsentDate(Instant.now());
        return user;
    }

    /**
     * Publica los eventos de analítica y correo de bienvenida tras el registro.
     *
     * @param user usuario recién persistido.
     */
    private void publishRegistrationEvents(User user) {
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
    // Private helpers — login
    // =========================================================================

    /**
     * Localiza el usuario por correo y verifica que la cuenta esté activa y
     * desbloqueada. Registra auditoría en caso de fallo.
     *
     * @param req       credenciales de la solicitud de login.
     * @param ipAddress IP del cliente para auditoría.
     * @return entidad {@link User} lista para verificación de contraseña.
     * @throws NotAuthorizedException si el usuario no existe, está inactivo
     *                                o está bloqueado.
     */
    private User resolveActiveUser(LoginRequest req, String ipAddress) {
        User user = userRepo.findByEmail(req.email()).orElse(null);
        if (user == null) {
            auditService.log(null, "LOGIN_FAILED", req.email(), ipAddress);
            throw new NotAuthorizedException("Credenciales inválidas.");
        }
        if (!user.isActive() || user.isDeleted()) {
            auditService.log(user.getId(), "LOGIN_BLOCKED", user.getEmail(), ipAddress);
            throw new NotAuthorizedException("Cuenta inactiva o eliminada.");
        }
        if (user.isLocked()) {
            auditService.log(user.getId(), "LOGIN_BLOCKED", user.getEmail(), ipAddress);
            throw new NotAuthorizedException(
                    "Cuenta bloqueada por intentos fallidos. Intenta en 15 minutos.");
        }
        return user;
    }

    /**
     * Verifica la contraseña contra el hash almacenado; incrementa el contador
     * de fallos y registra auditoría si la contraseña no coincide.
     *
     * @param req       credenciales de la solicitud de login.
     * @param user      entidad del usuario cuya contraseña se verifica.
     * @param ipAddress IP del cliente para auditoría.
     * @throws NotAuthorizedException si la contraseña no coincide.
     */
    private void verifyPassword(LoginRequest req, User user, String ipAddress) {
        if (!BCrypt.checkpw(req.password(), user.getPasswordHash())) {
            user.recordFailedLogin();
            userRepo.save(user);
            auditService.log(user.getId(), "LOGIN_FAILED", user.getEmail(), ipAddress);
            throw new NotAuthorizedException("Credenciales inválidas.");
        }
    }

    /**
     * Genera un nuevo access token JWT para el usuario.
     *
     * @param user usuario autenticado.
     * @return cadena JWT firmada.
     */
    private String issueAccessToken(User user) {
        return jwtProvider.generateAccessToken(
                user.getId().toString(), user.getEmail(), user.getRole().name(),
                Duration.ofSeconds(ACCESS_TTL_SECONDS));
    }

    /**
     * Crea, hashea y persiste un nuevo refresh token para el usuario.
     *
     * <p>El valor bruto (raw) del token se devuelve dentro del arreglo
     * {@code [0] = tokenId, [1] = raw} para que el llamador pueda construir
     * el valor compuesto que se envía al cliente, sin alterar la entidad
     * persistida.
     *
     * @param user usuario autenticado al que se le emite el token.
     * @return arreglo de dos elementos: {@code [0]} UUID del token como cadena,
     *         {@code [1]} valor raw del token para incluir en la respuesta.
     */
    private String[] createRefreshToken(User user) {
        String raw = UUID.randomUUID().toString();
        RefreshToken refresh = new RefreshToken();
        refresh.setUser(user);
        refresh.setTokenHash(BCrypt.hashpw(raw, BCrypt.gensalt(10)));
        refresh.setExpiresAt(Instant.now().plusSeconds(REFRESH_TTL_SECONDS));
        refreshRepo.save(refresh);
        return new String[]{ refresh.getId().toString(), raw };
    }

    /**
     * Construye el {@link LoginResponse} combinando el access token, el refresh
     * token emitidos y los datos del usuario.
     *
     * @param accessToken    cadena JWT de acceso.
     * @param refreshIdAndRaw arreglo {@code [uuid, raw]} devuelto por
     *                       {@link #createRefreshToken(User)}.
     * @param user           usuario autenticado cuyos datos públicos se incluyen.
     * @return respuesta completa de login lista para serializar.
     */
    private LoginResponse buildLoginResponse(String accessToken,
                                             String[] refreshIdAndRaw,
                                             User user) {
        String refreshTokenValue = refreshIdAndRaw[0] + ":" + refreshIdAndRaw[1];
        return LoginResponse.of(accessToken, refreshTokenValue,
                ACCESS_TTL_SECONDS, UserResponse.from(user));
    }

    /**
     * Publica el evento de analítica que indica que el usuario inició sesión.
     *
     * @param user usuario que completó el inicio de sesión.
     */
    private void publishLoginEvent(User user) {
        eventPublisher.publishAnalytics(new UserLoggedInEvent(
                user.getId().toString(), user.getEmail(), user.getRole().name()));
    }
}
