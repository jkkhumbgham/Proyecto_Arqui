package com.puj.colaboracion.foros.dominio;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Foro de discusión asociado a un curso de la plataforma.
 *
 * <p>Cada curso puede tener uno o más foros. Un foro agrupa hilos de
 * discusión ({@link Hilo}) que a su vez contienen publicaciones ({@link Publicacion}).
 * El borrado es lógico: el foro se marca con {@code deleted_at} y se excluye
 * de las consultas activas.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "forums", schema = "collaboration")
public class Foro {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @JsonProperty("title")
    @NotBlank
    @Column(name = "title", nullable = false, length = 200)
    private String nombre;

    /** Identificador del curso al que pertenece el foro. */
    @JsonProperty("courseId")
    @Column(name = "course_id", nullable = false)
    private UUID idCurso;

    /** Identificador del usuario que creó el foro (instructor o admin). */
    @Column(name = "created_by", nullable = false)
    private UUID creadoPor;

    @JsonProperty("description")
    @Column(name = "description", columnDefinition = "TEXT")
    private String descripcion;

    @OneToMany(mappedBy = "foro",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    @OrderBy("creadoEn DESC")
    private List<Hilo> hilos = new ArrayList<>();

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
     * Indica si el foro ha sido eliminado de forma lógica.
     *
     * @return {@code true} si {@code eliminadoEn} tiene valor
     */
    public boolean estaEliminado() { return eliminadoEn != null; }

    /**
     * Marca el foro como eliminado de forma lógica con la marca de tiempo actual.
     */
    public void eliminarLogicamente() { this.eliminadoEn = Instant.now(); }

    /** @return identificador único del foro */
    public UUID        getId()          { return id; }

    /** @return nombre (título) del foro */
    public String      getNombre()      { return nombre; }

    /** @return identificador del curso al que pertenece */
    public UUID        getIdCurso()     { return idCurso; }

    /** @return identificador del creador del foro */
    public UUID        getCreadoPor()   { return creadoPor; }

    /** @return descripción del foro */
    public String      getDescripcion() { return descripcion; }

    /** @return lista de hilos del foro, ordenados por fecha de creación descendente */
    public List<Hilo>  getHilos()       { return hilos; }

    /** @return instante de creación del foro */
    public Instant     getCreadoEn()    { return creadoEn; }

    /** @return instante de eliminación lógica, o {@code null} si está activo */
    public Instant     getEliminadoEn() { return eliminadoEn; }

    /**
     * Establece el nombre (título) del foro.
     *
     * @param nombre nombre no nulo ni en blanco (máx. 200 caracteres)
     */
    public void setNombre(String nombre)           { this.nombre = nombre; }

    /**
     * Establece el curso al que pertenece el foro.
     *
     * @param idCurso UUID del curso
     */
    public void setIdCurso(UUID idCurso)           { this.idCurso = idCurso; }

    /**
     * Establece el creador del foro.
     *
     * @param creadoPor UUID del usuario creador
     */
    public void setCreadoPor(UUID creadoPor)       { this.creadoPor = creadoPor; }

    /**
     * Establece la descripción del foro.
     *
     * @param descripcion texto libre descriptivo
     */
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}
