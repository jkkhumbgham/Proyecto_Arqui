package com.puj.usuarios.autenticacion.aplicacion;

import com.puj.eventos.EventoNotificacionCorreo;
import com.puj.eventos.EventoUsuarioConectado;
import com.puj.eventos.EventoUsuarioRegistrado;
import com.puj.eventos.publicador.PublicadorEventos;
import com.puj.seguridad.listanegra.ServicioListaNegra;
import com.puj.seguridad.jwt.ReclamosJwt;
import com.puj.seguridad.jwt.ProveedorJwt;
import com.puj.seguridad.rbac.Rol;
import com.puj.usuarios.aspectos.registro.Registrable;
import com.puj.usuarios.autenticacion.dominio.RepositorioTokens;
import com.puj.usuarios.autenticacion.dominio.RepositorioUsuarios;
import com.puj.usuarios.autenticacion.dominio.TokenRefresh;
import com.puj.usuarios.autenticacion.dominio.Usuario;
import com.puj.usuarios.autenticacion.interfaces.dto.SolicitudLogin;
import com.puj.usuarios.autenticacion.interfaces.dto.SolicitudRegistro;
import com.puj.usuarios.autenticacion.interfaces.dto.RespuestaLogin;
import com.puj.usuarios.autenticacion.interfaces.dto.RespuestaUsuario;
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
@Registrable
public class ServicioAutenticacion {

    /** Tiempo de vida del access token en segundos (por defecto 15 minutos). */
    private static final long TTL_ACCESO_SEGUNDOS =
            Long.parseLong(System.getenv()
                    .getOrDefault("JWT_ACCESS_TTL_SECONDS", "900"));

    /** Tiempo de vida del refresh token en segundos (por defecto 7 días). */
    private static final long TTL_REFRESH_SEGUNDOS =
            Long.parseLong(System.getenv()
                    .getOrDefault("JWT_REFRESH_TTL_SECONDS", "604800"));

    @Inject private RepositorioUsuarios  repositorioUsuarios;
    @Inject private RepositorioTokens    repositorioTokens;
    @Inject private ProveedorJwt         proveedorJwt;
    @Inject private ServicioListaNegra   servicioListaNegra;
    @Inject private PublicadorEventos    publicadorEventos;
    @Inject private ServicioAuditoria    servicioAuditoria;
    // Self-injection: permite llamar métodos @Transactional a través del proxy CDI
    @Inject private ServicioAutenticacion self;

    // =========================================================================
    // API pública
    // =========================================================================

    /**
     * Registra un nuevo usuario con rol {@code STUDENT} y publica los eventos
     * de analítica y bienvenida por correo.
     *
     * @param solicitud datos del nuevo usuario incluyendo consentimiento de datos.
     * @return representación pública del usuario recién creado.
     * @throws BadRequestException si el consentimiento no fue otorgado o si el
     *                             correo electrónico ya está registrado.
     */
    public RespuestaUsuario registrar(SolicitudRegistro solicitud) {
        RespuestaUsuario respuesta = self.persistirRegistro(solicitud);
        publicarEventosRegistro(respuesta);
        return respuesta;
    }

    /** Parte transaccional del registro — solo operaciones de BD. */
    @Transactional
    public RespuestaUsuario persistirRegistro(SolicitudRegistro solicitud) {
        validarPrecondicionesRegistro(solicitud);
        Usuario usuario = construirNuevoUsuario(solicitud);
        repositorioUsuarios.guardar(usuario);
        return RespuestaUsuario.desde(usuario);
    }

    /**
     * Autentica al usuario con sus credenciales y emite un par de tokens.
     *
     * <p>El método verifica en orden: existencia del usuario, estado activo,
     * bloqueo temporal y contraseña. Cualquier fallo incrementa el contador de
     * intentos y genera una entrada de auditoría sin revelar si la cuenta existe.
     *
     * @param solicitud  credenciales de inicio de sesión.
     * @param direccionIp dirección IP del cliente para auditoría.
     * @return respuesta con access token, refresh token y datos del usuario.
     * @throws NotAuthorizedException si las credenciales son inválidas o la
     *                                cuenta está bloqueada o inactiva.
     */
    public RespuestaLogin iniciarSesion(SolicitudLogin solicitud, String direccionIp) {
        RespuestaLogin respuesta = self.persistirSesion(solicitud, direccionIp);
        publicarEventoAcceso(respuesta);
        return respuesta;
    }

    /** Parte transaccional del login — BD + auditoría, sin RabbitMQ. */
    @Transactional
    public RespuestaLogin persistirSesion(SolicitudLogin solicitud, String direccionIp) {
        Usuario usuario = resolverUsuarioActivo(solicitud, direccionIp);
        verificarContrasena(solicitud, usuario, direccionIp);
        usuario.registrarAccesoExitoso();
        String tokenAcceso   = emitirTokenAcceso(usuario);
        String[] idYValorRaw = crearTokenRefresh(usuario);
        repositorioUsuarios.guardar(usuario);
        servicioAuditoria.registrar(usuario.getId(), "LOGIN_SUCCESS", usuario.obtenerCorreo(), direccionIp);
        return construirRespuestaLogin(tokenAcceso, idYValorRaw, usuario);
    }

    /**
     * Renueva el access token a partir de un refresh token válido.
     *
     * <p>El refresh token se recibe como una cadena compuesta por el UUID del
     * registro y el valor aleatorio separados por dos puntos
     * ({@code "<uuid>:<raw>"}).
     *
     * @param valorTokenRefresh token de renovación en formato {@code "<uuid>:<raw>"}.
     * @return nueva respuesta con el access token renovado.
     * @throws NotAuthorizedException si el formato, el identificador o el hash
     *                                del refresh token no son válidos.
     */
    @Transactional
    public RespuestaLogin renovarToken(String valorTokenRefresh) {
        String[] partes = valorTokenRefresh.split(":", 2);
        if (partes.length != 2) {
            throw new NotAuthorizedException("Refresh token inválido.");
        }

        UUID idToken = UUID.fromString(partes[0]);
        String raw   = partes[1];

        TokenRefresh almacenado = repositorioTokens.buscarPorId(idToken)
                .orElseThrow(() ->
                        new NotAuthorizedException("Refresh token no encontrado."));

        if (!almacenado.esValido() || !BCrypt.checkpw(raw, almacenado.obtenerHashToken())) {
            throw new NotAuthorizedException("Refresh token inválido o expirado.");
        }

        Usuario usuario = almacenado.obtenerUsuario();

        // Revocar el refresh token usado (no puede reusarse — refresh token rotation)
        almacenado.revocar();
        repositorioTokens.sincronizar(almacenado);

        // Emitir par fresco
        String nuevoAcceso = proveedorJwt.generarTokenAcceso(
                usuario.getId().toString(), usuario.obtenerCorreo(), usuario.obtenerRol().name(),
                Duration.ofSeconds(TTL_ACCESO_SEGUNDOS));
        String[] nuevoRefresh = crearTokenRefresh(usuario);

        return RespuestaLogin.de(nuevoAcceso,
                nuevoRefresh[0] + ":" + nuevoRefresh[1],
                TTL_ACCESO_SEGUNDOS, RespuestaUsuario.desde(usuario));
    }

    /**
     * Cierra la sesión del usuario añadiendo el JTI del access token a la lista
     * negra y revocando todos sus refresh tokens activos.
     *
     * @param reclamos    reclamos del JWT del usuario que solicita el cierre de sesión.
     * @param direccionIp dirección IP del cliente para auditoría.
     */
    @Transactional
    public void cerrarSesion(ReclamosJwt reclamos, String direccionIp) {
        long ttl = proveedorJwt.obtenerTtlRestanteSegundos(reclamos.jti());
        servicioListaNegra.agregarAListaNegra(reclamos.jti(), ttl);
        repositorioTokens.revocarTodosDelUsuario(UUID.fromString(reclamos.idUsuario()));
        servicioAuditoria.registrar(
                UUID.fromString(reclamos.idUsuario()), "LOGOUT",
                reclamos.correo(), direccionIp);
    }

    // =========================================================================
    // Helpers privados — registro
    // =========================================================================

    /**
     * Valida las precondiciones del registro: consentimiento y unicidad del correo.
     *
     * @param solicitud datos del formulario de registro.
     * @throws BadRequestException si el consentimiento no fue otorgado o el
     *                             correo ya está registrado.
     */
    private void validarPrecondicionesRegistro(SolicitudRegistro solicitud) {
        if (!Boolean.TRUE.equals(solicitud.consentimientoDado())) {
            throw new BadRequestException(
                    "Se requiere consentimiento de tratamiento de datos (Ley 1581/2012).");
        }
        if (repositorioUsuarios.existePorCorreo(solicitud.email())) {
            throw new BadRequestException("El correo electrónico ya está registrado.");
        }
    }

    /**
     * Construye y devuelve una entidad {@link Usuario} con los datos del registro.
     *
     * @param solicitud datos del formulario de registro validados.
     * @return nueva entidad de usuario sin persistir.
     */
    private Usuario construirNuevoUsuario(SolicitudRegistro solicitud) {
        Usuario usuario = new Usuario();
        usuario.establecerCorreo(solicitud.email().toLowerCase().trim());
        usuario.establecerHashContrasena(BCrypt.hashpw(solicitud.contrasena(), BCrypt.gensalt(12)));
        usuario.establecerNombre(solicitud.nombre().trim());
        usuario.establecerApellido(solicitud.apellido().trim());
        usuario.establecerRol(Rol.STUDENT);
        usuario.establecerConsentimientoDado(true);
        usuario.establecerFechaConsentimiento(Instant.now());
        return usuario;
    }

    /**
     * Publica los eventos de analítica y correo de bienvenida tras el registro.
     *
     * @param usuario usuario recién persistido.
     */
    private void publicarEventosRegistro(RespuestaUsuario respuesta) {
        publicadorEventos.publicarAnaliticas(new EventoUsuarioRegistrado(
                respuesta.id().toString(), respuesta.email(),
                respuesta.nombre(), respuesta.apellido(),
                respuesta.rol().name()));

        publicadorEventos.publicarCorreo(new EventoNotificacionCorreo(
                respuesta.email(), respuesta.nombre(),
                EventoNotificacionCorreo.TipoCorreo.WELCOME,
                Map.of("firstName", respuesta.nombre())));
    }

    // =========================================================================
    // Helpers privados — inicio de sesión
    // =========================================================================

    /**
     * Localiza el usuario por correo y verifica que la cuenta esté activa y
     * desbloqueada. Registra auditoría en caso de fallo.
     *
     * @param solicitud   credenciales de la solicitud de login.
     * @param direccionIp IP del cliente para auditoría.
     * @return entidad {@link Usuario} lista para verificación de contraseña.
     * @throws NotAuthorizedException si el usuario no existe, está inactivo
     *                                o está bloqueado.
     */
    private Usuario resolverUsuarioActivo(SolicitudLogin solicitud, String direccionIp) {
        Usuario usuario = repositorioUsuarios.buscarPorCorreo(solicitud.email()).orElse(null);
        if (usuario == null) {
            servicioAuditoria.registrar(null, "LOGIN_FAILED", solicitud.email(), direccionIp);
            throw new NotAuthorizedException("Credenciales inválidas.");
        }
        if (!usuario.esActivo() || usuario.estaEliminado()) {
            servicioAuditoria.registrar(usuario.getId(), "LOGIN_BLOCKED", usuario.obtenerCorreo(), direccionIp);
            throw new NotAuthorizedException("Cuenta inactiva o eliminada.");
        }
        if (usuario.estaBloqueado()) {
            servicioAuditoria.registrar(usuario.getId(), "LOGIN_BLOCKED", usuario.obtenerCorreo(), direccionIp);
            throw new NotAuthorizedException(
                    "Cuenta bloqueada por intentos fallidos. Intenta en 15 minutos.");
        }
        return usuario;
    }

    /**
     * Verifica la contraseña contra el hash almacenado; incrementa el contador
     * de fallos y registra auditoría si la contraseña no coincide.
     *
     * @param solicitud   credenciales de la solicitud de login.
     * @param usuario     entidad del usuario cuya contraseña se verifica.
     * @param direccionIp IP del cliente para auditoría.
     * @throws NotAuthorizedException si la contraseña no coincide.
     */
    private void verificarContrasena(SolicitudLogin solicitud, Usuario usuario, String direccionIp) {
        if (!BCrypt.checkpw(solicitud.contrasena(), usuario.obtenerHashContrasena())) {
            usuario.registrarIntentoFallido();
            repositorioUsuarios.guardar(usuario);
            servicioAuditoria.registrar(usuario.getId(), "LOGIN_FAILED", usuario.obtenerCorreo(), direccionIp);
            throw new NotAuthorizedException("Credenciales inválidas.");
        }
    }

    /**
     * Genera un nuevo access token JWT para el usuario.
     *
     * @param usuario usuario autenticado.
     * @return cadena JWT firmada.
     */
    private String emitirTokenAcceso(Usuario usuario) {
        return proveedorJwt.generarTokenAcceso(
                usuario.getId().toString(), usuario.obtenerCorreo(), usuario.obtenerRol().name(),
                Duration.ofSeconds(TTL_ACCESO_SEGUNDOS));
    }

    /**
     * Crea, hashea y persiste un nuevo refresh token para el usuario.
     *
     * @param usuario usuario autenticado al que se le emite el token.
     * @return arreglo de dos elementos: {@code [0]} UUID del token como cadena,
     *         {@code [1]} valor raw del token para incluir en la respuesta.
     */
    private String[] crearTokenRefresh(Usuario usuario) {
        String raw = UUID.randomUUID().toString();
        TokenRefresh refresh = new TokenRefresh();
        refresh.establecerUsuario(usuario);
        refresh.establecerHashToken(BCrypt.hashpw(raw, BCrypt.gensalt(10)));
        refresh.establecerExpiraEn(Instant.now().plusSeconds(TTL_REFRESH_SEGUNDOS));
        repositorioTokens.guardar(refresh);
        return new String[]{ refresh.getId().toString(), raw };
    }

    /**
     * Construye el {@link RespuestaLogin} combinando el access token, el refresh
     * token emitidos y los datos del usuario.
     *
     * @param tokenAcceso    cadena JWT de acceso.
     * @param idYValorRaw    arreglo {@code [uuid, raw]} devuelto por
     *                       {@link #crearTokenRefresh(Usuario)}.
     * @param usuario        usuario autenticado cuyos datos públicos se incluyen.
     * @return respuesta completa de login lista para serializar.
     */
    private RespuestaLogin construirRespuestaLogin(String tokenAcceso,
                                                   String[] idYValorRaw,
                                                   Usuario usuario) {
        String valorTokenRefresh = idYValorRaw[0] + ":" + idYValorRaw[1];
        return RespuestaLogin.de(tokenAcceso, valorTokenRefresh,
                TTL_ACCESO_SEGUNDOS, RespuestaUsuario.desde(usuario));
    }

    /**
     * Publica el evento de analítica que indica que el usuario inició sesión.
     *
     * @param usuario usuario que completó el inicio de sesión.
     */
    private void publicarEventoAcceso(RespuestaLogin respuesta) {
        publicadorEventos.publicarAnaliticas(new EventoUsuarioConectado(
                respuesta.usuario().id().toString(),
                respuesta.usuario().email(),
                respuesta.usuario().rol().name()));
    }
}
