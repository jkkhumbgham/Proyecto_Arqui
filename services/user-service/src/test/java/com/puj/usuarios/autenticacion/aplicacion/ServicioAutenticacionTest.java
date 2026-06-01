package com.puj.usuarios.autenticacion.aplicacion;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mindrot.jbcrypt.BCrypt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.puj.eventos.publicador.PublicadorEventos;
import com.puj.seguridad.listanegra.ServicioListaNegra;
import com.puj.seguridad.jwt.ReclamosJwt;
import com.puj.seguridad.jwt.ProveedorJwt;
import com.puj.seguridad.rbac.Rol;
import com.puj.usuarios.autenticacion.dominio.RepositorioTokens;
import com.puj.usuarios.autenticacion.dominio.RepositorioUsuarios;
import com.puj.usuarios.autenticacion.dominio.TokenRefresh;
import com.puj.usuarios.autenticacion.dominio.Usuario;
import com.puj.usuarios.autenticacion.interfaces.dto.SolicitudLogin;
import com.puj.usuarios.autenticacion.interfaces.dto.RespuestaLogin;
import com.puj.usuarios.autenticacion.interfaces.dto.SolicitudRegistro;
import com.puj.usuarios.autenticacion.interfaces.dto.RespuestaUsuario;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;

@ExtendWith(MockitoExtension.class)
class ServicioAutenticacionTest {

    @Mock private RepositorioUsuarios repositorioUsuarios;
    @Mock private RepositorioTokens   repositorioTokens;
    @Mock private ProveedorJwt        proveedorJwt;
    @Mock private ServicioListaNegra  servicioListaNegra;
    @Mock private PublicadorEventos   publicadorEventos;
    @Mock private ServicioAuditoria   servicioAuditoria;

    @InjectMocks
    private ServicioAutenticacion servicioAutenticacion;

    private Usuario usuarioEjemplo;

    @BeforeEach
    void configurar() {
        // CDI no existe en unit tests: inyectamos self manualmente igual que hace el contenedor
        establecerCampo(servicioAutenticacion, "self", servicioAutenticacion);

        usuarioEjemplo = new Usuario();
        establecerCampo(usuarioEjemplo, "id", UUID.randomUUID());
        usuarioEjemplo.establecerCorreo("test@puj.edu.co");
        usuarioEjemplo.establecerHashContrasena(BCrypt.hashpw("password123", BCrypt.gensalt(4)));
        usuarioEjemplo.establecerNombre("Juan");
        usuarioEjemplo.establecerApellido("Pérez");
        usuarioEjemplo.establecerRol(Rol.STUDENT);
        usuarioEjemplo.establecerActivo(true);
        usuarioEjemplo.establecerConsentimientoDado(true);
        usuarioEjemplo.establecerFechaConsentimiento(Instant.now());
    }

    @Test
    void registrar_sinConsentimiento_lanzaExcepcion() {
        SolicitudRegistro solicitud = new SolicitudRegistro(
                "new@puj.edu.co", "pass12345", "Ana", "López", false);

        assertThatThrownBy(() -> servicioAutenticacion.registrar(solicitud))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("consentimiento");
    }

    @Test
    void registrar_correoRepetido_lanzaExcepcion() {
        when(repositorioUsuarios.existePorCorreo(anyString())).thenReturn(true);
        SolicitudRegistro solicitud = new SolicitudRegistro(
                "dup@puj.edu.co", "pass12345", "Ana", "López", true);

        assertThatThrownBy(() -> servicioAutenticacion.registrar(solicitud))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("registrado");
    }

    @Test
    void registrar_exitoso_persisteYPublicaEventos() {
        when(repositorioUsuarios.existePorCorreo(anyString())).thenReturn(false);
        when(repositorioUsuarios.guardar(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            if (u.getId() == null) establecerCampo(u, "id", UUID.randomUUID());
            return u;
        });

        SolicitudRegistro solicitud = new SolicitudRegistro(
                "new@puj.edu.co", "pass12345", "Ana", "López", true);

        RespuestaUsuario respuesta = servicioAutenticacion.registrar(solicitud);

        assertThat(respuesta.email()).isEqualTo("new@puj.edu.co");
        assertThat(respuesta.rol()).isEqualTo(Rol.STUDENT);
        verify(publicadorEventos, times(1)).publicarAnaliticas(any());
        verify(publicadorEventos, times(1)).publicarCorreo(any());
    }

    @Test
    void iniciarSesion_contrasenaIncorrecta_lanzaExcepcion() {
        when(repositorioUsuarios.buscarPorCorreo("test@puj.edu.co"))
                .thenReturn(Optional.of(usuarioEjemplo));

        assertThatThrownBy(() -> servicioAutenticacion.iniciarSesion(
                new SolicitudLogin("test@puj.edu.co", "wrongpass"), "127.0.0.1"))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void iniciarSesion_exitoso_devuelveTokens() {
        when(repositorioUsuarios.buscarPorCorreo("test@puj.edu.co"))
                .thenReturn(Optional.of(usuarioEjemplo));
        when(proveedorJwt.generarTokenAcceso(any(), any(), any(), any()))
                .thenReturn("access.token.here");
        when(repositorioTokens.guardar(any(TokenRefresh.class)))
            .thenAnswer(invocation -> {
                TokenRefresh token = invocation.getArgument(0);
                Method m = TokenRefresh.class.getDeclaredMethod("alCrear");
                m.setAccessible(true);
                m.invoke(token);
                return token;
            });
        when(repositorioUsuarios.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        RespuestaLogin respuesta = servicioAutenticacion.iniciarSesion(
                new SolicitudLogin("test@puj.edu.co", "password123"), "127.0.0.1");

        assertThat(respuesta.tokenAcceso()).isEqualTo("access.token.here");
        assertThat(respuesta.tipoToken()).isEqualTo("Bearer");
        assertThat(respuesta.tokenRefresh()).isNotBlank();
    }

    @Test
    void cerrarSesion_agregaJtiAListaNegra() {
        ReclamosJwt reclamos = new ReclamosJwt("jti-123", usuarioEjemplo.getId().toString(),
                "test@puj.edu.co", "STUDENT", Instant.now(),
                Instant.now().plusSeconds(900));
        when(proveedorJwt.obtenerTtlRestanteSegundos("jti-123")).thenReturn(500L);

        servicioAutenticacion.cerrarSesion(reclamos, "127.0.0.1");

        verify(servicioListaNegra).agregarAListaNegra("jti-123", 500L);
        verify(repositorioTokens).revocarTodosDelUsuario(usuarioEjemplo.getId());
    }

    private void establecerCampo(Object destino, String nombre, Object valor) {
        try {
            var f = destino.getClass().getDeclaredField(nombre);
            f.setAccessible(true);
            f.set(destino, valor);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
