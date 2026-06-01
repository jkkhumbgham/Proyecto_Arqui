package com.puj.seguridad.jwt;

import com.puj.seguridad.listanegra.ServicioListaNegra;
import com.puj.seguridad.rbac.Rol;
import com.puj.seguridad.rbac.UsuarioAutenticado;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.time.Instant;

/**
 * Filtro JAX-RS que valida el JWT Bearer de cada solicitud entrante.
 *
 * <p>Se ejecuta antes de los interceptores RBAC. Si el header
 * {@code Authorization: Bearer <token>} está ausente, el filtro no actúa
 * (la solicitud puede ser pública). Si está presente pero inválido, aborta
 * con HTTP 401.</p>
 *
 * <p>Flujo de validación:</p>
 * <ol>
 *   <li>Verifica firma RS256 y expiración ({@link ProveedorJwt#validarToken(String)})</li>
 *   <li>Verifica que el {@code jti} no esté en la lista negra Redis</li>
 *   <li>Verifica que el token no haya expirado (doble check)</li>
 *   <li>Puebla {@link UsuarioAutenticado} en el scope de la solicitud</li>
 * </ol>
 *
 * @author Plataforma PUJ
 * @since 1.0
 * @see ProveedorJwt
 * @see ServicioListaNegra
 * @see UsuarioAutenticado
 */
@Provider
public class FiltroJwt implements ContainerRequestFilter {

    @Inject private ProveedorJwt       proveedorJwt;
    @Inject private ServicioListaNegra servicioListaNegra;
    @Inject private UsuarioAutenticado usuarioAutenticado;

    /**
     * Intercepta cada solicitud HTTP para validar el JWT si está presente.
     *
     * @param ctx contexto de la solicitud JAX-RS; se aborta con 401 si el token es inválido
     */
    @Override
    public void filter(ContainerRequestContext ctx) {
        String authHeader = ctx.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }

        String token = authHeader.substring(7);
        try {
            ReclamosJwt reclamos = proveedorJwt.validarToken(token);

            if (servicioListaNegra.estaEnListaNegra(reclamos.jti())) {
                abortar(ctx, "TOKEN_REVOKED", "El token ha sido revocado");
                return;
            }

            if (reclamos.expiraEn().isBefore(Instant.now())) {
                abortar(ctx, "TOKEN_EXPIRED", "El token ha expirado");
                return;
            }

            usuarioAutenticado.poblar(
                    reclamos.idUsuario(),
                    reclamos.correo(),
                    Rol.valueOf(reclamos.rol()),
                    token
            );

        } catch (ProveedorJwt.ExcepcionValidacionToken e) {
            abortar(ctx, e.obtenerCodigo(), e.getMessage());
        } catch (Exception e) {
            abortar(ctx, "INVALID_TOKEN", "Token inválido");
        }
    }

    /**
     * Aborta la solicitud con HTTP 401 y un cuerpo JSON estructurado.
     *
     * @param ctx     contexto de la solicitud a abortar
     * @param codigo  código de error legible por máquina
     * @param mensaje mensaje legible por humanos
     */
    private void abortar(ContainerRequestContext ctx, String codigo, String mensaje) {
        ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .type(MediaType.APPLICATION_JSON)
                .entity(String.format(
                        "{\"error\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                        codigo, mensaje, Instant.now()))
                .build());
    }
}
