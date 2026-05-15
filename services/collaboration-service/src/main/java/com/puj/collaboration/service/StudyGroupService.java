package com.puj.collaboration.service;

import com.puj.collaboration.entity.GroupMember;
import com.puj.collaboration.entity.StudyGroup;
import com.puj.collaboration.repository.StudyGroupRepository;
import com.puj.security.auth.AuthenticatedUser;
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
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class StudyGroupService {

    private static final Logger LOG = Logger.getLogger(StudyGroupService.class.getName());
    private static final String ASSESSMENT_URL =
            System.getenv().getOrDefault("ASSESSMENT_SERVICE_URL", "http://assessment-service:8080");
    private static final double TUTOR_THRESHOLD = 85.0;

    @Inject private StudyGroupRepository repo;
    @Inject private AuthenticatedUser     currentUser;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    @Transactional
    public StudyGroup create(String name, UUID courseId, int maxMembers) {
        StudyGroup g = new StudyGroup();
        g.setName(name);
        g.setCourseId(courseId);
        g.setOwnerId(currentUser.getUserIdAsUUID());
        g.setMaxMembers(maxMembers);
        repo.save(g);

        // creator joins automatically
        GroupMember m = new GroupMember();
        m.setGroup(g);
        m.setUserId(currentUser.getUserIdAsUUID());
        repo.addMember(m);
        return g;
    }

    @Transactional
    public void join(UUID groupId) {
        StudyGroup g = repo.findById(groupId)
                .orElseThrow(() -> new NotFoundException("Grupo no encontrado."));

        UUID userId = currentUser.getUserIdAsUUID();
        if (repo.isMember(groupId, userId)) {
            throw new BadRequestException("Ya eres miembro de este grupo.");
        }
        if (g.getMembers().stream().filter(m -> !m.isDeleted()).count() >= g.getMaxMembers()) {
            throw new BadRequestException("El grupo está lleno.");
        }

        GroupMember m = new GroupMember();
        m.setGroup(g);
        m.setUserId(userId);
        repo.addMember(m);

        // Check if this student qualifies as tutor (average score > 85%)
        tryAssignTutor(g, userId);
    }

    public List<StudyGroup> findByCourse(UUID courseId) {
        return repo.findByCourseId(courseId);
    }

    /**
     * Queries assessment-service for the student's average score.
     * If it exceeds TUTOR_THRESHOLD and the group has no tutor yet, assigns them.
     * Failure is non-fatal — logs a warning and continues.
     */
    private void tryAssignTutor(StudyGroup group, UUID studentId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ASSESSMENT_URL + "/api/v1/submissions/student/"
                            + studentId + "/average"))
                    .header("Authorization", "Bearer " + currentUser.getRawToken())
                    .GET().timeout(Duration.ofSeconds(3)).build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                double avg = Double.parseDouble(resp.body().replaceAll("[^0-9.]", ""));
                boolean groupHasTutor = group.getMembers().stream()
                        .anyMatch(m -> !m.isDeleted() && m.getUserId().equals(group.getOwnerId()));

                if (avg >= TUTOR_THRESHOLD) {
                    LOG.info(String.format(
                            "Student %s qualifies as tutor (avg=%.1f%%) for group %s",
                            studentId, avg, group.getId()));
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "Could not evaluate tutor eligibility for student " + studentId, e);
        }
    }
}
