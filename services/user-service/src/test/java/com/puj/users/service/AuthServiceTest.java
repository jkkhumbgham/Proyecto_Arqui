package com.puj.users.service;

import com.puj.events.publisher.EventPublisher;
import com.puj.security.blacklist.TokenBlacklistService;
import com.puj.security.jwt.JwtClaims;
import com.puj.security.jwt.JwtProvider;
import com.puj.security.rbac.Role;
import com.puj.users.dto.LoginRequest;
import com.puj.users.dto.LoginResponse;
import com.puj.users.dto.RegisterRequest;
import com.puj.users.dto.UserResponse;
import com.puj.users.entity.RefreshToken;
import com.puj.users.entity.User;
import com.puj.users.repository.RefreshTokenRepository;
import com.puj.users.repository.UserRepository;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mindrot.jbcrypt.BCrypt;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository         userRepo;
    @Mock private RefreshTokenRepository refreshRepo;
    @Mock private JwtProvider            jwtProvider;
    @Mock private TokenBlacklistService  blacklistService;
    @Mock private EventPublisher         eventPublisher;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        setField(sampleUser, "id", UUID.randomUUID());
        sampleUser.setEmail("test@puj.edu.co");
        sampleUser.setPasswordHash(BCrypt.hashpw("password123", BCrypt.gensalt(4)));
        sampleUser.setFirstName("Juan");
        sampleUser.setLastName("Pérez");
        sampleUser.setRole(Role.STUDENT);
        sampleUser.setActive(true);
        sampleUser.setConsentGiven(true);
        sampleUser.setConsentDate(Instant.now());
    }

    @Test
    void register_withConsentFalse_throwsBadRequest() {
        RegisterRequest req = new RegisterRequest(
                "new@puj.edu.co", "pass12345", "Ana", "López", false);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("consentimiento");
    }

    @Test
    void register_duplicateEmail_throwsBadRequest() {
        when(userRepo.existsByEmail(anyString())).thenReturn(true);
        RegisterRequest req = new RegisterRequest(
                "dup@puj.edu.co", "pass12345", "Ana", "López", true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("registrado");
    }

    @Test
    void register_success_persistsAndPublishesEvents() {
        when(userRepo.existsByEmail(anyString())).thenReturn(false);
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterRequest req = new RegisterRequest(
                "new@puj.edu.co", "pass12345", "Ana", "López", true);

        UserResponse resp = authService.register(req);

        assertThat(resp.email()).isEqualTo("new@puj.edu.co");
        assertThat(resp.role()).isEqualTo(Role.STUDENT);
        verify(eventPublisher, times(1)).publishAnalytics(any());
        verify(eventPublisher, times(1)).publishEmail(any());
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        when(userRepo.findByEmail("test@puj.edu.co")).thenReturn(Optional.of(sampleUser));

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("test@puj.edu.co", "wrongpass"), "127.0.0.1"))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void login_success_returnsTokens() {
        when(userRepo.findByEmail("test@puj.edu.co")).thenReturn(Optional.of(sampleUser));
        when(jwtProvider.generateAccessToken(any(), any(), any(), any())).thenReturn("access.token.here");
        when(refreshRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoginResponse resp = authService.login(
                new LoginRequest("test@puj.edu.co", "password123"), "127.0.0.1");

        assertThat(resp.accessToken()).isEqualTo("access.token.here");
        assertThat(resp.tokenType()).isEqualTo("Bearer");
        assertThat(resp.refreshToken()).isNotBlank();
    }

    @Test
    void logout_blacklistsJti() {
        JwtClaims claims = new JwtClaims("jti-123", sampleUser.getId().toString(),
                "test@puj.edu.co", "STUDENT", Instant.now(),
                Instant.now().plusSeconds(900));
        when(jwtProvider.getRemainingTtlSeconds("jti-123")).thenReturn(500L);

        authService.logout(claims);

        verify(blacklistService).blacklist("jti-123", 500L);
        verify(refreshRepo).revokeAllForUser(sampleUser.getId());
    }

    private void setField(Object target, String name, Object value) {
        try {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
