package com.puj.usuarios.integracion;

import com.puj.seguridad.rbac.Rol;
import com.puj.usuarios.autenticacion.dominio.Usuario;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TI-001 — Prueba de integración: dominio de Usuario sin infraestructura real.
 *
 * <p>Verifica el comportamiento de la entidad {@link Usuario} de forma aislada:
 * bloqueo por intentos fallidos, acceso exitoso y borrado lógico.
 * No requiere Docker ni base de datos real.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@DisplayName("TI-001 · Integración dominio Usuario")
class UsuarioDominioIntegracionTest {

    private Usuario usuario;

    @BeforeEach
    void setUp() throws Exception {
        usuario = new Usuario();
        setField(usuario, "id", UUID.randomUUID());
        usuario.establecerCorreo("ti001@puj.edu.co");
        usuario.establecerNombre("Ana");
        usuario.establecerApellido("García");
        usuario.establecerRol(Rol.STUDENT);
        usuario.establecerActivo(true);
        usuario.establecerConsentimientoDado(true);
        usuario.establecerFechaConsentimiento(Instant.now());
    }

    // ── TI-001-A: Bloqueo tras 5 intentos fallidos ───────────────────────────

    @Test
    @DisplayName("TI-001-A · 5 intentos fallidos → cuenta bloqueada")
    void cincoIntentosFallidos_debenBloquearCuenta() {
        for (int i = 0; i < 5; i++) {
            usuario.registrarIntentoFallido();
        }
        assertThat(usuario.estaBloqueado())
                .as("La cuenta debe estar bloqueada tras 5 intentos fallidos")
                .isTrue();
        assertThat(usuario.obtenerBloqueadoHasta())
                .as("bloqueadoHasta debe ser un instante futuro")
                .isAfter(Instant.now());
    }

    @Test
    @DisplayName("TI-001-B · 4 intentos fallidos → cuenta NO bloqueada")
    void cuatroIntentosFallidos_noBloqueaCuenta() {
        for (int i = 0; i < 4; i++) {
            usuario.registrarIntentoFallido();
        }
        assertThat(usuario.estaBloqueado())
                .as("4 intentos NO deben bloquear la cuenta")
                .isFalse();
    }

    @Test
    @DisplayName("TI-001-C · Acceso exitoso resetea contadores")
    void accesoExitoso_resetea_intentosFallidos() {
        for (int i = 0; i < 3; i++) {
            usuario.registrarIntentoFallido();
        }
        usuario.registrarAccesoExitoso();

        assertThat(usuario.obtenerIntentosFallidos())
                .as("Los intentos fallidos deben restablecerse a 0")
                .isZero();
        assertThat(usuario.estaBloqueado())
                .as("La cuenta no debe estar bloqueada tras acceso exitoso")
                .isFalse();
        assertThat(usuario.obtenerUltimoAcceso())
                .as("ultimoAcceso debe registrarse")
                .isNotNull();
    }

    @Test
    @DisplayName("TI-001-D · Borrado lógico desactiva la cuenta")
    void borradoLogico_desactiva_cuenta() {
        usuario.eliminarLogicamente();

        assertThat(usuario.estaEliminado())
                .as("El usuario debe aparecer como eliminado")
                .isTrue();
        assertThat(usuario.esActivo())
                .as("El usuario debe estar inactivo")
                .isFalse();
        assertThat(usuario.obtenerEliminadoEn())
                .as("eliminadoEn debe estar establecido")
                .isNotNull();
    }

    @Test
    @DisplayName("TI-001-E · Usuario nuevo no está bloqueado ni eliminado")
    void usuarioNuevo_estadoInicial_correcto() {
        assertThat(usuario.estaBloqueado()).isFalse();
        assertThat(usuario.estaEliminado()).isFalse();
        assertThat(usuario.esActivo()).isTrue();
        assertThat(usuario.esConsentimientoDado()).isTrue();
        assertThat(usuario.obtenerIntentosFallidos()).isZero();
    }

    // ── TI-002: TokenRefresh ──────────────────────────────────────────────────

    @Test
    @DisplayName("TI-002-A · TokenRefresh expirado es inválido")
    void tokenRefreshExpirado_esInvalido() throws Exception {
        com.puj.usuarios.autenticacion.dominio.TokenRefresh token =
                new com.puj.usuarios.autenticacion.dominio.TokenRefresh();
        token.establecerUsuario(usuario);
        token.establecerHashToken("$2a$10$dummyhashfortest");
        // Expirado: en el pasado
        token.establecerExpiraEn(Instant.now().minusSeconds(60));

        assertThat(token.estaExpirado()).isTrue();
        assertThat(token.esValido()).isFalse();
    }

    @Test
    @DisplayName("TI-002-B · TokenRefresh vigente y no revocado es válido")
    void tokenRefreshVigente_esValido() throws Exception {
        com.puj.usuarios.autenticacion.dominio.TokenRefresh token =
                new com.puj.usuarios.autenticacion.dominio.TokenRefresh();
        token.establecerUsuario(usuario);
        token.establecerHashToken("$2a$10$dummyhashfortest");
        token.establecerExpiraEn(Instant.now().plusSeconds(3600));

        assertThat(token.estaExpirado()).isFalse();
        assertThat(token.estaRevocado()).isFalse();
        assertThat(token.esValido()).isTrue();
    }

    @Test
    @DisplayName("TI-002-C · TokenRefresh revocado es inválido aunque no haya expirado")
    void tokenRefreshRevocado_esInvalido() throws Exception {
        com.puj.usuarios.autenticacion.dominio.TokenRefresh token =
                new com.puj.usuarios.autenticacion.dominio.TokenRefresh();
        token.establecerUsuario(usuario);
        token.establecerHashToken("$2a$10$dummyhashfortest");
        token.establecerExpiraEn(Instant.now().plusSeconds(3600));
        token.revocar();

        assertThat(token.estaRevocado()).isTrue();
        assertThat(token.esValido()).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setField(Object target, String name, Object value) throws Exception {
        Field f = findField(target.getClass(), name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private Field findField(Class<?> cls, String name) throws NoSuchFieldException {
        try {
            return cls.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            if (cls.getSuperclass() != null) return findField(cls.getSuperclass(), name);
            throw e;
        }
    }
}
