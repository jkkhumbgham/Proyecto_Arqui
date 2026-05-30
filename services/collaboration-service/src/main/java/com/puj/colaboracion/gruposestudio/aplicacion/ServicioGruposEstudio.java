package com.puj.colaboracion.gruposestudio.aplicacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puj.colaboracion.aspectos.registro.Registrable;
import com.puj.colaboracion.gruposestudio.dominio.GrupoEstudio;
import com.puj.colaboracion.gruposestudio.dominio.MiembroGrupo;
import com.puj.colaboracion.gruposestudio.dominio.RepositorioGruposEstudio;
import com.puj.colaboracion.resiliencia.DisyuntorCircuito;
import com.puj.seguridad.rbac.UsuarioAutenticado;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Servicio de negocio para la gestión de grupos de estudio.
 *
 * <p>Gestiona el ciclo de vida de los grupos (creación, unión y abandono) y
 * orquesta la reelección del tutor del grupo mediante una llamada HTTP al
 * servicio de evaluaciones, protegida con un {@link DisyuntorCircuito} (RNF-04b).</p>
 *
 * <p>La elección del tutor se basa en el promedio de puntaje de cada miembro
 * en las evaluaciones del curso. Si el servicio de evaluaciones no está
 * disponible, la operación continúa sin asignar tutor.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
@Registrable
public class ServicioGruposEstudio {

    private static final String URL_SERVICIO_EVALUACIONES =
            System.getenv().getOrDefault(
                    "ASSESSMENT_SERVICE_URL", "http://assessment-service:8080");

    @Inject private RepositorioGruposEstudio repo;
    @Inject private UsuarioAutenticado        usuarioActual;
    @Inject private DisyuntorCircuito        disyuntorEvaluaciones;

    private final HttpClient   clienteHttp =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapeador    = new ObjectMapper();

    /**
     * Crea un nuevo grupo de estudio y añade al creador como primer tutor.
     *
     * @param nombre     nombre del grupo
     * @param idCurso    UUID del curso al que pertenece el grupo
     * @param maxMiembros número máximo de miembros activos
     * @return el grupo creado y persistido
     */
    @Transactional
    public GrupoEstudio crear(String nombre, UUID idCurso, int maxMiembros) {
        GrupoEstudio grupo = new GrupoEstudio();
        grupo.setNombre(nombre);
        grupo.setIdCurso(idCurso);
        grupo.setCreadoPor(UUID.fromString(usuarioActual.obtenerIdUsuario()));
        grupo.setMaxMiembros(maxMiembros);
        repo.guardar(grupo);

        MiembroGrupo miembro = new MiembroGrupo();
        miembro.setGrupo(grupo);
        miembro.setIdUsuario(UUID.fromString(usuarioActual.obtenerIdUsuario()));
        miembro.setTutor(true); // el creador es el tutor inicial
        repo.agregarMiembro(miembro);
        return grupo;
    }

    /**
     * Une al usuario autenticado al grupo de estudio indicado.
     *
     * <p>Si el usuario tuvo membresía previa (soft-deleted), la restaura para
     * evitar violar la restricción única {@code uq_group_member}. Tras unirse,
     * intenta reelegir al tutor del grupo.</p>
     *
     * @param idGrupo UUID del grupo al que unirse
     * @throws NotFoundException   si el grupo no existe o está eliminado
     * @throws BadRequestException si el usuario ya es miembro o el grupo está lleno
     */
    @Transactional
    public void unirse(UUID idGrupo) {
        GrupoEstudio grupo = repo.buscarPorId(idGrupo)
                .orElseThrow(() -> new NotFoundException("Grupo no encontrado."));

        UUID idUsuario = UUID.fromString(usuarioActual.obtenerIdUsuario());
        if (repo.esMiembro(idGrupo, idUsuario)) {
            throw new BadRequestException("Ya eres miembro de este grupo.");
        }

        List<MiembroGrupo> miembrosActuales = repo.buscarMiembrosActivos(idGrupo);
        if (miembrosActuales.size() >= grupo.getMaxMiembros()) {
            throw new BadRequestException("El grupo está lleno.");
        }

        restaurarOCrearMembresia(grupo, idGrupo, idUsuario);
        intentarAsignarTutor(grupo, idUsuario);
    }

    /**
     * Retira al usuario autenticado del grupo de estudio.
     *
     * <p>Si el miembro era tutor, intenta reelegir un nuevo tutor entre los
     * miembros restantes.</p>
     *
     * @param idGrupo UUID del grupo a abandonar
     * @throws BadRequestException si el usuario no es miembro activo del grupo
     */
    @Transactional
    public void abandonar(UUID idGrupo) {
        UUID idUsuario = UUID.fromString(usuarioActual.obtenerIdUsuario());
        MiembroGrupo miembro =
                repo.buscarMiembroPorGrupoYUsuario(idGrupo, idUsuario)
                .orElseThrow(() -> new BadRequestException(
                        "No eres miembro de este grupo."));
        miembro.eliminarLogicamente();
        repo.fusionarMiembro(miembro);

        if (miembro.esTutor()) {
            GrupoEstudio grupo = repo.buscarPorId(idGrupo).orElse(null);
            if (grupo != null) intentarAsignarTutor(grupo, idUsuario);
        }
    }

    /**
     * Retorna los grupos de estudio activos de un curso.
     *
     * @param idCurso UUID del curso
     * @return lista de grupos activos del curso (puede estar vacía)
     */
    public List<GrupoEstudio> buscarPorCurso(UUID idCurso) {
        return repo.buscarPorIdCurso(idCurso);
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * Restaura una membresía eliminada existente o crea una nueva.
     */
    private void restaurarOCrearMembresia(
            GrupoEstudio grupo, UUID idGrupo, UUID idUsuario) {

        MiembroGrupo miembro =
                repo.buscarMiembroPorGrupoYUsuarioCualquiera(idGrupo, idUsuario)
                .orElse(null);
        if (miembro != null) {
            miembro.setTutor(false);
            miembro.restaurar();
            repo.fusionarMiembro(miembro);
        } else {
            miembro = new MiembroGrupo();
            miembro.setGrupo(grupo);
            miembro.setIdUsuario(idUsuario);
            repo.agregarMiembro(miembro);
        }
    }

    /**
     * Elige al miembro con mayor promedio de puntaje como tutor del grupo.
     *
     * <p>La falla es no-fatal: si el servicio de evaluaciones no está disponible
     * o el disyuntor está abierto, se omite la reelección sin lanzar excepción.
     * Implementa RNF-04b (aislamiento de fallos).</p>
     */
    private void intentarAsignarTutor(GrupoEstudio grupo, UUID idUsuarioIgnorado) {
        if (!disyuntorEvaluaciones.permitirPeticion()) {
            return;
        }
        try {
            List<String> idsEvaluaciones =
                    obtenerIdsEvaluaciones(grupo.getIdCurso());
            if (idsEvaluaciones.isEmpty()) {
                disyuntorEvaluaciones.registrarExito();
                return;
            }

            UUID mejorMiembro = elegirMejorMiembro(grupo, idsEvaluaciones);
            disyuntorEvaluaciones.registrarExito();

            if (mejorMiembro != null) {
                actualizarMarcasTutor(grupo, mejorMiembro);
            }
        } catch (Exception e) {
            disyuntorEvaluaciones.registrarFalla();
            java.util.logging.Logger
                    .getLogger(getClass().getName())
                    .log(Level.WARNING,
                            "No se pudo elegir tutor para el grupo " + grupo.getId(), e);
        }
    }

    private List<String> obtenerIdsEvaluaciones(UUID idCurso) throws Exception {
        HttpRequest solicitud = HttpRequest.newBuilder()
                .uri(URI.create(URL_SERVICIO_EVALUACIONES
                        + "/api/v1/assessments/course/" + idCurso))
                .header("Authorization", "Bearer " + usuarioActual.obtenerTokenBruto())
                .GET().timeout(Duration.ofSeconds(3)).build();
        HttpResponse<String> respuesta =
                clienteHttp.send(solicitud, HttpResponse.BodyHandlers.ofString());

        if (respuesta.statusCode() != 200) {
            disyuntorEvaluaciones.registrarFalla();
            return List.of();
        }

        List<String> ids = new ArrayList<>();
        mapeador.readTree(respuesta.body()).forEach(nodo -> {
            String id = nodo.path("id").asText();
            if (!id.isBlank()) ids.add(id);
        });
        return ids;
    }

    private UUID elegirMejorMiembro(
            GrupoEstudio grupo, List<String> idsEvaluaciones) throws Exception {

        String idsUnidos = String.join(",", idsEvaluaciones);
        List<MiembroGrupo> miembros = repo.buscarMiembrosActivos(grupo.getId());
        UUID   mejorMiembro = null;
        double mejorPuntaje = -1.0;

        for (MiembroGrupo m : miembros) {
            double promedio = obtenerPromedioPuntaje(m.getIdUsuario(), idsUnidos);
            if (promedio > mejorPuntaje) {
                mejorPuntaje = promedio;
                mejorMiembro = m.getIdUsuario();
            }
        }
        return mejorMiembro;
    }

    private double obtenerPromedioPuntaje(UUID idUsuario, String idsEvaluaciones) {
        try {
            HttpRequest solicitudPromedio = HttpRequest.newBuilder()
                    .uri(URI.create(URL_SERVICIO_EVALUACIONES
                            + "/api/v1/submissions/avg-for-assessments"
                            + "?userId=" + idUsuario
                            + "&assessmentIds=" + idsEvaluaciones))
                    .header("Authorization", "Bearer " + usuarioActual.obtenerTokenBruto())
                    .GET().timeout(Duration.ofSeconds(3)).build();
            HttpResponse<String> respuestaPromedio =
                    clienteHttp.send(solicitudPromedio,
                            HttpResponse.BodyHandlers.ofString());
            if (respuestaPromedio.statusCode() == 200) {
                JsonNode nodo = mapeador.readTree(respuestaPromedio.body());
                return nodo.path("avgScorePct").asDouble(-1.0);
            }
        } catch (Exception ignorado) {}
        return -1.0;
    }

    private void actualizarMarcasTutor(GrupoEstudio grupo, UUID idTutor) {
        List<MiembroGrupo> miembros = repo.buscarMiembrosActivos(grupo.getId());
        for (MiembroGrupo m : miembros) {
            boolean deberiaSerTutor = m.getIdUsuario().equals(idTutor);
            if (m.esTutor() != deberiaSerTutor) {
                m.setTutor(deberiaSerTutor);
                repo.fusionarMiembro(m);
            }
        }
    }
}
