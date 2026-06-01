package com.puj.colaboracion.gruposestudio.dominio;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Membresía de un usuario en un grupo de estudio.
 *
 * <p>La restricción única {@code uq_group_member} sobre {@code (group_id, user_id)}
 * garantiza que un usuario no pueda pertenecer dos veces al mismo grupo como
 * miembro activo. El borrado es lógico para permitir re-unirse sin violar la
 * restricción (se restaura la membresía con {@link #restaurar()}).</p>
 *
 * <p>El campo {@code esTutor} se actualiza automáticamente por el servicio
 * {@code ServicioGruposEstudio.intentarAsignarTutor()} al producirse cambios
 * en la membresía.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "group_members", schema = "collaboration",
        uniqueConstraints = @UniqueConstraint(
                name        = "uq_group_member",
                columnNames = {"group_id", "user_id"}))
public class MiembroGrupo {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_member_group"))
    private GrupoEstudio grupo;

    @Column(name = "user_id", nullable = false)
    private UUID idUsuario;

    /** {@code true} si el miembro es el tutor actual del grupo. */
    @Column(name = "is_tutor", nullable = false)
    private boolean esTutor = false;

    /** Instante en que el usuario se unió al grupo por primera (o última) vez. */
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant unidoEn;

    @Column(name = "deleted_at")
    private Instant eliminadoEn;

    /**
     * Genera el UUID y establece la marca de tiempo de ingreso al persistir.
     */
    @PrePersist
    void alCrear() {
        if (id == null) id = UUID.randomUUID();
        unidoEn = Instant.now();
    }

    /**
     * Indica si la membresía ha sido eliminada de forma lógica.
     *
     * @return {@code true} si {@code eliminadoEn} tiene valor
     */
    public boolean estaEliminado()  { return eliminadoEn != null; }

    /**
     * Marca la membresía como eliminada (el usuario abandonó el grupo).
     */
    public void eliminarLogicamente() { this.eliminadoEn = Instant.now(); }

    /**
     * Restaura una membresía eliminada (el usuario vuelve a unirse al grupo).
     */
    public void restaurar()          { this.eliminadoEn = null; }

    /** @return identificador único de la membresía */
    public UUID          getId()           { return id; }

    /** @return grupo de estudio al que pertenece esta membresía */
    public GrupoEstudio  getGrupo()        { return grupo; }

    /** @return identificador del usuario miembro */
    public UUID          getIdUsuario()    { return idUsuario; }

    /** @return {@code true} si el miembro es el tutor del grupo */
    public boolean       esTutor()         { return esTutor; }

    /** @return instante de ingreso al grupo */
    public Instant       getUnidoEn()      { return unidoEn; }

    /** @return instante de eliminación lógica, o {@code null} si es miembro activo */
    public Instant       getEliminadoEn()  { return eliminadoEn; }

    /**
     * Establece el grupo de estudio propietario de esta membresía.
     *
     * @param grupo grupo de estudio
     */
    public void setGrupo(GrupoEstudio grupo) { this.grupo = grupo; }

    /**
     * Establece el identificador del usuario miembro.
     *
     * @param idUsuario UUID del usuario
     */
    public void setIdUsuario(UUID idUsuario) { this.idUsuario = idUsuario; }

    /**
     * Marca o desmarca al miembro como tutor del grupo.
     *
     * @param tutor {@code true} para designar como tutor
     */
    public void setTutor(boolean tutor)      { this.esTutor = tutor; }
}
