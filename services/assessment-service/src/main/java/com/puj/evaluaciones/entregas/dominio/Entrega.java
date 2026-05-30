package com.puj.evaluaciones.entregas.dominio;

import com.puj.evaluaciones.evaluaciones.dominio.Evaluacion;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Intento de un estudiante en una evaluación (entrega).
 *
 * <p>Registra el ciclo completo desde el inicio ({@code IN_PROGRESS}) hasta
 * la calificación ({@code GRADED}), incluyendo las respuestas en formato JSON,
 * el puntaje obtenido y la duración del intento.</p>
 *
 * <p>El campo {@code numero_intento} permite distinguir los distintos intentos
 * de un mismo estudiante en la misma evaluación.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "submissions", schema = "assessments")
public class Entrega {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID idUsuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_submission_assessment"))
    private Evaluacion evaluacion;

    /** Puntaje bruto obtenido por el estudiante. {@code null} mientras IN_PROGRESS. */
    @Column(name = "score", precision = 5, scale = 2)
    private BigDecimal puntuacion;

    /** Puntaje máximo posible para la evaluación. {@code null} mientras IN_PROGRESS. */
    @Column(name = "max_score", precision = 5, scale = 2)
    private BigDecimal puntuacionMaxima;

    /** {@code true} si el porcentaje de aciertos supera el umbral de aprobación. */
    @Column(name = "passed", nullable = false)
    private boolean aprobado = false;

    /** Duración en segundos que el estudiante empleó en completar la evaluación. */
    @Column(name = "duration_seconds")
    private long duracionSegundos;

    /** Número de intento (base 1) dentro del límite configurado en la evaluación. */
    @Column(name = "attempt_number", nullable = false)
    private int numeroIntento;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EstadoEntrega estado = EstadoEntrega.IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private Instant iniciadaEn;

    @Column(name = "submitted_at")
    private Instant entregadaEn;

    @Column(name = "graded_at")
    private Instant calificadaEn;

    /** Respuestas del estudiante serializadas como JSON (mapa preguntaId → opciones). */
    @Column(name = "answers_json", columnDefinition = "TEXT")
    private String respuestasJson;

    /**
     * Inicializa el ID y la marca de tiempo de inicio al persistir por primera vez.
     */
    @PrePersist
    void alCrear() {
        if (id == null) id = UUID.randomUUID();
        iniciadaEn = Instant.now();
    }

    /**
     * Siempre retorna {@code false}; las entregas no se eliminan de forma lógica.
     *
     * @return {@code false}
     */
    public boolean isEliminada() { return false; }

    /** @return identificador único de la entrega */
    public UUID         getId()               { return id; }

    /** @return identificador del estudiante que realizó el intento */
    public UUID         getIdUsuario()        { return idUsuario; }

    /** @return evaluación a la que pertenece este intento */
    public Evaluacion   getEvaluacion()       { return evaluacion; }

    /** @return puntaje obtenido, o {@code null} si aún no ha sido calificado */
    public BigDecimal   getPuntuacion()       { return puntuacion; }

    /** @return puntaje máximo posible, o {@code null} si aún no ha sido calificado */
    public BigDecimal   getPuntuacionMaxima() { return puntuacionMaxima; }

    /** @return {@code true} si el estudiante aprobó la evaluación */
    public boolean      isAprobado()          { return aprobado; }

    /** @return duración del intento en segundos */
    public long         getDuracionSegundos() { return duracionSegundos; }

    /** @return número de intento (base 1) */
    public int          getNumeroIntento()    { return numeroIntento; }

    /** @return estado actual del intento */
    public EstadoEntrega getEstado()          { return estado; }

    /** @return instante en que el estudiante inició la evaluación */
    public Instant      getIniciadaEn()       { return iniciadaEn; }

    /** @return instante en que el estudiante envió sus respuestas, o {@code null} */
    public Instant      getEntregadaEn()      { return entregadaEn; }

    /** @return instante en que se calificó el intento, o {@code null} */
    public Instant      getCalificadaEn()     { return calificadaEn; }

    /** @return respuestas del estudiante en formato JSON */
    public String       getRespuestasJson()   { return respuestasJson; }

    /**
     * Establece el identificador del estudiante.
     *
     * @param idUsuario UUID del estudiante
     */
    public void setIdUsuario(UUID idUsuario)              { this.idUsuario = idUsuario; }

    /**
     * Establece la evaluación asociada a este intento.
     *
     * @param e evaluación propietaria
     */
    public void setEvaluacion(Evaluacion e)               { this.evaluacion = e; }

    /**
     * Establece el puntaje bruto obtenido.
     *
     * @param puntuacion puntaje con precisión de 2 decimales
     */
    public void setPuntuacion(BigDecimal puntuacion)       { this.puntuacion = puntuacion; }

    /**
     * Establece el puntaje máximo posible.
     *
     * @param puntuacionMaxima puntaje máximo con precisión de 2 decimales
     */
    public void setPuntuacionMaxima(BigDecimal puntuacionMaxima) {
        this.puntuacionMaxima = puntuacionMaxima;
    }

    /**
     * Establece si el estudiante aprobó la evaluación.
     *
     * @param aprobado {@code true} si superó el umbral de aprobación
     */
    public void setAprobado(boolean aprobado)             { this.aprobado = aprobado; }

    /**
     * Establece la duración del intento.
     *
     * @param duracionSegundos segundos transcurridos desde el inicio hasta el envío
     */
    public void setDuracionSegundos(long duracionSegundos){ this.duracionSegundos = duracionSegundos; }

    /**
     * Establece el número de intento dentro del límite configurado.
     *
     * @param numeroIntento número entero positivo (base 1)
     */
    public void setNumeroIntento(int numeroIntento)       { this.numeroIntento = numeroIntento; }

    /**
     * Establece el estado del intento.
     *
     * @param estado uno de {@link EstadoEntrega}
     */
    public void setEstado(EstadoEntrega estado)           { this.estado = estado; }

    /**
     * Establece el instante de envío de respuestas.
     *
     * @param entregadaEn marca de tiempo del momento del envío
     */
    public void setEntregadaEn(Instant entregadaEn)       { this.entregadaEn = entregadaEn; }

    /**
     * Establece el instante en que se calificó el intento.
     *
     * @param calificadaEn marca de tiempo de la calificación
     */
    public void setCalificadaEn(Instant calificadaEn)     { this.calificadaEn = calificadaEn; }

    /**
     * Almacena las respuestas del estudiante serializadas como JSON.
     *
     * @param respuestasJson JSON con el mapa de respuestas (preguntaId → opciones)
     */
    public void setRespuestasJson(String respuestasJson)  { this.respuestasJson = respuestasJson; }
}
