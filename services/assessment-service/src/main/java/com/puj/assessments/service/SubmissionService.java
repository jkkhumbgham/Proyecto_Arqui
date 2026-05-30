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

/**
 * Servicio de calificación de evaluaciones.
 *
 * <p>Gestiona dos flujos principales:</p>
 * <ol>
 *   <li>{@link #gradeExisting} — califica una submission ya creada en estado
 *       {@code IN_PROGRESS} (flujo de dos pasos: primero el estudiante inicia,
 *       luego envía respuestas).</li>
 *   <li>{@link #submit} — crea y califica la submission en un único paso
 *       (flujo legado de un paso).</li>
 * </ol>
 *
 * <p>Tras calificar, publica un {@link AssessmentSubmittedEvent} en RabbitMQ
 * y consulta el motor adaptativo para determinar si se debe recomendar
 * contenido suplementario.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class SubmissionService {

    @Inject private SubmissionRepository submissionRepo;
    @Inject private AssessmentRepository assessmentRepo;
    @Inject private AdaptiveEngine       adaptiveEngine;
    @Inject private EventPublisher       eventPublisher;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Califica una submission ya persistida en estado {@code IN_PROGRESS}.
     *
     * <p>Mide la duración real en el servidor (RNF RF-08) y la limita al tiempo
     * máximo configurado en la evaluación si existe. Publica el evento de
     * analytics y consulta el motor adaptativo al finalizar.</p>
     *
     * @param submission submission en estado {@code IN_PROGRESS} a calificar
     * @param req        respuestas y duración reportada por el cliente
     * @return resultado de la calificación con puntaje y recomendación adaptativa
     */
    @Transactional
    public SubmissionResult gradeExisting(Submission submission, SubmitRequest req) {
        Assessment assessment = submission.getAssessment();
        persistAnswersJson(submission, req.answers());

        long actualSeconds = computeActualDuration(submission, assessment);
        GradeResult grade  = grade(assessment, req.answers());

        applyGrade(submission, grade, actualSeconds, Instant.now());
        submissionRepo.save(submission);

        boolean allPassed = computeAllAssessmentsPassed(
                submission.getUserId(), assessment.getCourseId());
        publishEvent(submission, assessment, grade, req.durationSeconds(), allPassed);

        Optional<AdaptiveRecommendation> recommendation =
                adaptiveEngine.evaluate(
                        assessment.getId(), submission.getUserId(),
                        grade.score(), grade.maxScore());

        return new SubmissionResult(
                submission.getId(), grade.score(), grade.maxScore(),
                grade.passed(), grade.scorePct(), recommendation.orElse(null)
        );
    }

    /**
     * Crea y califica una submission en un único paso (flujo legado).
     *
     * <p>Verifica que el estudiante no haya superado el límite de intentos antes
     * de crear la submission.</p>
     *
     * @param userId       UUID del estudiante que realiza el intento
     * @param assessmentId UUID de la evaluación
     * @param req          respuestas y duración del intento
     * @return resultado de la calificación con puntaje y recomendación adaptativa
     * @throws NotFoundException   si la evaluación no existe
     * @throws BadRequestException si el estudiante alcanzó el límite de intentos
     */
    @Transactional
    public SubmissionResult submit(
            UUID userId,
            UUID assessmentId,
            SubmitRequest req) {

        Assessment assessment = assessmentRepo.findById(assessmentId)
                .orElseThrow(() -> new NotFoundException("Evaluación no encontrada."));

        long attempts = assessmentRepo.countAttempts(userId, assessmentId);
        if (attempts >= assessment.getMaxAttempts()) {
            throw new BadRequestException(
                    "Has alcanzado el número máximo de intentos ("
                    + assessment.getMaxAttempts() + ").");
        }

        Submission submission = buildNewSubmission(
                userId, assessment, (int) attempts + 1, req);

        GradeResult grade = grade(assessment, req.answers());
        applyGrade(submission, grade, req.durationSeconds(), Instant.now());
        submission.setStatus(SubmissionStatus.GRADED);
        submissionRepo.save(submission);

        boolean allPassed = computeAllAssessmentsPassed(userId, assessment.getCourseId());
        publishEvent(submission, assessment, grade, req.durationSeconds(), allPassed);

        Optional<AdaptiveRecommendation> recommendation =
                adaptiveEngine.evaluate(
                        assessmentId, userId, grade.score(), grade.maxScore());

        return new SubmissionResult(
                submission.getId(), grade.score(), grade.maxScore(),
                grade.passed(), grade.scorePct(), recommendation.orElse(null)
        );
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * Construye una nueva {@link Submission} en estado {@code SUBMITTED} lista
     * para ser calificada.
     *
     * @param userId        UUID del estudiante
     * @param assessment    evaluación asociada
     * @param attemptNumber número de intento (base 1)
     * @param req           request con las respuestas y duración
     * @return instancia de {@link Submission} con los datos iniciales configurados
     */
    private Submission buildNewSubmission(
            UUID userId,
            Assessment assessment,
            int attemptNumber,
            SubmitRequest req) {

        Submission s = new Submission();
        s.setUserId(userId);
        s.setAssessment(assessment);
        s.setAttemptNumber(attemptNumber);
        s.setStatus(SubmissionStatus.SUBMITTED);
        s.setSubmittedAt(Instant.now());
        s.setDurationSeconds(req.durationSeconds());
        persistAnswersJson(s, req.answers());
        return s;
    }

    /**
     * Serializa las respuestas del estudiante y las almacena en la submission.
     *
     * <p>Si la serialización falla, almacena {@code "{}"}.</p>
     *
     * @param submission submission donde se almacenarán las respuestas
     * @param answers    mapa de respuestas (preguntaId → lista de opciones)
     */
    private void persistAnswersJson(
            Submission submission,
            Map<String, List<String>> answers) {
        try {
            submission.setAnswersJson(mapper.writeValueAsString(answers));
        } catch (Exception e) {
            submission.setAnswersJson("{}");
        }
    }

    /**
     * Calcula la duración real del intento medida en el servidor.
     *
     * <p>Si la evaluación tiene tiempo límite configurado, la duración se
     * limita a ese máximo (RNF RF-08).</p>
     *
     * @param submission submission con la marca de tiempo de inicio
     * @param assessment evaluación con la configuración de tiempo límite
     * @return duración en segundos, acotada al tiempo límite si corresponde
     */
    private long computeActualDuration(Submission submission, Assessment assessment) {
        Instant now = Instant.now();
        long actualSeconds =
                now.getEpochSecond() - submission.getStartedAt().getEpochSecond();
        if (assessment.getTimeLimitMinutes() != null) {
            actualSeconds = Math.min(
                    actualSeconds, assessment.getTimeLimitMinutes() * 60L);
        }
        return actualSeconds;
    }

    /**
     * Aplica el resultado de la calificación sobre la submission.
     *
     * @param submission      submission a actualizar
     * @param grade           resultado de la calificación
     * @param durationSeconds duración del intento en segundos
     * @param gradedAt        instante de calificación
     */
    private void applyGrade(
            Submission submission,
            GradeResult grade,
            long durationSeconds,
            Instant gradedAt) {

        submission.setScore(grade.score());
        submission.setMaxScore(grade.maxScore());
        submission.setPassed(grade.passed());
        submission.setStatus(SubmissionStatus.GRADED);
        submission.setSubmittedAt(gradedAt);
        submission.setDurationSeconds(durationSeconds);
        submission.setGradedAt(gradedAt);
    }

    /**
     * Publica el evento de submission completada en el bus de analytics (RabbitMQ).
     *
     * @param submission submission calificada
     * @param assessment evaluación asociada
     * @param grade      resultado de la calificación
     * @param duration   duración reportada por el cliente
     * @param allPassed  {@code true} si el estudiante aprobó todas las evaluaciones
     *                   del curso
     */
    private void publishEvent(
            Submission submission,
            Assessment assessment,
            GradeResult grade,
            long duration,
            boolean allPassed) {

        String lessonId = assessment.getLessonId() != null
                ? assessment.getLessonId().toString() : null;
        AssessmentSubmittedEvent event = new AssessmentSubmittedEvent(
                submission.getId().toString(),
                submission.getUserId().toString(),
                assessment.getId().toString(),
                assessment.getCourseId().toString(),
                lessonId,
                grade.score(), grade.maxScore(),
                grade.passed(), duration, allPassed
        );
        eventPublisher.publishAnalytics(event);
    }

    /**
     * Califica las respuestas del estudiante contra las opciones correctas
     * de cada pregunta activa de la evaluación.
     *
     * <p>Lógica de puntuación:</p>
     * <ul>
     *   <li>{@code SINGLE_CHOICE} / {@code TRUE_FALSE}: puntos completos si se
     *       seleccionó exactamente la opción correcta.</li>
     *   <li>{@code MULTIPLE_CHOICE}: puntos completos si el conjunto seleccionado
     *       es exactamente igual al conjunto de opciones correctas.</li>
     *   <li>{@code SHORT_ANSWER}: requiere revisión manual; no se puntúa
     *       automáticamente.</li>
     * </ul>
     *
     * @param assessment evaluación con sus preguntas y opciones
     * @param answers    mapa de respuestas del estudiante (preguntaId → opciones)
     * @return {@link GradeResult} con puntaje, máximo, porcentaje y aprobación
     */
    private GradeResult grade(
            Assessment assessment,
            Map<String, List<String>> answers) {

        double totalPoints  = 0.0;
        double earnedPoints = 0.0;

        for (Question q : assessment.getQuestions()) {
            if (q.isDeleted()) continue;
            totalPoints += q.getPoints();

            List<String> given =
                    answers.getOrDefault(q.getId().toString(), List.of());
            Set<String> correctIds = collectCorrectIds(q);

            earnedPoints += computeEarnedPoints(q, given, correctIds);
        }

        BigDecimal score    = BigDecimal.valueOf(earnedPoints)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal maxScore = BigDecimal.valueOf(totalPoints)
                .setScale(2, RoundingMode.HALF_UP);
        double scorePct     = totalPoints > 0
                ? (earnedPoints / totalPoints) * 100.0 : 0.0;
        boolean passed      = scorePct >= assessment.getPassingScorePct();

        return new GradeResult(score, maxScore, scorePct, passed);
    }

    /**
     * Recopila los IDs de las opciones correctas de una pregunta.
     *
     * @param q pregunta cuyas opciones se analizan
     * @return conjunto de UUIDs (como {@code String}) de las opciones correctas
     */
    private Set<String> collectCorrectIds(Question q) {
        Set<String> correctIds = new HashSet<>();
        for (AnswerOption opt : q.getOptions()) {
            if (opt.isCorrect()) correctIds.add(opt.getId().toString());
        }
        return correctIds;
    }

    /**
     * Calcula los puntos ganados por el estudiante en una pregunta.
     *
     * @param q          pregunta evaluada
     * @param given      lista de IDs de opciones seleccionadas por el estudiante
     * @param correctIds conjunto de IDs de opciones correctas
     * @return puntos completos de la pregunta si respondió correctamente, 0.0 si no
     */
    private double computeEarnedPoints(
            Question q,
            List<String> given,
            Set<String> correctIds) {

        if (q.getType() == QuestionType.SINGLE_CHOICE
                || q.getType() == QuestionType.TRUE_FALSE) {
            if (given.size() == 1 && correctIds.contains(given.get(0))) {
                return q.getPoints();
            }
        } else if (q.getType() == QuestionType.MULTIPLE_CHOICE) {
            Set<String> givenSet = new HashSet<>(given);
            if (givenSet.equals(correctIds)) {
                return q.getPoints();
            }
        }
        // SHORT_ANSWER: requiere revisión manual — no se puntúa automáticamente
        return 0.0;
    }

    /**
     * Determina si el estudiante aprobó todas las evaluaciones activas del curso.
     *
     * <p>Se usa para determinar si el estudiante completó el curso, lo cual
     * se incluye en el evento de analytics.</p>
     *
     * @param userId   UUID del estudiante
     * @param courseId UUID del curso
     * @return {@code true} si el estudiante aprobó al menos un intento de cada
     *         evaluación del curso, {@code false} si el curso no tiene evaluaciones
     *         o si alguna aún no ha sido aprobada
     */
    private boolean computeAllAssessmentsPassed(UUID userId, UUID courseId) {
        List<Assessment> courseAssessments = assessmentRepo.findByCourse(courseId);
        if (courseAssessments.isEmpty()) return false;

        List<UUID> assessmentIds =
                courseAssessments.stream().map(Assessment::getId).toList();
        List<Submission> userSubmissions =
                submissionRepo.findByUserAndAssessments(userId, assessmentIds);

        return courseAssessments.stream().allMatch(a ->
                userSubmissions.stream().anyMatch(s ->
                        s.getAssessment().getId().equals(a.getId())
                        && s.isPassed()));
    }

    /**
     * Resultado interno de la calificación de una evaluación.
     *
     * @param score    puntaje bruto obtenido
     * @param maxScore puntaje máximo posible
     * @param scorePct porcentaje de aciertos (0-100)
     * @param passed   {@code true} si supera el umbral de aprobación
     */
    private record GradeResult(
            BigDecimal score,
            BigDecimal maxScore,
            double     scorePct,
            boolean    passed) {}
}
