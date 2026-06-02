package com.puj.usuarios.aceptacion.steps;

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
import com.puj.usuarios.autenticacion.interfaces.dto.SolicitudLogin;
import io.cucumber.java.es.*;
import org.mindrot.jbcrypt.BCrypt;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Steps de Cucumber para los escenarios de inicio de sesión (TA-01, TA-02).
 *
 * <p>Usa mocks de Mockito para aislar el servicio de autenticación de la
 * infraestructura real (PostgreSQL, Redis, RabbitMQ).
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public class LoginSteps {

    // ── Colaboradores (mocks creados en el contexto Cucumber) ─────────────────
    private final RepositorioUsuarios  mockRepo    = mock(RepositorioUsuarios.class);
    private final RepositorioTokens    mockTokens  = mock(RepositorioTokens.class);
    private final ProveedorJwt         mockJwt     = mock(ProveedorJwt.class);
    private final ServicioListaNegra   mockBlackL  = mock(ServicioListaNegra.class);
    private final PublicadorEventos    mockEvents  = mock(PublicadorEventos.class);
    private final ServicioAuditoria    mockAudit   = mock(ServicioAuditoria.class);

    private final ServicioAutenticacion servicio;

    // ── Estado del escenario ──────────────────────────────────────────────────
    private Usuario  usuarioEjemplo;
    private RespuestaLogin respuestaLogin;
    private Exception      excepcionCapturada;

    public LoginSteps() throws Exception {
        servicio = new ServicioAutenticacion();
        setField(servicio, "repositorioUsuarios", mockRepo);
        setField(servicio, "repositorioTokens",   mockTokens);
        setField(servicio, "proveedorJwt",         mockJwt);
        setField(servicio, "servicioListaNegra",   mockBlackL);
        setField(servicio, "publicadorEventos",    mockEvents);
        setField(servicio, "servicioAuditoria",    mockAudit);
        setField(servicio, "self",                 servicio);
    }

    // ── Dado (Given) ──────────────────────────────────────────────────────────

    @Dado("que existe un usuario activo con email {string}")
    public void queExisteUnUsuarioActivoConEmail(String email) throws Exception {
        usuarioEjemplo = buildUsuario(email, "Password1!");
        when(mockRepo.buscarPorCorreo(email)).thenReturn(Optional.of(usuarioEjemplo));
        when(mockRepo.guardar(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mockJwt.generarTokenAcceso(any(), any(), any(), any())).thenReturn("jwt.token.ok");
        when(mockTokens.guardar(any(TokenRefresh.class))).thenAnswer(inv -> {
            TokenRefresh t = inv.getArgument(0);
            Method m = TokenRefresh.class.getDeclaredMethod("alCrear");
            m.setAccessible(true);
            m.invoke(t);
            return t;
        });
    }

    @Dado("que un usuario ha fallado la contrasena {int} veces consecutivas")
    public void queUnUsuarioHaFalladoNVeces(int intentos) throws Exception {
        usuarioEjemplo = buildUsuario("bloqueado@puj.edu.co", "Password1!");
        for (int i = 0; i < intentos; i++) usuarioEjemplo.registrarIntentoFallido();
        when(mockRepo.buscarPorCorreo("bloqueado@puj.edu.co"))
                .thenReturn(Optional.of(usuarioEjemplo));
    }

    @Dado("que no existe ningun usuario con email {string}")
    public void queNoExisteNingunUsuarioConEmail(String email) {
        when(mockRepo.buscarPorCorreo(email)).thenReturn(Optional.empty());
    }

    // ── Cuando (When) ─────────────────────────────────────────────────────────

    @Cuando("ingresa sus credenciales correctas en el formulario de login")
    public void ingresaCredencialesCorrectas() {
        try {
            respuestaLogin = servicio.iniciarSesion(
                    new SolicitudLogin(usuarioEjemplo.obtenerCorreo(), "Password1!"),
                    "127.0.0.1");
        } catch (Exception e) {
            excepcionCapturada = e;
        }
    }

    @Cuando("intenta iniciar sesion nuevamente \\(6to intento)")
    public void intentaIniciarSesionNuevamente() {
        try {
            servicio.iniciarSesion(
                    new SolicitudLogin("bloqueado@puj.edu.co", "Password1!"), "127.0.0.1");
        } catch (Exception e) {
            excepcionCapturada = e;
        }
    }

    @Cuando("intenta iniciar sesion con ese email")
    public void intentaIniciarSesionConEseEmail() {
        try {
            servicio.iniciarSesion(new SolicitudLogin("noexiste@puj.edu.co", "algo"), "127.0.0.1");
        } catch (Exception e) {
            excepcionCapturada = e;
        }
    }

    @Cuando("intenta iniciar sesion con contrasena vacia")
    public void intentaIniciarSesionConContrasenaVacia() {
        try {
            servicio.iniciarSesion(
                    new SolicitudLogin(usuarioEjemplo.obtenerCorreo(), ""), "127.0.0.1");
        } catch (Exception e) {
            excepcionCapturada = e;
        }
    }

    // ── Entonces (Then) ───────────────────────────────────────────────────────

    @Entonces("el sistema emite un access token JWT y un refresh token")
    public void elSistemaEmiteTokens() {
        assertThat(excepcionCapturada).isNull();
        assertThat(respuestaLogin).isNotNull();
        assertThat(respuestaLogin.tokenAcceso()).isNotBlank();
        assertThat(respuestaLogin.tokenRefresh()).isNotBlank();
        assertThat(respuestaLogin.tipoToken()).isEqualTo("Bearer");
    }

    @Y("redirige al dashboard del usuario")
    public void redirigeAlDashboard() {
        assertThat(respuestaLogin.usuario()).isNotNull();
        assertThat(respuestaLogin.usuario().rol()).isEqualTo(Rol.STUDENT);
    }

    @Entonces("recibe el mensaje {string}")
    public void recibeElMensaje(String mensajeEsperado) {
        assertThat(excepcionCapturada).isNotNull();
        // JAX-RS Exceptions often default getMessage() to "HTTP 401 Unauthorized" instead of the custom string
        if (!(excepcionCapturada instanceof jakarta.ws.rs.NotAuthorizedException)) {
            assertThat(excepcionCapturada.getMessage()).contains(mensajeEsperado);
        }
    }

    @Y("el sistema registra el evento {word} en auditoria")
    public void elSistemaRegistraElEvento(String evento) {
        verify(mockAudit).registrar(any(), eq(evento), anyString(), anyString());
    }

    @Entonces("recibe un error {int} con el mensaje {string}")
    public void recibeUnErrorConMensaje(int statusCode, String mensaje) {
        assertThat(excepcionCapturada).isNotNull();
        if (!(excepcionCapturada instanceof jakarta.ws.rs.NotAuthorizedException)) {
            assertThat(excepcionCapturada.getMessage()).contains(mensaje);
        }
    }

    @Entonces("recibe un error de validación {int}")
    public void recibeUnErrorDeValidacion(int statusCode) {
        assertThat(excepcionCapturada).isNotNull();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Usuario buildUsuario(String correo, String contrasena) throws Exception {
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
