package com.puj.collaboration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puj.collaboration.entity.GroupMember;
import com.puj.collaboration.entity.StudyGroup;
import com.puj.collaboration.repository.StudyGroupRepository;
import com.puj.collaboration.resilience.CircuitBreaker;
import com.puj.security.rbac.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
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
import java.util.logging.Logger;

/**
 * Servicio de negocio para la gestión de grupos de estudio.
 *
 * <p>Gestiona el ciclo de vida de los grupos (creación, unión y abandono) y
 * orquesta la reelección del tutor del grupo mediante una llamada HTTP al
 * servicio de evaluaciones, protegida con un {@link CircuitBreaker} (RNF-04b).</p>
 *
 * <p>La elección del tutor se basa en el promedio de puntaje de cada miembro
 * en las evaluaciones del curso. Si el servicio de evaluaciones no está
 * disponible, la operación continúa sin asignar tutor.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class StudyGroupService {

    private static final Logger LOG = Logger.getLogger(StudyGroupService.class.getName());
    private static final String ASSESSMENT_URL =
            System.getenv().getOrDefault(
                    "ASSESSMENT_SERVICE_URL", "http://assessment-service:8080");

    @Inject private StudyGroupRepository repo;
    @Inject private AuthenticatedUser    currentUser;
    @Inject private CircuitBreaker       assessmentCircuitBreaker;

    private final HttpClient   http   =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Crea un nuevo grupo de estudio y añade al creador como primer tutor.
     *
     * @param name       nombre del grupo
     * @param courseId   UUID del curso al que pertenece el grupo
     * @param maxMembers número máximo de miembros activos
     * @return el grupo creado y persistido
     */
    @Transactional
    public StudyGroup create(String name, UUID courseId, int maxMembers) {
        StudyGroup g = new StudyGroup();
        g.setName(name);
        g.setCourseId(courseId);
        g.setOwnerId(UUID.fromString(currentUser.getUserId()));
        g.setMaxMembers(maxMembers);
        repo.save(g);

        GroupMember m = new GroupMember();
        m.setGroup(g);
        m.setUserId(UUID.fromString(currentUser.getUserId()));
        m.setTutor(true); // el creador es el tutor inicial
        repo.addMember(m);
        return g;
    }

    /**
     * Une al usuario autenticado al grupo de estudio indicado.
     *
     * <p>Si el usuario tuvo membresía previa (soft-deleted), la restaura para
     * evitar violar la restricción única {@code uq_group_member}. Tras unirse,
     * intenta reelegir al tutor del grupo.</p>
     *
     * @param groupId UUID del grupo al que unirse
     * @throws NotFoundException   si el grupo no existe o está eliminado
     * @throws BadRequestException si el usuario ya es miembro o el grupo está lleno
     */
    @Transactional
    public void join(UUID groupId) {
        StudyGroup g = repo.findById(groupId)
                .orElseThrow(() -> new NotFoundException("Grupo no encontrado."));

        UUID userId = UUID.fromString(currentUser.getUserId());
        if (repo.isMember(groupId, userId)) {
            throw new BadRequestException("Ya eres miembro de este grupo.");
        }

        List<GroupMember> currentMembers = repo.findActiveMembers(groupId);
        if (currentMembers.size() >= g.getMaxMembers()) {
            throw new BadRequestException("El grupo está lleno.");
        }

        restoreOrCreateMembership(g, groupId, userId);
        tryAssignTutor(g, userId);
    }

    /**
     * Retira al usuario autenticado del grupo de estudio.
     *
     * <p>Si el miembro era tutor, intenta reelegir un nuevo tutor entre los
     * miembros restantes.</p>
     *
     * @param groupId UUID del grupo a abandonar
     * @throws BadRequestException si el usuario no es miembro activo del grupo
     */
    @Transactional
    public void leave(UUID groupId) {
        UUID userId = UUID.fromString(currentUser.getUserId());
        GroupMember member = repo.findMemberByGroupAndUser(groupId, userId)
                .orElseThrow(() -> new BadRequestException(
                        "No eres miembro de este grupo."));
        member.softDelete();
        repo.mergeMember(member);

        if (member.isTutor()) {
            StudyGroup g = repo.findById(groupId).orElse(null);
            if (g != null) tryAssignTutor(g, userId);
        }
    }

    /**
     * Retorna los grupos de estudio activos de un curso.
     *
     * @param courseId UUID del curso
     * @return lista de grupos activos del curso (puede estar vacía)
     */
    public List<StudyGroup> findByCourse(UUID courseId) {
        return repo.findByCourseId(courseId);
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * Restaura una membresía eliminada existente o crea una nueva.
     *
     * <p>Evita violar la restricción única {@code uq_group_member} al volver
     * a unirse tras haber abandonado el grupo.</p>
     *
     * @param g       grupo de estudio
     * @param groupId UUID del grupo
     * @param userId  UUID del usuario que se une
     */
    private void restoreOrCreateMembership(
            StudyGroup g, UUID groupId, UUID userId) {

        GroupMember m = repo.findMemberByGroupAndUserAny(groupId, userId)
                .orElse(null);
        if (m != null) {
            m.setTutor(false);
            m.softRestore();
            repo.mergeMember(m);
        } else {
            m = new GroupMember();
            m.setGroup(g);
            m.setUserId(userId);
            repo.addMember(m);
        }
    }

    /**
     * Elije al miembro con mayor promedio de puntaje como tutor del grupo.
     *
     * <p>La falla es no-fatal: si el servicio de evaluaciones no está disponible
     * o el circuit breaker está abierto, se omite la reelección sin lanzar
     * excepción. Implementa RNF-04b (aislamiento de fallos).</p>
     *
     * @param group         grupo de estudio cuyo tutor se reelige
     * @param ignoredUserId UUID del usuario que acaba de entrar o salir
     *                      (se incluye en la elección)
     */
    private void tryAssignTutor(StudyGroup group, UUID ignoredUserId) {
        if (!assessmentCircuitBreaker.allowRequest()) {
            LOG.warning("[CircuitBreaker] Assessment-service unavailable "
                    + "— skipping tutor election for group " + group.getId());
            return;
        }
        try {
            List<String> assessmentIds = fetchAssessmentIds(group.getCourseId());
            if (assessmentIds.isEmpty()) {
                assessmentCircuitBreaker.recordSuccess();
                return;
            }

            UUID bestMember = electBestMember(group, assessmentIds);
            assessmentCircuitBreaker.recordSuccess();

            if (bestMember != null) {
                updateTutorFlags(group, bestMember);
            }
        } catch (Exception e) {
            assessmentCircuitBreaker.recordFailure();
            LOG.log(Level.WARNING,
                    "Could not elect tutor for group " + group.getId(), e);
        }
    }

    /**
     * Consulta los IDs de las evaluaciones activas del curso al servicio de
     * evaluaciones.
     *
     * @param courseId UUID del curso
     * @return lista de UUIDs de evaluaciones como {@code String}
     * @throws Exception si la petición HTTP falla o retorna un código distinto de 200
     */
    private List<String> fetchAssessmentIds(UUID courseId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ASSESSMENT_URL
                        + "/api/v1/assessments/course/" + courseId))
                .header("Authorization", "Bearer " + currentUser.getRawToken())
                .GET().timeout(Duration.ofSeconds(3)).build();
        HttpResponse<String> resp =
                http.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() != 200) {
            assessmentCircuitBreaker.recordFailure();
            return List.of();
        }

        List<String> ids = new ArrayList<>();
        mapper.readTree(resp.body()).forEach(n -> {
            String id = n.path("id").asText();
            if (!id.isBlank()) ids.add(id);
        });
        return ids;
    }

    /**
     * Determina el miembro del grupo con mayor promedio de puntaje en las
     * evaluaciones del curso.
     *
     * @param group         grupo de estudio cuyos miembros se evalúan
     * @param assessmentIds lista de UUIDs de evaluaciones del curso
     * @return UUID del miembro con mayor promedio, o {@code null} si ninguno
     *         respondió correctamente
     * @throws Exception si alguna petición HTTP falla de forma inesperada
     */
    private UUID electBestMember(
            StudyGroup group, List<String> assessmentIds) throws Exception {

        String joinedIds = String.join(",", assessmentIds);
        List<GroupMember> members = repo.findActiveMembers(group.getId());
        UUID   bestMember = null;
        double bestScore  = -1.0;

        for (GroupMember m : members) {
            double avg = fetchAvgScore(m.getUserId(), joinedIds);
            if (avg > bestScore) {
                bestScore  = avg;
                bestMember = m.getUserId();
            }
        }
        return bestMember;
    }

    /**
     * Consulta el promedio de puntaje de un usuario en un conjunto de evaluaciones.
     *
     * @param userId        UUID del usuario
     * @param assessmentIds UUIDs de evaluaciones separados por coma
     * @return promedio de puntaje (0-100), o -1.0 si la petición falla
     */
    private double fetchAvgScore(UUID userId, String assessmentIds) {
        try {
            HttpRequest avgReq = HttpRequest.newBuilder()
                    .uri(URI.create(ASSESSMENT_URL
                            + "/api/v1/submissions/avg-for-assessments"
                            + "?userId=" + userId
                            + "&assessmentIds=" + assessmentIds))
                    .header("Authorization", "Bearer " + currentUser.getRawToken())
                    .GET().timeout(Duration.ofSeconds(3)).build();
            HttpResponse<String> avgResp =
                    http.send(avgReq, HttpResponse.BodyHandlers.ofString());
            if (avgResp.statusCode() == 200) {
                JsonNode node = mapper.readTree(avgResp.body());
                return node.path("avgScorePct").asDouble(-1.0);
            }
        } catch (Exception ignored) {}
        return -1.0;
    }

    /**
     * Actualiza el flag {@code isTutor} de todos los miembros del grupo,
     * asignando el rol únicamente al miembro elegido.
     *
     * @param group   grupo cuyos miembros se actualizan
     * @param tutorId UUID del miembro que debe ser tutor
     */
    private void updateTutorFlags(StudyGroup group, UUID tutorId) {
        List<GroupMember> members = repo.findActiveMembers(group.getId());
        for (GroupMember m : members) {
            boolean shouldBeTutor = m.getUserId().equals(tutorId);
            if (m.isTutor() != shouldBeTutor) {
                m.setTutor(shouldBeTutor);
                repo.mergeMember(m);
            }
        }
    }
}
