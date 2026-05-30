package com.puj.collaboration.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Membresía de un usuario en un grupo de estudio.
 *
 * <p>La restricción única {@code uq_group_member} sobre {@code (group_id, user_id)}
 * garantiza que un usuario no pueda pertenecer dos veces al mismo grupo como
 * miembro activo. El borrado es lógico para permitir re-unirse sin violar la
 * restricción (se restaura la membresía con {@link #softRestore()}).</p>
 *
 * <p>El campo {@code isTutor} se actualiza automáticamente por el servicio
 * {@code StudyGroupService.tryAssignTutor()} al producirse cambios en la
 * membresía.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "group_members", schema = "collaboration",
        uniqueConstraints = @UniqueConstraint(
                name        = "uq_group_member",
                columnNames = {"group_id", "user_id"}))
public class GroupMember {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_member_group"))
    private StudyGroup group;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** {@code true} si el miembro es el tutor actual del grupo. */
    @Column(name = "is_tutor", nullable = false)
    private boolean isTutor = false;

    /** Instante en que el usuario se unió al grupo por primera (o última) vez. */
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * Genera el UUID y establece la marca de tiempo de ingreso al persistir.
     */
    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        joinedAt = Instant.now();
    }

    /**
     * Indica si la membresía ha sido eliminada de forma lógica.
     *
     * @return {@code true} si {@code deletedAt} tiene valor
     */
    public boolean isDeleted()  { return deletedAt != null; }

    /**
     * Marca la membresía como eliminada (el usuario abandonó el grupo).
     */
    public void softDelete()    { this.deletedAt = Instant.now(); }

    /**
     * Restaura una membresía eliminada (el usuario vuelve a unirse al grupo).
     */
    public void softRestore()   { this.deletedAt = null; }

    /** @return identificador único de la membresía */
    public UUID       getId()       { return id; }

    /** @return grupo de estudio al que pertenece esta membresía */
    public StudyGroup getGroup()    { return group; }

    /** @return identificador del usuario miembro */
    public UUID       getUserId()   { return userId; }

    /** @return {@code true} si el miembro es el tutor del grupo */
    public boolean    isTutor()     { return isTutor; }

    /** @return instante de ingreso al grupo */
    public Instant    getJoinedAt() { return joinedAt; }

    /** @return instante de eliminación lógica, o {@code null} si es miembro activo */
    public Instant    getDeletedAt(){ return deletedAt; }

    /**
     * Establece el grupo de estudio propietario de esta membresía.
     *
     * @param group grupo de estudio
     */
    public void setGroup(StudyGroup group) { this.group = group; }

    /**
     * Establece el identificador del usuario miembro.
     *
     * @param userId UUID del usuario
     */
    public void setUserId(UUID userId)     { this.userId = userId; }

    /**
     * Marca o desmarca al miembro como tutor del grupo.
     *
     * @param tutor {@code true} para designar como tutor
     */
    public void setTutor(boolean tutor)    { this.isTutor = tutor; }
}
