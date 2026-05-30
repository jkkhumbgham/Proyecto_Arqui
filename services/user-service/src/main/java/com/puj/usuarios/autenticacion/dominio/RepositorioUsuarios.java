package com.puj.usuarios.autenticacion.dominio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato de repositorio para la entidad {@link Usuario}.
 *
 * <p>Define las operaciones de acceso a datos sin acoplar la capa de dominio
 * a una tecnología de persistencia concreta.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
public interface RepositorioUsuarios {

    /**
     * Persiste o actualiza un usuario en el almacén de datos.
     *
     * @param usuario entidad a guardar.
     * @return la entidad gestionada resultante.
     */
    Usuario guardar(Usuario usuario);

    /**
     * Busca un usuario activo por su identificador único.
     *
     * @param id UUID del usuario.
     * @return {@link Optional} con el usuario si existe y no ha sido eliminado.
     */
    Optional<Usuario> buscarPorId(UUID id);

    /**
     * Busca un usuario activo por su correo electrónico.
     *
     * @param correo dirección de correo electrónico a buscar.
     * @return {@link Optional} con el usuario si existe y no ha sido eliminado.
     */
    Optional<Usuario> buscarPorCorreo(String correo);

    /**
     * Devuelve una página de usuarios activos.
     *
     * @param pagina  número de página (basado en cero).
     * @param cantidad número máximo de resultados por página.
     * @return lista de usuarios de la página solicitada.
     */
    List<Usuario> buscarTodos(int pagina, int cantidad);

    /**
     * Cuenta el total de usuarios activos.
     *
     * @return número total de usuarios activos.
     */
    long contarTodos();

    /**
     * Comprueba si ya existe un usuario activo con el correo electrónico dado.
     *
     * @param correo dirección de correo electrónico a verificar.
     * @return {@code true} si ya existe un usuario activo con ese correo.
     */
    boolean existePorCorreo(String correo);

    /**
     * Devuelve una página de usuarios inactivos anteriores al umbral indicado.
     *
     * @param umbral   instante límite de actividad.
     * @param pagina   número de página (basado en cero).
     * @param cantidad número máximo de resultados por página.
     * @return lista de usuarios inactivos.
     */
    List<Usuario> buscarInactivos(Instant umbral, int pagina, int cantidad);
}
