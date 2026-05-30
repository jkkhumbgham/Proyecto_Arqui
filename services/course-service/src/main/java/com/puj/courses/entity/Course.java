package com.puj.courses.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entidad JPA que representa un curso en la plataforma de aprendizaje.
 *
 * <p>Un curso pertenece a un instructor, contiene módulos ordenados y tiene un ciclo
 * de vida definido por {@link CourseStatus}. El borrado lógico (soft delete) se realiza
 * asignando {@code deletedAt}; los registros eliminados son ignorados por la capa
 * de repositorio.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "courses", schema = "courses")
public class Course {

    /** Identificador único del curso, generado al persistir. */
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Título del curso; máximo 200 caracteres, obligatorio. */
    @NotBlank @Size(max = 200)
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** Descripción detallada del curso en texto libre. */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Identificador del usuario instructor propietario del curso. */
    @NotNull
    @Column(name = "instructor_id", nullable = false)
    private UUID instructorId;

    /** Estado actual del ciclo de vida del curso; valor por defecto {@code DRAFT}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CourseStatus status = CourseStatus.DRAFT;

    /** URL de la imagen de portada almacenada en S3; puede ser {@code null}. */
    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    /** Número máximo de estudiantes permitidos; {@code null} indica sin límite. */
    @Column(name = "max_students")
    private Integer maxStudents;

    /** Módulos del curso ordenados por {@code orderIndex} de forma ascendente. */
    @OneToMany(
            mappedBy    = "course",
            cascade     = CascadeType.ALL,
            orphanRemoval = true,
            fetch       = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    private List<Module> modules = new ArrayList<>();

    /** Marca temporal de creación; inmutable tras la primera persistencia. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Marca temporal de la última actualización. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Categoría temática del curso (ej. "Programación", "Diseño"). */
    @Column(name = "category", length = 100)
    private String category;

    /** Marca temporal de borrado lógico; {@code null} si el curso está activo. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    // -------------------------------------------------------------------------
    // Callbacks JPA
    // -------------------------------------------------------------------------

    /**
     * Inicializa {@code id}, {@code createdAt} y {@code updatedAt} antes de persistir.
     */
    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    /**
     * Actualiza {@code updatedAt} en cada modificación de la entidad.
     */
    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    // -------------------------------------------------------------------------
    // Estado lógico
    // -------------------------------------------------------------------------

    /**
     * Indica si el curso ha sido borrado lógicamente.
     *
     * @return {@code true} si {@code deletedAt} tiene un valor distinto de {@code null}
     */
    public boolean isDeleted() { return deletedAt != null; }

    /**
     * Realiza el borrado lógico del curso asignando la marca temporal actual.
     */
    public void softDelete() { this.deletedAt = Instant.now(); }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** @return identificador único del curso */
    public UUID         getId()           { return id; }

    /** @return título del curso */
    public String       getTitle()        { return title; }

    /** @return descripción del curso, puede ser {@code null} */
    public String       getDescription()  { return description; }

    /** @return identificador del instructor propietario */
    public UUID         getInstructorId() { return instructorId; }

    /** @return estado actual del curso */
    public CourseStatus getStatus()       { return status; }

    /** @return URL de la imagen de portada, puede ser {@code null} */
    public String       getCoverImageUrl(){ return coverImageUrl; }

    /** @return categoría temática del curso, puede ser {@code null} */
    public String       getCategory()     { return category; }

    /** @return capacidad máxima de estudiantes, puede ser {@code null} */
    public Integer      getMaxStudents()  { return maxStudents; }

    /** @return lista de módulos del curso */
    public List<Module> getModules()      { return modules; }

    /** @return marca temporal de creación */
    public Instant      getCreatedAt()    { return createdAt; }

    /** @return marca temporal de última actualización */
    public Instant      getUpdatedAt()    { return updatedAt; }

    /** @return marca temporal de borrado lógico, {@code null} si activo */
    public Instant      getDeletedAt()    { return deletedAt; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    /**
     * Establece el título del curso.
     *
     * @param title nuevo título; no debe ser vacío ni superar 200 caracteres
     */
    public void setTitle(String title)              { this.title = title; }

    /**
     * Establece la descripción del curso.
     *
     * @param description descripción en texto libre; puede ser {@code null}
     */
    public void setDescription(String description)  { this.description = description; }

    /**
     * Establece el identificador del instructor propietario.
     *
     * @param instructorId UUID del usuario con rol INSTRUCTOR
     */
    public void setInstructorId(UUID instructorId)  { this.instructorId = instructorId; }

    /**
     * Establece el estado del ciclo de vida del curso.
     *
     * @param status nuevo estado; no debe ser {@code null}
     */
    public void setStatus(CourseStatus status)      { this.status = status; }

    /**
     * Establece la URL de la imagen de portada.
     *
     * @param url URL de la imagen almacenada en S3; puede ser {@code null}
     */
    public void setCoverImageUrl(String url)        { this.coverImageUrl = url; }

    /**
     * Establece la categoría temática del curso.
     *
     * @param category nombre de la categoría; puede ser {@code null}
     */
    public void setCategory(String category)        { this.category = category; }

    /**
     * Establece el número máximo de estudiantes permitidos.
     *
     * @param maxStudents límite de inscripciones; {@code null} elimina el límite
     */
    public void setMaxStudents(Integer maxStudents) { this.maxStudents = maxStudents; }
}
