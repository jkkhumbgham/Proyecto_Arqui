package com.puj.evaluaciones.evaluaciones.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.puj.evaluaciones.evaluaciones.dominio.Evaluacion;
import com.puj.evaluaciones.evaluaciones.dominio.Pregunta;

import java.util.List;
import java.util.UUID;

/**
 * Vista detallada de una evaluación, incluyendo todas sus preguntas y opciones.
 *
 * <p>Se usa para devolver al cliente la estructura completa de la evaluación.
 * Las respuestas correctas se incluyen para el uso del instructor; el frontend
 * es responsable de ocultarlas al estudiante según el rol.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public record DetalleEvaluacion(
        @JsonProperty("id")              UUID                   id,
        @JsonProperty("title")           String                 titulo,
        @JsonProperty("description")     String                 descripcion,
        @JsonProperty("courseId")        UUID                   idCurso,
        @JsonProperty("lessonId")        UUID                   idLeccion,
        @JsonProperty("passingScorePct") double                 porcentajeAprobacion,
        @JsonProperty("maxAttempts")     int                    maxIntentos,
        @JsonProperty("timeLimitMinutes")Integer                limiteMinutos,
        @JsonProperty("questions")       List<DetallePregunta>  preguntas
) {

    /**
     * Vista de una opción de respuesta para la representación detallada.
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public record DetalleOpcion(
            @JsonProperty("id")         UUID    id,
            @JsonProperty("text")       String  texto,
            @JsonProperty("orderIndex") int     orden,
            @JsonProperty("correct")    boolean correcta
    ) {}

    /**
     * Vista de una pregunta con sus opciones para la representación detallada.
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public record DetallePregunta(
            @JsonProperty("id")           UUID               id,
            @JsonProperty("questionText") String             textoPregunta,
            @JsonProperty("questionType") String             tipoPregunta,
            @JsonProperty("points")       double             puntos,
            @JsonProperty("orderIndex")   int                orden,
            @JsonProperty("options")      List<DetalleOpcion> opciones
    ) {}

    /**
     * Construye un {@link DetalleEvaluacion} a partir de la entidad {@link Evaluacion}.
     *
     * <p>Filtra las preguntas eliminadas (soft-deleted) y mapea cada pregunta
     * con sus opciones al formato de presentación.</p>
     *
     * @param e entidad de evaluación a proyectar
     * @return instancia de {@link DetalleEvaluacion} con la vista completa
     */
    public static DetalleEvaluacion from(Evaluacion e) {
        List<DetallePregunta> dps = e.getPreguntas().stream()
                .filter(p -> !p.isEliminada())
                .map(DetalleEvaluacion::aDetallePregunta)
                .toList();

        return new DetalleEvaluacion(
                e.getId(), e.getTitulo(), e.getDescripcion(),
                e.getIdCurso(), e.getIdLeccion(),
                e.getPorcentajeAprobacion(), e.getMaxIntentos(),
                e.getLimiteMinutos(), dps
        );
    }

    /**
     * Mapea una entidad {@link Pregunta} a su DTO de presentación.
     *
     * @param p entidad de pregunta a mapear
     * @return {@link DetallePregunta} con las opciones de la pregunta
     */
    private static DetallePregunta aDetallePregunta(Pregunta p) {
        List<DetalleOpcion> ops = p.getOpciones().stream()
                .map(o -> new DetalleOpcion(
                        o.getId(), o.getTexto(), o.getOrden(), o.isCorrecta()))
                .toList();
        return new DetallePregunta(
                p.getId(), p.getTexto(), p.getTipo().name(),
                p.getPuntos(), p.getOrden(), ops
        );
    }
}
