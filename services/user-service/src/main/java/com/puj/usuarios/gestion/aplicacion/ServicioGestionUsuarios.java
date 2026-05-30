package com.puj.usuarios.gestion.aplicacion;

import com.puj.eventos.EventoNotificacionCorreo;
import com.puj.eventos.EventoUsuarioRegistrado;
import com.puj.eventos.publicador.PublicadorEventos;
import com.puj.seguridad.rbac.Rol;
import com.puj.usuarios.aspectos.registro.Registrable;
import com.puj.usuarios.autenticacion.dominio.RegistroAuditoria;
import com.puj.usuarios.autenticacion.dominio.RepositorioAuditoria;
import com.puj.usuarios.autenticacion.dominio.RepositorioUsuarios;
import com.puj.usuarios.autenticacion.dominio.Usuario;
import com.puj.usuarios.autenticacion.dominio.excepciones.UsuarioNoEncontradoException;
import com.puj.usuarios.autenticacion.interfaces.dto.RespuestaUsuario;
import com.puj.usuarios.gestion.interfaces.dto.SolicitudActualizarUsuario;
import com.puj.usuarios.gestion.interfaces.dto.SolicitudCrearUsuario;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import org.mindrot.jbcrypt.BCrypt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de gestión de usuarios que expone operaciones de consulta,
 * actualización, creación administrativa y eliminación lógica.
 *
 * <p>Las operaciones de escritura generan entradas de auditoría en la misma
 * transacción mediante {@link RepositorioAuditoria} directamente, sin delegar en
 * {@code ServicioAuditoria} (que usa {@code REQUIRES_NEW}) para mantener la
 * atomicidad con la operación principal.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@ApplicationScoped
@Registrable
public class ServicioGestionUsuarios {

    @Inject private RepositorioUsuarios  repositorioUsuarios;
    @Inject private RepositorioAuditoria repositorioAuditoria;
    @Inject private PublicadorEventos    publicadorEventos;

    // =========================================================================
    // API pública — consultas
    // =========================================================================

    /**
     * Busca un usuario activo por su identificador.
     *
     * @param id UUID del usuario a buscar.
     * @return representación pública del usuario.
     * @throws UsuarioNoEncontradoException si no existe un usuario activo con ese ID.
     */
    public RespuestaUsuario buscarPorId(UUID id) {
        return repositorioUsuarios.buscarPorId(id)
                .map(RespuestaUsuario::desde)
                .orElseThrow(() ->
                        new UsuarioNoEncontradoException("Usuario no encontrado: " + id));
    }

    /**
     * Devuelve una página de usuarios activos ordenados por fecha de creación.
     *
     * @param pagina   número de página (basado en cero).
     * @param cantidad número máximo de resultados por página.
     * @return lista de representaciones públicas de usuarios.
     */
    public List<RespuestaUsuario> buscarTodos(int pagina, int cantidad) {
        return repositorioUsuarios.buscarTodos(pagina, cantidad).stream()
                .map(RespuestaUsuario::desde)
                .toList();
    }

    /**
     * Cuenta el total de usuarios activos en la plataforma.
     *
     * @return número total de usuarios activos.
     */
    public long contar() {
        return repositorioUsuarios.contarTodos();
    }

    /**
     * Devuelve una página de usuarios que no han iniciado sesión en los últimos
     * {@code dias} días o que nunca han iniciado sesión.
     *
     * @param dias     número de días de inactividad para considerar un usuario inactivo.
     * @param pagina   número de página (basado en cero).
     * @param cantidad número máximo de resultados por página.
     * @return lista de usuarios inactivos.
     */
    public List<RespuestaUsuario> buscarInactivos(int dias, int pagina, int cantidad) {
        Instant umbral = Instant.now().minusSeconds((long) dias * 86400L);
        return repositorioUsuarios.buscarInactivos(umbral, pagina, cantidad).stream()
                .map(RespuestaUsuario::desde)
                .toList();
    }

    // =========================================================================
    // API pública — mutaciones
    // =========================================================================

    /**
     * Actualiza los datos personales y/o la contraseña de un usuario.
     *
     * <p>Los campos nulos o en blanco en {@code solicitud} son ignorados.
     *
     * @param id          UUID del usuario a actualizar.
     * @param solicitud   datos a actualizar; los campos nulos se omiten.
     * @param realizadoPor UUID del actor que realiza la operación (para auditoría).
     * @param ip          dirección IP del cliente (para auditoría).
     * @return representación pública del usuario actualizado.
     * @throws UsuarioNoEncontradoException si no existe un usuario activo con ese ID.
     */
    @Transactional
    public RespuestaUsuario actualizar(UUID id, SolicitudActualizarUsuario solicitud,
                                       UUID realizadoPor, String ip) {
        Usuario usuario = requerirUsuarioExistente(id);
        aplicarCambiosPerfil(usuario, solicitud);
        repositorioUsuarios.guardar(usuario);
        repositorioAuditoria.guardar(RegistroAuditoria.crear(realizadoPor, "USER_UPDATE",
                "/api/v1/users/" + id, ip));
        return RespuestaUsuario.desde(usuario);
    }

    /**
     * Cambia el rol de seguridad de un usuario.
     *
     * @param idObjetivo UUID del usuario cuyo rol se cambia.
     * @param nuevoRol   nuevo rol RBAC a asignar.
     * @param idAdmin    UUID del administrador que realiza el cambio (para auditoría).
     * @param ip         dirección IP del cliente (para auditoría).
     * @throws UsuarioNoEncontradoException si no existe un usuario activo con ese ID.
     */
    @Transactional
    public void cambiarRol(UUID idObjetivo, Rol nuevoRol, UUID idAdmin, String ip) {
        Usuario usuario = requerirUsuarioExistente(idObjetivo);
        usuario.establecerRol(nuevoRol);
        repositorioUsuarios.guardar(usuario);
        repositorioAuditoria.guardar(RegistroAuditoria.crear(idAdmin, "ROLE_CHANGE",
                "/api/v1/users/" + idObjetivo + "/role", ip));
    }

    /**
     * Crea un nuevo usuario con rol configurable desde el panel de administración.
     *
     * <p>Al finalizar publica un evento de analítica y envía el correo de
     * bienvenida al nuevo usuario.
     *
     * @param solicitud datos del nuevo usuario incluyendo rol opcional.
     * @param idAdmin   UUID del administrador que crea el usuario (para auditoría).
     * @param ip        dirección IP del cliente (para auditoría).
     * @return representación pública del usuario creado.
     * @throws BadRequestException si el correo ya está registrado o el rol es inválido.
     */
    @Transactional
    public RespuestaUsuario crearDesdeAdmin(SolicitudCrearUsuario solicitud,
                                            UUID idAdmin, String ip) {
        validarPrecondicionesAdmin(solicitud);
        Rol rol = resolverRol(solicitud.rol());
        Usuario usuario = construirUsuarioAdmin(solicitud, rol);
        repositorioUsuarios.guardar(usuario);
        repositorioAuditoria.guardar(RegistroAuditoria.crear(idAdmin, "ADMIN_CREATE_USER",
                "/api/v1/users", ip));
        publicarEventosCreacion(usuario);
        return RespuestaUsuario.desde(usuario);
    }

    /**
     * Realiza el borrado lógico de un usuario desactivando su cuenta.
     *
     * @param id      UUID del usuario a eliminar.
     * @param idAdmin UUID del administrador que solicita la eliminación.
     * @param ip      dirección IP del cliente (para auditoría).
     * @throws UsuarioNoEncontradoException si no existe un usuario activo con ese ID.
     */
    @Transactional
    public void eliminar(UUID id, UUID idAdmin, String ip) {
        Usuario usuario = requerirUsuarioExistente(id);
        usuario.eliminarLogicamente();
        repositorioUsuarios.guardar(usuario);
        repositorioAuditoria.guardar(RegistroAuditoria.crear(idAdmin, "USER_DELETE",
                "/api/v1/users/" + id, ip));
    }

    // =========================================================================
    // Helpers privados — actualizar
    // =========================================================================

    /**
     * Aplica los cambios de perfil no nulos de la solicitud sobre la entidad usuario.
     *
     * @param usuario   entidad a modificar.
     * @param solicitud datos opcionales de actualización.
     */
    private void aplicarCambiosPerfil(Usuario usuario, SolicitudActualizarUsuario solicitud) {
        if (solicitud.nombre() != null && !solicitud.nombre().isBlank()) {
            usuario.establecerNombre(solicitud.nombre().trim());
        }
        if (solicitud.apellido() != null && !solicitud.apellido().isBlank()) {
            usuario.establecerApellido(solicitud.apellido().trim());
        }
        if (solicitud.nuevaContrasena() != null && !solicitud.nuevaContrasena().isBlank()) {
            usuario.establecerHashContrasena(BCrypt.hashpw(solicitud.nuevaContrasena(), BCrypt.gensalt(12)));
        }
    }

    // =========================================================================
    // Helpers privados — crearDesdeAdmin
    // =========================================================================

    /**
     * Valida que el correo no esté duplicado antes de crear el usuario.
     *
     * @param solicitud datos de la solicitud de creación administrativa.
     * @throws BadRequestException si el correo ya está registrado.
     */
    private void validarPrecondicionesAdmin(SolicitudCrearUsuario solicitud) {
        if (repositorioUsuarios.existePorCorreo(solicitud.email())) {
            throw new BadRequestException("El correo electrónico ya está registrado.");
        }
    }

    /**
     * Resuelve el rol a partir del nombre de cadena proporcionado.
     *
     * @param nombreRol nombre del rol en cualquier combinación de mayúsculas/minúsculas;
     *                  si es {@code null} se usa {@code STUDENT}.
     * @return rol RBAC resuelto.
     * @throws BadRequestException si el nombre no corresponde a ningún rol válido.
     */
    private Rol resolverRol(String nombreRol) {
        try {
            return nombreRol != null
                    ? Rol.valueOf(nombreRol.toUpperCase())
                    : Rol.STUDENT;
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Rol inválido: " + nombreRol);
        }
    }

    /**
     * Construye la entidad {@link Usuario} con los datos de la solicitud administrativa.
     *
     * @param solicitud datos del formulario de creación administrativa.
     * @param rol       rol RBAC ya resuelto y validado.
     * @return nueva entidad de usuario sin persistir.
     */
    private Usuario construirUsuarioAdmin(SolicitudCrearUsuario solicitud, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.establecerCorreo(solicitud.email().toLowerCase().trim());
        usuario.establecerHashContrasena(BCrypt.hashpw(solicitud.contrasena(), BCrypt.gensalt(12)));
        usuario.establecerNombre(solicitud.nombre().trim());
        usuario.establecerApellido(solicitud.apellido().trim());
        usuario.establecerRol(rol);
        usuario.establecerConsentimientoDado(solicitud.consentimientoDado());
        if (solicitud.consentimientoDado()) {
            usuario.establecerFechaConsentimiento(Instant.now());
        }
        return usuario;
    }

    /**
     * Publica los eventos de analítica y correo de bienvenida tras la creación
     * administrativa de un usuario.
     *
     * @param usuario usuario recién persistido.
     */
    private void publicarEventosCreacion(Usuario usuario) {
        publicadorEventos.publicarAnaliticas(new EventoUsuarioRegistrado(
                usuario.getId().toString(), usuario.obtenerCorreo(),
                usuario.obtenerNombre(), usuario.obtenerApellido(),
                usuario.obtenerRol().name()));

        publicadorEventos.publicarCorreo(new EventoNotificacionCorreo(
                usuario.obtenerCorreo(), usuario.obtenerNombre(),
                EventoNotificacionCorreo.TipoCorreo.WELCOME,
                Map.of("firstName", usuario.obtenerNombre())));
    }

    // =========================================================================
    // Helpers privados — compartidos
    // =========================================================================

    /**
     * Localiza un usuario activo por ID o lanza excepción si no existe.
     *
     * @param id UUID del usuario a buscar.
     * @return entidad {@link Usuario} activa.
     * @throws UsuarioNoEncontradoException si no existe un usuario activo con ese ID.
     */
    private Usuario requerirUsuarioExistente(UUID id) {
        return repositorioUsuarios.buscarPorId(id)
                .orElseThrow(() ->
                        new UsuarioNoEncontradoException("Usuario no encontrado: " + id));
    }
}
