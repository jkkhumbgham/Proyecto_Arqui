package com.puj.colaboracion.gruposestudio.dominio;

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
 * como tutor inicial. El tutor se reelige dinámicamente por puntaje cuando
 * cambia la membresía.</p>
 *
 * <p>El borrado es lógico: el grupo se marca con {@code deleted_at} y se excluye
 * de las consultas activas.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "study_groups", schema = "collaboration")
public class GrupoEstudio {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Column(name = "name", nullable = false, length = 200)
    private String nombre;

    /** Identificador del curso al que pertenece el grupo. */
    @Column(name = "course_id", nullable = false)
    private UUID idCurso;

    /** Identificador del estudiante que creó el grupo. */
    @Column(name = "owner_id", nullable = false)
    private UUID creadoPor;

    /** Número máximo de miembros activos permitidos en el grupo. Por defecto 10. */
    @Column(name = "max_members", nullable = false)
    private int maxMiembros = 10;

    @OneToMany(mappedBy = "grupo",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    private List<MiembroGrupo> miembros = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "deleted_at")
    private Instant eliminadoEn;

    /**
     * Genera el UUID y establece la marca de tiempo de creación al persistir.
     */
    @PrePersist
    void alCrear() {
        if (id == null) id = UUID.randomUUID();
        creadoEn = Instant.now();
    }

    /**
     * Indica si el grupo ha sido eliminado de forma lógica.
     *
     * @return {@code true} si {@code eliminadoEn} tiene valor
     */
    public boolean estaEliminado() { return eliminadoEn != null; }

    /**
     * Marca el grupo como eliminado de forma lógica.
     */
    public void eliminarLogicamente() { this.eliminadoEn = Instant.now(); }

    /** @return identificador único del grupo */
    public UUID               getId()          { return id; }

    /** @return nombre del grupo */
    public String             getNombre()      { return nombre; }

    /** @return identificador del curso al que pertenece */
    public UUID               getIdCurso()     { return idCurso; }

    /** @return identificador del creador del grupo */
    public UUID               getCreadoPor()   { return creadoPor; }

    /** @return número máximo de miembros activos */
    public int                getMaxMiembros() { return maxMiembros; }

    /** @return lista de membresías (activas e inactivas) del grupo */
    public List<MiembroGrupo> getMiembros()    { return miembros; }

    /** @return instante de creación del grupo */
    public Instant            getCreadoEn()    { return creadoEn; }

    /** @return instante de eliminación lógica, o {@code null} si está activo */
    public Instant            getEliminadoEn() { return eliminadoEn; }

    /**
     * Establece el nombre del grupo.
     *
     * @param nombre nombre no nulo ni en blanco (máx. 200 caracteres)
     */
    public void setNombre(String nombre)           { this.nombre = nombre; }

    /**
     * Establece el curso al que pertenece el grupo.
     *
     * @param idCurso UUID del curso
     */
    public void setIdCurso(UUID idCurso)           { this.idCurso = idCurso; }

    /**
     * Establece el propietario o creador del grupo.
     *
     * @param creadoPor UUID del estudiante creador
     */
    public void setCreadoPor(UUID creadoPor)       { this.creadoPor = creadoPor; }

    /**
     * Establece el número máximo de miembros activos en el grupo.
     *
     * @param maxMiembros entero positivo; por defecto 10
     */
    public void setMaxMiembros(int maxMiembros)    { this.maxMiembros = maxMiembros; }
}
