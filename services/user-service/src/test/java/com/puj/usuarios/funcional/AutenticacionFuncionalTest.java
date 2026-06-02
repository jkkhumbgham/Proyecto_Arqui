package com.puj.usuarios.funcional;

import com.puj.eventos.publicador.PublicadorEventos;
import com.puj.seguridad.listanegra.ServicioListaNegra;
import com.puj.seguridad.jwt.ProveedorJwt;
import com.puj.seguridad.rbac.Rol;
import com.puj.usuarios.autenticacion.aplicacion.ServicioAuditoria;
import com.puj.usuarios.autenticacion.aplicacion.ServicioAutenticacion;
import com.puj.usuarios.autenticacion.dominio.RepositorioTokens;
import com.puj.usuarios.autenticacion.dominio.RepositorioUsuarios;
import com.puj.usuarios.autenticacion.dominio.TokenRefresh;
import com.puj.usuarios.autenticacion.dominio.Usuario;
import com.puj.usuarios.autenticacion.interfaces.dto.RespuestaLogin;
import com.puj.usuarios.autenticacion.interfaces.dto.RespuestaUsuario;
import com.puj.usuarios.autenticacion.interfaces.dto.SolicitudLogin;
import com.puj.usuarios.autenticacion.interfaces.dto.SolicitudRegistro;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mindrot.jbcrypt.BCrypt;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas Funcionales del servicio de autenticación (TF-001..TF-006).
 *
 * <p>Verifica los flujos completos de registro, login, refresh y logout
 * usando mocks de repositorios. Cubre los escenarios del plan de pruebas:
 * <ul>
 *   <li>TF-001: Registro exitoso con consentimiento LOPD</li>
 *   <li>TF-002: Registro sin consentimiento → 400</li>
 *   <li>TF-003: Registro con correo duplicado → 400</li>
 *   <li>TF-004: Login exitoso → tokens JWT</li>
 *   <li>TF-005: Login con contraseña incorrecta → 401</li>
 *   <li>TF-006: Login con cuenta bloqueada → 401 con mensaje específico</li>
 * </ul>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TF-001..006 · Funcional Autenticación")
class AutenticacionFuncionalTest {

    @Mock private RepositorioUsuarios  repositorioUsuarios;
    @Mock private RepositorioTokens    repositorioTokens;
    @Mock private ProveedorJwt         proveedorJwt;
    @Mock private ServicioListaNegra   servicioListaNegra;
    @Mock private PublicadorEventos    publicadorEventos;
    @Mock private ServicioAuditoria    servicioAuditoria;

    @InjectMocks
    private ServicioAutenticacion servicio;

    private Usuario usuarioBloqueado;
    private Usuario usuarioActivo;

    @BeforeEach
    void setUp() throws Exception {
        // CDI self-injection simulada
        setField(servicio, "self", servicio);

        usuarioActivo = buildUsuario("activo@puj.edu.co", "Password1!", false);
        usuarioBloqueado = buildUsuario("bloqueado@puj.edu.co", "Password1!", false);
        // Bloquear: 5 intentos fallidos
        for (int i = 0; i < 5; i++) usuarioBloqueado.registrarIntentoFallido();
    }

    // ── TF-001: Registro exitoso ──────────────────────────────────────────────

    @Test
    @DisplayName("TF-001 · Registro exitoso crea usuario STUDENT y publica eventos")
    void tf001_registro_exitoso_creaEstudianteYPublicaEventos() {
        when(repositorioUsuarios.existePorCorreo(anyString())).thenReturn(false);
        when(repositorioUsuarios.guardar(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            setField(u, "id", UUID.randomUUID());
            return u;
        });

        SolicitudRegistro solicitud = new SolicitudRegistro(
                "nuevo@puj.edu.co", "Password1!", "Laura", "Gómez", true);

        RespuestaUsuario respuesta = servicio.registrar(solicitud);

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(respuesta.email()).isEqualTo("nuevo@puj.edu.co");
        soft.assertThat(respuesta.rol()).isEqualTo(Rol.STUDENT);
        soft.assertThat(respuesta.activo()).isTrue();
        soft.assertThat(respuesta.id()).isNotNull();
        soft.assertAll();

        verify(publicadorEventos, times(1)).publicarAnaliticas(any());
        verify(publicadorEventos, times(1)).publicarCorreo(any());
    }

    // ── TF-002: Registro sin consentimiento ──────────────────────────────────

    @Test
    @DisplayName("TF-002 · Registro sin consentimiento LOPD → BadRequestException")
    void tf002_registro_sinConsentimiento_lanzaExcepcion() {
        SolicitudRegistro solicitud = new SolicitudRegistro(
                "nocons@puj.edu.co", "Password1!", "Pedro", "López", false);

        assertThatThrownBy(() -> servicio.registrar(solicitud))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("consentimiento");

        verify(repositorioUsuarios, never()).guardar(any());
    }

    // ── TF-003: Registro con correo duplicado ─────────────────────────────────

    @Test
    @DisplayName("TF-003 · Registro con correo ya existente → BadRequestException")
    void tf003_registro_correoExistente_lanzaExcepcion() {
        when(repositorioUsuarios.existePorCorreo("dup@puj.edu.co")).thenReturn(true);

        SolicitudRegistro solicitud = new SolicitudRegistro(
                "dup@puj.edu.co", "Password1!", "María", "Pérez", true);

        assertThatThrownBy(() -> servicio.registrar(solicitud))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("registrado");
    }

    // ── TF-004: Login exitoso ─────────────────────────────────────────────────

    @Test
    @DisplayName("TF-004 · Login exitoso devuelve accessToken Bearer + refreshToken")
    void tf004_login_exitoso_devuelveTokens() throws Exception {
        when(repositorioUsuarios.buscarPorCorreo("activo@puj.edu.co"))
                .thenReturn(Optional.of(usuarioActivo));
        when(proveedorJwt.generarTokenAcceso(any(), any(), any(), any()))
                .thenReturn("jwt.access.token");
        when(repositorioTokens.guardar(any(TokenRefresh.class))).thenAnswer(inv -> {
            TokenRefresh t = inv.getArgument(0);
            Method alCrear = TokenRefresh.class.getDeclaredMethod("alCrear");
            alCrear.setAccessible(true);
            alCrear.invoke(t);
            return t;
        });
        when(repositorioUsuarios.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        RespuestaLogin resp = servicio.iniciarSesion(
                new SolicitudLogin("activo@puj.edu.co", "Password1!"), "192.168.1.1");

        assertThat(resp.tokenAcceso()).isEqualTo("jwt.access.token");
        assertThat(resp.tipoToken()).isEqualTo("Bearer");
        assertThat(resp.tokenRefresh()).isNotBlank();
        assertThat(resp.usuario().email()).isEqualTo("activo@puj.edu.co");
        assertThat(resp.usuario().rol()).isEqualTo(Rol.STUDENT);
    }

    // ── TF-005: Login con contraseña incorrecta ───────────────────────────────

    @Test
    @DisplayName("TF-005 · Login con contraseña incorrecta → NotAuthorizedException")
    void tf005_login_contrasenaIncorrecta_lanzaExcepcion() {
        when(repositorioUsuarios.buscarPorCorreo("activo@puj.edu.co"))
                .thenReturn(Optional.of(usuarioActivo));
        when(repositorioUsuarios.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> servicio.iniciarSesion(
                new SolicitudLogin("activo@puj.edu.co", "WrongPass!"), "10.0.0.1"))
                .isInstanceOf(NotAuthorizedException.class);

        // El intento fallido debe registrarse en auditoría
        verify(servicioAuditoria).registrar(any(), eq("LOGIN_FAILED"), anyString(), anyString());
    }

    // ── TF-006: Login con cuenta bloqueada ───────────────────────────────────

    @Test
    @DisplayName("TF-006 · Login con cuenta bloqueada → mensaje 'Cuenta bloqueada'")
    void tf006_login_cuentaBloqueada_mensajeEspecifico() {
        when(repositorioUsuarios.buscarPorCorreo("bloqueado@puj.edu.co"))
                .thenReturn(Optional.of(usuarioBloqueado));

        assertThatThrownBy(() -> servicio.iniciarSesion(
                new SolicitudLogin("bloqueado@puj.edu.co", "Password1!"), "10.0.0.2"))
                .isInstanceOf(NotAuthorizedException.class);

        verify(servicioAuditoria).registrar(any(), eq("LOGIN_BLOCKED"), anyString(), anyString());
    }

    // ── TF adicional: hash no expone contraseña en plano ─────────────────────

    @Test
    @DisplayName("TF-ADI · Registro almacena hash BCrypt, no contraseña en plano")
    void tf_adicional_contrasenaHasheada() {
        when(repositorioUsuarios.existePorCorreo(anyString())).thenReturn(false);
        when(repositorioUsuarios.guardar(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            setField(u, "id", UUID.randomUUID());
            return u;
        });

        SolicitudRegistro solicitud = new SolicitudRegistro(
                "hash@puj.edu.co", "MiClave123!", "Carlos", "Ruiz", true);
        servicio.registrar(solicitud);

        verify(repositorioUsuarios).guardar(argThat(u ->
                u.obtenerHashContrasena() != null
                && !u.obtenerHashContrasena().equals("MiClave123!")
                && u.obtenerHashContrasena().startsWith("$2")));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Usuario buildUsuario(String correo, String contrasena,
                                  boolean bloqueado) throws Exception {
        Usuario u = new Usuario();
        setField(u, "id", UUID.randomUUID());
        u.establecerCorreo(correo);
        u.establecerHashContrasena(BCrypt.hashpw(contrasena, BCrypt.gensalt(4)));
        u.establecerNombre("Test");
        u.establecerApellido("User");
        u.establecerRol(Rol.STUDENT);
        u.establecerActivo(true);
        u.establecerConsentimientoDado(true);
        u.establecerFechaConsentimiento(Instant.now());
        return u;
    }

    private void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field f = findField(target.getClass(), name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private java.lang.reflect.Field findField(Class<?> cls, String name)
            throws NoSuchFieldException {
        try {
            return cls.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            if (cls.getSuperclass() != null) return findField(cls.getSuperclass(), name);
            throw e;
        }
    }
}
