package com.puj.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class JwtProviderTest {

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        KeyProvider keyProvider = new KeyProvider();
        setField(keyProvider, "privateKey", kp.getPrivate());
        setField(keyProvider, "publicKey",  kp.getPublic());

        jwtProvider = new JwtProvider();
        setField(jwtProvider, "keyProvider", keyProvider);
    }

    @Test
    void generateAndValidate_roundTrip() {
        String token = jwtProvider.generateAccessToken("user-1", "test@puj.edu.co", "STUDENT",
                Duration.ofMinutes(15));

        JwtClaims claims = jwtProvider.validateToken(token);

        assertThat(claims.userId()).isEqualTo("user-1");
        assertThat(claims.email()).isEqualTo("test@puj.edu.co");
        assertThat(claims.role()).isEqualTo("STUDENT");
        assertThat(claims.jti()).isNotBlank();
    }

    @Test
    void validateToken_expired_throwsException() throws Exception {
        String token = jwtProvider.generateAccessToken("u", "e@e.com", "ADMIN",
                Duration.ofSeconds(-1));

        assertThatThrownBy(() -> jwtProvider.validateToken(token))
                .isInstanceOf(JwtProvider.TokenValidationException.class)
                .extracting("code").isEqualTo("TOKEN_EXPIRED");
    }

    @Test
    void validateToken_tampered_throwsException() {
        String token = jwtProvider.generateAccessToken("u", "e@e.com", "ADMIN",
                Duration.ofMinutes(15));
        String tampered = token.substring(0, token.length() - 4) + "XXXX";

        assertThatThrownBy(() -> jwtProvider.validateToken(tampered))
                .isInstanceOf(JwtProvider.TokenValidationException.class);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = findField(target.getClass(), fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) return findField(clazz.getSuperclass(), name);
            throw e;
        }
    }
}
