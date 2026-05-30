package com.puj.assessments.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Intento de un estudiante en una evaluación (submission).
 *
 * <p>Registra el ciclo completo desde el inicio ({@code IN_PROGRESS}) hasta
 * la calificación ({@code GRADED}), incluyendo las respuestas en formato JSON,
 * el puntaje obtenido y la duración del intento.</p>
 *
 * <p>El campo {@code attemptNumber} permite distinguir los distintos intentos
 * de un mismo estudiante en la misma evaluación.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "submissions", schema = "assessments")
public class Submission {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_submission_assessment"))
    private Assessment assessment;

    /** Puntaje bruto obtenido por el estudiante. {@code null} mientras IN_PROGRESS. */
    @Column(name = "score", precision = 5, scale = 2)
    private BigDecimal score;

    /** Puntaje máximo posible para la evaluación. {@code null} mientras IN_PROGRESS. */
    @Column(name = "max_score", precision = 5, scale = 2)
    private BigDecimal maxScore;

    /** {@code true} si el porcentaje de aciertos supera el umbral de aprobación. */
    @Column(name = "passed", nullable = false)
    private boolean passed = false;

    /** Duración en segundos que el estudiante empleó en completar la evaluación. */
    @Column(name = "duration_seconds")
    private long durationSeconds;

    /** Número de intento (base 1) dentro del límite configurado en la evaluación. */
    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubmissionStatus status = SubmissionStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "graded_at")
    private Instant gradedAt;

    /** Respuestas del estudiante serializadas como JSON (mapa preguntaId → opciones). */
    @Column(name = "answers_json", columnDefinition = "TEXT")
    private String answersJson;

    /**
     * Inicializa el ID y la marca de tiempo de inicio al persistir por primera vez.
     */
    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        startedAt = Instant.now();
    }

    /**
     * Siempre retorna {@code false}; las submissions no se eliminan de forma lógica.
     *
     * @return {@code false}
     */
    public boolean isDeleted() { return false; }

    /** @return identificador único de la submission */
    public UUID             getId()              { return id; }

    /** @return identificador del estudiante que realizó el intento */
    public UUID             getUserId()          { return userId; }

    /** @return evaluación a la que pertenece este intento */
    public Assessment       getAssessment()      { return assessment; }

    /** @return puntaje obtenido, o {@code null} si aún no ha sido calificado */
    public BigDecimal       getScore()           { return score; }

    /** @return puntaje máximo posible, o {@code null} si aún no ha sido calificado */
    public BigDecimal       getMaxScore()        { return maxScore; }

    /** @return {@code true} si el estudiante aprobó la evaluación */
    public boolean          isPassed()           { return passed; }

    /** @return duración del intento en segundos */
    public long             getDurationSeconds() { return durationSeconds; }

    /** @return número de intento (base 1) */
    public int              getAttemptNumber()   { return attemptNumber; }

    /** @return estado actual del intento */
    public SubmissionStatus getStatus()          { return status; }

    /** @return instante en que el estudiante inició la evaluación */
    public Instant          getStartedAt()       { return startedAt; }

    /** @return instante en que el estudiante envió sus respuestas, o {@code null} */
    public Instant          getSubmittedAt()     { return submittedAt; }

    /** @return instante en que se calificó el intento, o {@code null} */
    public Instant          getGradedAt()        { return gradedAt; }

    /** @return respuestas del estudiante en formato JSON */
    public String           getAnswersJson()     { return answersJson; }

    /**
     * Establece el identificador del estudiante.
     *
     * @param userId UUID del estudiante
     */
    public void setUserId(UUID userId)              { this.userId = userId; }

    /**
     * Establece la evaluación asociada a este intento.
     *
     * @param a evaluación propietaria
     */
    public void setAssessment(Assessment a)         { this.assessment = a; }

    /**
     * Establece el puntaje bruto obtenido.
     *
     * @param score puntaje con precisión de 2 decimales
     */
    public void setScore(BigDecimal score)           { this.score = score; }

    /**
     * Establece el puntaje máximo posible.
     *
     * @param maxScore puntaje máximo con precisión de 2 decimales
     */
    public void setMaxScore(BigDecimal maxScore)     { this.maxScore = maxScore; }

    /**
     * Establece si el estudiante aprobó la evaluación.
     *
     * @param passed {@code true} si superó el umbral de aprobación
     */
    public void setPassed(boolean passed)            { this.passed = passed; }

    /**
     * Establece la duración del intento.
     *
     * @param d segundos transcurridos desde el inicio hasta el envío
     */
    public void setDurationSeconds(long d)           { this.durationSeconds = d; }

    /**
     * Establece el número de intento dentro del límite configurado.
     *
     * @param attemptNumber número entero positivo (base 1)
     */
    public void setAttemptNumber(int attemptNumber)  { this.attemptNumber = attemptNumber; }

    /**
     * Establece el estado del intento.
     *
     * @param status uno de {@link SubmissionStatus}
     */
    public void setStatus(SubmissionStatus status)   { this.status = status; }

    /**
     * Establece el instante de envío de respuestas.
     *
     * @param submittedAt marca de tiempo del momento del envío
     */
    public void setSubmittedAt(Instant submittedAt)  { this.submittedAt = submittedAt; }

    /**
     * Establece el instante en que se calificó el intento.
     *
     * @param gradedAt marca de tiempo de la calificación
     */
    public void setGradedAt(Instant gradedAt)        { this.gradedAt = gradedAt; }

    /**
     * Almacena las respuestas del estudiante serializadas como JSON.
     *
     * @param answersJson JSON con el mapa de respuestas (preguntaId → opciones)
     */
    public void setAnswersJson(String answersJson)   { this.answersJson = answersJson; }
}
