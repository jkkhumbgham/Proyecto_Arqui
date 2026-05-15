package com.puj.assessments.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.puj.assessments.adaptive.AdaptiveEngine;
import com.puj.assessments.adaptive.AdaptiveRecommendation;
import com.puj.assessments.dto.SubmitRequest;
import com.puj.assessments.dto.SubmissionResult;
import com.puj.assessments.entity.*;
import com.puj.assessments.repository.AssessmentRepository;
import com.puj.assessments.repository.SubmissionRepository;
import com.puj.events.AssessmentSubmittedEvent;
import com.puj.events.publisher.EventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class SubmissionService {

    @Inject private SubmissionRepository submissionRepo;
    @Inject private AssessmentRepository assessmentRepo;
    @Inject private AdaptiveEngine       adaptiveEngine;
    @Inject private EventPublisher       eventPublisher;

    private final ObjectMapper mapper = new ObjectMapper();

    @Transactional
    public SubmissionResult submit(UUID userId, UUID assessmentId, SubmitRequest req) {
        Assessment assessment = assessmentRepo.findById(assessmentId)
                .orElseThrow(() -> new NotFoundException("Evaluación no encontrada."));

        long attempts = assessmentRepo.countAttempts(userId, assessmentId);
        if (attempts >= assessment.getMaxAttempts()) {
            throw new BadRequestException("Has alcanzado el número máximo de intentos (" + assessment.getMaxAttempts() + ").");
        }

        Submission submission = new Submission();
        submission.setUserId(userId);
        submission.setAssessment(assessment);
        submission.setAttemptNumber((int) attempts + 1);
        submission.setStatus(SubmissionStatus.SUBMITTED);
        submission.setSubmittedAt(Instant.now());
        submission.setDurationSeconds(req.durationSeconds());

        try {
            submission.setAnswersJson(mapper.writeValueAsString(req.answers()));
        } catch (Exception e) {
            submission.setAnswersJson("{}");
        }

        GradeResult grade = grade(assessment, req.answers());
        submission.setScore(grade.score());
        submission.setMaxScore(grade.maxScore());
        submission.setPassed(grade.passed());
        submission.setStatus(SubmissionStatus.GRADED);
        submission.setGradedAt(Instant.now());
        submissionRepo.save(submission);

        AssessmentSubmittedEvent event = new AssessmentSubmittedEvent(
                submission.getId().toString(), userId.toString(),
                assessmentId.toString(), assessment.getCourseId().toString(),
                assessment.getLessonId() != null ? assessment.getLessonId().toString() : null,
                grade.score(), grade.maxScore(), grade.passed(), req.durationSeconds()
        );
        eventPublisher.publishAnalytics(event);

        Optional<AdaptiveRecommendation> recommendation =
                adaptiveEngine.evaluate(assessmentId, userId, grade.score(), grade.maxScore());

        return new SubmissionResult(
                submission.getId(), grade.score(), grade.maxScore(),
                grade.passed(), grade.scorePct(), recommendation.orElse(null)
        );
    }

    private GradeResult grade(Assessment assessment, Map<String, List<String>> answers) {
        double totalPoints  = 0.0;
        double earnedPoints = 0.0;

        for (Question q : assessment.getQuestions()) {
            if (q.isDeleted()) continue;
            totalPoints += q.getPoints();
            List<String> given = answers.getOrDefault(q.getId().toString(), List.of());

            Set<String> correctIds = new HashSet<>();
            for (AnswerOption opt : q.getOptions()) {
                if (opt.isCorrect()) correctIds.add(opt.getId().toString());
            }

            if (q.getType() == QuestionType.SINGLE_CHOICE || q.getType() == QuestionType.TRUE_FALSE) {
                if (given.size() == 1 && correctIds.contains(given.get(0))) {
                    earnedPoints += q.getPoints();
                }
            } else if (q.getType() == QuestionType.MULTIPLE_CHOICE) {
                Set<String> givenSet = new HashSet<>(given);
                if (givenSet.equals(correctIds)) {
                    earnedPoints += q.getPoints();
                }
            }
            // SHORT_ANSWER: requiere revisión manual — no se puntúa automáticamente
        }

        BigDecimal score   = BigDecimal.valueOf(earnedPoints).setScale(2, RoundingMode.HALF_UP);
        BigDecimal maxScore = BigDecimal.valueOf(totalPoints).setScale(2, RoundingMode.HALF_UP);
        double scorePct    = totalPoints > 0 ? (earnedPoints / totalPoints) * 100.0 : 0.0;
        boolean passed     = scorePct >= assessment.getPassingScorePct();

        return new GradeResult(score, maxScore, scorePct, passed);
    }

    private record GradeResult(BigDecimal score, BigDecimal maxScore, double scorePct, boolean passed) {}
}
