package com.puj.seguridad.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class ProveedorJwtTest {

    private ProveedorJwt proveedorJwt;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        ProveedorClaves proveedorClaves = new ProveedorClaves();
        setField(proveedorClaves, "clavePrivada", kp.getPrivate());
        setField(proveedorClaves, "clavePublica",  kp.getPublic());

        proveedorJwt = new ProveedorJwt();
        setField(proveedorJwt, "proveedorClaves", proveedorClaves);
    }

    @Test
    void generarYValidar_roundTrip() {
        String token = proveedorJwt.generarTokenAcceso("usuario-1", "prueba@puj.edu.co", "STUDENT",
                Duration.ofMinutes(15));

        ReclamosJwt reclamos = proveedorJwt.validarToken(token);

        assertThat(reclamos.idUsuario()).isEqualTo("usuario-1");
        assertThat(reclamos.correo()).isEqualTo("prueba@puj.edu.co");
        assertThat(reclamos.rol()).isEqualTo("STUDENT");
        assertThat(reclamos.jti()).isNotBlank();
    }

    @Test
    void validarToken_expirado_lanzaExcepcion() throws Exception {
        String token = proveedorJwt.generarTokenAcceso("u", "e@e.com", "ADMIN",
                Duration.ofSeconds(-1));

        assertThatThrownBy(() -> proveedorJwt.validarToken(token))
                .isInstanceOf(ProveedorJwt.ExcepcionValidacionToken.class)
                .extracting(t -> ((ProveedorJwt.ExcepcionValidacionToken) t).obtenerCodigo())
                .isEqualTo("TOKEN_EXPIRED");
    }

    @Test
    void validarToken_manipulado_lanzaExcepcion() {
        String token = proveedorJwt.generarTokenAcceso("u", "e@e.com", "ADMIN",
                Duration.ofMinutes(15));
        String manipulado = token.substring(0, token.length() - 4) + "XXXX";

        assertThatThrownBy(() -> proveedorJwt.validarToken(manipulado))
                .isInstanceOf(ProveedorJwt.ExcepcionValidacionToken.class);
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
