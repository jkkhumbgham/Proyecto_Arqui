package com.puj.collaboration.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Grupo de estudio colaborativo dentro de un curso.
 *
 * <p>Los grupos permiten a los estudiantes colaborar en tiempo real mediante
 * WebSocket y chat persistido. El creador del grupo es designado automáticamente
 * como tutor inicial. El tutor se reelije dinámicamente por puntaje cuando
 * cambia la membresía.</p>
 *
 * <p>El borrado es lógico: el grupo se marca con {@code deletedAt} y se excluye
 * de las consultas activas.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "study_groups", schema = "collaboration")
public class StudyGroup {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** Identificador del curso al que pertenece el grupo. */
    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    /** Identificador del estudiante que creó el grupo. */
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    /** Número máximo de miembros activos permitidos en el grupo. Por defecto 10. */
    @Column(name = "max_members", nullable = false)
    private int maxMembers = 10;

    @OneToMany(mappedBy = "group",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    private List<GroupMember> members = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * Genera el UUID y establece la marca de tiempo de creación al persistir.
     */
    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
    }

    /**
     * Indica si el grupo ha sido eliminado de forma lógica.
     *
     * @return {@code true} si {@code deletedAt} tiene valor
     */
    public boolean isDeleted() { return deletedAt != null; }

    /**
     * Marca el grupo como eliminado de forma lógica.
     */
    public void softDelete()   { this.deletedAt = Instant.now(); }

    /** @return identificador único del grupo */
    public UUID              getId()         { return id; }

    /** @return nombre del grupo */
    public String            getName()       { return name; }

    /** @return identificador del curso al que pertenece */
    public UUID              getCourseId()   { return courseId; }

    /** @return identificador del creador del grupo */
    public UUID              getOwnerId()    { return ownerId; }

    /** @return número máximo de miembros activos */
    public int               getMaxMembers() { return maxMembers; }

    /** @return lista de membresías (activas e inactivas) del grupo */
    public List<GroupMember> getMembers()    { return members; }

    /** @return instante de creación del grupo */
    public Instant           getCreatedAt()  { return createdAt; }

    /** @return instante de eliminación lógica, o {@code null} si está activo */
    public Instant           getDeletedAt()  { return deletedAt; }

    /**
     * Establece el nombre del grupo.
     *
     * @param name nombre no nulo ni en blanco (máx. 200 caracteres)
     */
    public void setName(String name)          { this.name = name; }

    /**
     * Establece el curso al que pertenece el grupo.
     *
     * @param courseId UUID del curso
     */
    public void setCourseId(UUID courseId)    { this.courseId = courseId; }

    /**
     * Establece el propietario o creador del grupo.
     *
     * @param ownerId UUID del estudiante creador
     */
    public void setOwnerId(UUID ownerId)      { this.ownerId = ownerId; }

    /**
     * Establece el número máximo de miembros activos en el grupo.
     *
     * @param maxMembers entero positivo; por defecto 10
     */
    public void setMaxMembers(int maxMembers) { this.maxMembers = maxMembers; }
}
