package com.puj.assessments.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entidad principal que representa una evaluación de la plataforma.
 *
 * <p>Una evaluación pertenece a un curso y, opcionalmente, a una lección.
 * Contiene la lista de preguntas activas, el umbral de aprobación y la
 * configuración de intentos y tiempo límite.</p>
 *
 * <p>El borrado es lógico (soft-delete): la evaluación se marca con
 * {@code deletedAt} y se excluye de las consultas activas.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "assessments", schema = "assessments")
public class Assessment {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @NotNull
    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "lesson_id")
    private UUID lessonId;

    @NotNull
    @Column(name = "instructor_id", nullable = false)
    private UUID instructorId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Número máximo de intentos permitidos por estudiante. Por defecto 3. */
    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 3;

    /** Tiempo límite en minutos. {@code null} indica sin límite de tiempo. */
    @Column(name = "time_limit_minutes")
    private Integer timeLimitMinutes;

    /** Porcentaje mínimo de aciertos para aprobar la evaluación (0-100). Por defecto 60. */
    @Column(name = "passing_score_pct", nullable = false)
    private double passingScorePct = 60.0;

    /** Si {@code true}, el orden de las preguntas se aleatoriza para cada intento. */
    @Column(name = "randomize_questions", nullable = false)
    private boolean randomizeQuestions = false;

    @OneToMany(mappedBy = "assessment",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    private List<Question> questions = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * Genera el UUID y establece las marcas de tiempo al persistir por primera vez.
     */
    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    /**
     * Actualiza la marca de tiempo de modificación en cada actualización.
     */
    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    /**
     * Indica si la evaluación ha sido eliminada de forma lógica.
     *
     * @return {@code true} si {@code deletedAt} tiene valor
     */
    public boolean isDeleted() { return deletedAt != null; }

    /**
     * Marca la evaluación como eliminada de forma lógica con la marca de tiempo actual.
     */
    public void softDelete()   { this.deletedAt = Instant.now(); }

    /** @return identificador único de la evaluación */
    public UUID           getId()                { return id; }

    /** @return título de la evaluación */
    public String         getTitle()             { return title; }

    /** @return identificador del curso al que pertenece */
    public UUID           getCourseId()          { return courseId; }

    /** @return identificador de la lección asociada, o {@code null} */
    public UUID           getLessonId()          { return lessonId; }

    /** @return identificador del instructor propietario */
    public UUID           getInstructorId()      { return instructorId; }

    /** @return descripción o instrucciones de la evaluación */
    public String         getDescription()       { return description; }

    /** @return número máximo de intentos permitidos */
    public int            getMaxAttempts()       { return maxAttempts; }

    /** @return límite de tiempo en minutos, o {@code null} si no hay límite */
    public Integer        getTimeLimitMinutes()  { return timeLimitMinutes; }

    /** @return porcentaje mínimo para aprobar (0-100) */
    public double         getPassingScorePct()   { return passingScorePct; }

    /** @return {@code true} si el orden de preguntas se aleatoriza */
    public boolean        isRandomizeQuestions() { return randomizeQuestions; }

    /** @return lista de preguntas ordenadas por {@code orderIndex} */
    public List<Question> getQuestions()         { return questions; }

    /** @return instante de creación */
    public Instant        getCreatedAt()         { return createdAt; }

    /** @return instante de última modificación */
    public Instant        getUpdatedAt()         { return updatedAt; }

    /** @return instante de eliminación lógica, o {@code null} si está activa */
    public Instant        getDeletedAt()         { return deletedAt; }

    /**
     * Establece el título de la evaluación.
     *
     * @param title título no nulo ni en blanco (máx. 200 caracteres)
     */
    public void setTitle(String title)                     { this.title = title; }

    /**
     * Establece el curso al que pertenece la evaluación.
     *
     * @param courseId UUID del curso
     */
    public void setCourseId(UUID courseId)                 { this.courseId = courseId; }

    /**
     * Establece la lección asociada a la evaluación.
     *
     * @param lessonId UUID de la lección, o {@code null}
     */
    public void setLessonId(UUID lessonId)                 { this.lessonId = lessonId; }

    /**
     * Establece el instructor propietario de la evaluación.
     *
     * @param instructorId UUID del instructor
     */
    public void setInstructorId(UUID instructorId)         { this.instructorId = instructorId; }

    /**
     * Establece la descripción o instrucciones de la evaluación.
     *
     * @param description texto libre sin límite de longitud
     */
    public void setDescription(String description)         { this.description = description; }

    /**
     * Establece el número máximo de intentos permitidos por estudiante.
     *
     * @param maxAttempts número entero positivo de intentos
     */
    public void setMaxAttempts(int maxAttempts)            { this.maxAttempts = maxAttempts; }

    /**
     * Establece el tiempo límite en minutos para completar la evaluación.
     *
     * @param t minutos de tiempo límite, o {@code null} para sin límite
     */
    public void setTimeLimitMinutes(Integer t)             { this.timeLimitMinutes = t; }

    /**
     * Establece el porcentaje mínimo de aciertos para aprobar.
     *
     * @param passingScorePct porcentaje (0-100)
     */
    public void setPassingScorePct(double passingScorePct) {
        this.passingScorePct = passingScorePct;
    }

    /**
     * Activa o desactiva la aleatorización del orden de las preguntas.
     *
     * @param r {@code true} para aleatorizar
     */
    public void setRandomizeQuestions(boolean r)           { this.randomizeQuestions = r; }
}
