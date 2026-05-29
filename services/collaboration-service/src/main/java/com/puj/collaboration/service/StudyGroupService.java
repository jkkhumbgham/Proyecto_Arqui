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

@ApplicationScoped
public class StudyGroupService {

    private static final Logger LOG = Logger.getLogger(StudyGroupService.class.getName());
    private static final String ASSESSMENT_URL =
            System.getenv().getOrDefault("ASSESSMENT_SERVICE_URL", "http://assessment-service:8080");

    @Inject private StudyGroupRepository repo;
    @Inject private AuthenticatedUser    currentUser;
    @Inject private CircuitBreaker       assessmentCircuitBreaker;

    private final HttpClient   http   = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();

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
        m.setTutor(true); // creator is initial tutor
        repo.addMember(m);
        return g;
    }

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

        // Restore a previous soft-deleted membership instead of inserting a new one
        // (avoids unique constraint violation on group_id + user_id)
        GroupMember m = repo.findMemberByGroupAndUserAny(groupId, userId).orElse(null);
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

        tryAssignTutor(g, userId);
    }

    @Transactional
    public void leave(UUID groupId) {
        UUID userId = UUID.fromString(currentUser.getUserId());
        GroupMember member = repo.findMemberByGroupAndUser(groupId, userId)
                .orElseThrow(() -> new BadRequestException("No eres miembro de este grupo."));
        member.softDelete();
        repo.mergeMember(member);

        if (member.isTutor()) {
            StudyGroup g = repo.findById(groupId).orElse(null);
            if (g != null) tryAssignTutor(g, userId);
        }
    }

    public List<StudyGroup> findByCourse(UUID courseId) {
        return repo.findByCourseId(courseId);
    }

    /**
     * Elects the member with the highest avg score across all course assessments as tutor.
     * Failure is non-fatal. Circuit Breaker (RNF-04b) prevents cascading timeouts
     * when assessment-service is unavailable.
     */
    private void tryAssignTutor(StudyGroup group, UUID ignoredUserId) {
        if (!assessmentCircuitBreaker.allowRequest()) {
            LOG.warning("[CircuitBreaker] Assessment-service unavailable — skipping tutor election for group "
                    + group.getId());
            return;
        }
        try {
            // 1. Get assessment IDs for this course
            HttpRequest assessReq = HttpRequest.newBuilder()
                    .uri(URI.create(ASSESSMENT_URL + "/api/v1/assessments/course/" + group.getCourseId()))
                    .header("Authorization", "Bearer " + currentUser.getRawToken())
                    .GET().timeout(Duration.ofSeconds(3)).build();
            HttpResponse<String> assessResp = http.send(assessReq, HttpResponse.BodyHandlers.ofString());
            if (assessResp.statusCode() != 200) {
                assessmentCircuitBreaker.recordFailure();
                return;
            }

            List<String> ids = new ArrayList<>();
            mapper.readTree(assessResp.body()).forEach(n -> {
                String id = n.path("id").asText();
                if (!id.isBlank()) ids.add(id);
            });
            if (ids.isEmpty()) {
                assessmentCircuitBreaker.recordSuccess();
                return;
            }
            String assessmentIds = String.join(",", ids);

            // 2. Score each active member
            List<GroupMember> members = repo.findActiveMembers(group.getId());
            UUID   bestMember = null;
            double bestScore  = -1.0;

            for (GroupMember m : members) {
                try {
                    HttpRequest avgReq = HttpRequest.newBuilder()
                            .uri(URI.create(ASSESSMENT_URL + "/api/v1/submissions/avg-for-assessments?userId="
                                    + m.getUserId() + "&assessmentIds=" + assessmentIds))
                            .header("Authorization", "Bearer " + currentUser.getRawToken())
                            .GET().timeout(Duration.ofSeconds(3)).build();
                    HttpResponse<String> avgResp = http.send(avgReq, HttpResponse.BodyHandlers.ofString());
                    if (avgResp.statusCode() == 200) {
                        JsonNode node = mapper.readTree(avgResp.body());
                        double avg = node.path("avgScorePct").asDouble(-1.0);
                        if (avg > bestScore) {
                            bestScore = avg;
                            bestMember = m.getUserId();
                        }
                    }
                } catch (Exception ignored) {}
            }

            assessmentCircuitBreaker.recordSuccess();

            // 3. Update isTutor for all members
            if (bestMember != null) {
                final UUID tutorId = bestMember;
                for (GroupMember m : members) {
                    boolean shouldBeTutor = m.getUserId().equals(tutorId);
                    if (m.isTutor() != shouldBeTutor) {
                        m.setTutor(shouldBeTutor);
                        repo.mergeMember(m);
                    }
                }
            }
        } catch (Exception e) {
            assessmentCircuitBreaker.recordFailure();
            LOG.log(Level.WARNING, "Could not elect tutor for group " + group.getId(), e);
        }
    }
}
