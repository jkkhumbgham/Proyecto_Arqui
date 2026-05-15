package com.puj.users.service;

import com.puj.events.EmailNotificationEvent;
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

@ApplicationScoped
public class AuthService {

    private static final long ACCESS_TTL_SECONDS  =
            Long.parseLong(System.getenv().getOrDefault("JWT_ACCESS_TTL_SECONDS",  "900"));
    private static final long REFRESH_TTL_SECONDS =
            Long.parseLong(System.getenv().getOrDefault("JWT_REFRESH_TTL_SECONDS", "604800"));

    @Inject private UserRepository         userRepo;
    @Inject private RefreshTokenRepository refreshRepo;
    @Inject private JwtProvider            jwtProvider;
    @Inject private TokenBlacklistService  blacklistService;
    @Inject private EventPublisher         eventPublisher;

    @Transactional
    public UserResponse register(RegisterRequest req) {
        if (!Boolean.TRUE.equals(req.consentGiven())) {
            throw new BadRequestException("Se requiere consentimiento de tratamiento de datos (Ley 1581/2012).");
        }
        if (userRepo.existsByEmail(req.email())) {
            throw new BadRequestException("El correo electrónico ya está registrado.");
        }

        User user = new User();
        user.setEmail(req.email().toLowerCase().trim());
        user.setPasswordHash(BCrypt.hashpw(req.password(), BCrypt.gensalt(12)));
        user.setFirstName(req.firstName().trim());
        user.setLastName(req.lastName().trim());
        user.setRole(com.puj.security.rbac.Role.STUDENT);
        user.setConsentGiven(true);
        user.setConsentDate(Instant.now());
        userRepo.save(user);

        UserRegisteredEvent event = new UserRegisteredEvent(
                user.getId().toString(), user.getEmail(),
                user.getFirstName(), user.getLastName(),
                user.getRole().name()
        );
        eventPublisher.publishAnalytics(event);
        eventPublisher.publishEmail(new EmailNotificationEvent(
                user.getEmail(), user.getFirstName(),
                EmailNotificationEvent.EmailType.WELCOME,
                Map.of("firstName", user.getFirstName())
        ));

        return UserResponse.from(user);
    }

    @Transactional
    public LoginResponse login(LoginRequest req, String ipAddress) {
        User user = userRepo.findByEmail(req.email())
                .orElseThrow(() -> new NotAuthorizedException("Credenciales inválidas."));

        if (!user.isActive() || user.isDeleted()) {
            throw new NotAuthorizedException("Cuenta inactiva o eliminada.");
        }
        if (user.isLocked()) {
            throw new NotAuthorizedException("Cuenta bloqueada por intentos fallidos. Intenta en 15 minutos.");
        }
        if (!BCrypt.checkpw(req.password(), user.getPasswordHash())) {
            user.recordFailedLogin();
            userRepo.save(user);
            throw new NotAuthorizedException("Credenciales inválidas.");
        }

        user.recordSuccessfulLogin();
        String accessToken  = jwtProvider.generateAccessToken(
                user.getId().toString(), user.getEmail(), user.getRole().name(),
                Duration.ofSeconds(ACCESS_TTL_SECONDS));

        String rawRefresh   = UUID.randomUUID().toString();
        RefreshToken refresh = new RefreshToken();
        refresh.setUser(user);
        refresh.setTokenHash(BCrypt.hashpw(rawRefresh, BCrypt.gensalt(10)));
        refresh.setExpiresAt(Instant.now().plusSeconds(REFRESH_TTL_SECONDS));
        refreshRepo.save(refresh);

        userRepo.save(user);

        String refreshTokenValue = refresh.getId() + ":" + rawRefresh;

        return LoginResponse.of(accessToken, refreshTokenValue,
                ACCESS_TTL_SECONDS, UserResponse.from(user));
    }

    @Transactional
    public LoginResponse refreshAccessToken(String refreshTokenValue) {
        String[] parts = refreshTokenValue.split(":", 2);
        if (parts.length != 2) throw new NotAuthorizedException("Refresh token inválido.");

        UUID tokenId = UUID.fromString(parts[0]);
        String raw   = parts[1];

        RefreshToken stored = refreshRepo.findById(tokenId)
                .orElseThrow(() -> new NotAuthorizedException("Refresh token no encontrado."));

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

    @Transactional
    public void logout(JwtClaims claims) {
        long ttl = jwtProvider.getRemainingTtlSeconds(claims.jti());
        blacklistService.blacklist(claims.jti(), ttl);
        refreshRepo.revokeAllForUser(UUID.fromString(claims.userId()));
    }
}
