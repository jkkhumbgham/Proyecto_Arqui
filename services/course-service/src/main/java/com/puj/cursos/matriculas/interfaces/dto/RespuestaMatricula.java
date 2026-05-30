package com.puj.cursos.matriculas.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.puj.cursos.matriculas.dominio.EstadoMatricula;
import com.puj.cursos.matriculas.dominio.Matricula;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de salida que representa la matrícula de un estudiante en un curso.
 *
 * <p>Se construye a partir de una entidad {@link Matricula} mediante el método
 * de fábrica {@link #desde(Matricula)}. Los nombres de propiedad JSON mantienen
 * el esquema inglés para compatibilidad con el frontend existente.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public record RespuestaMatricula(
        @JsonProperty("id")           UUID           id,
        @JsonProperty("userId")       UUID           idUsuario,
        @JsonProperty("courseId")     UUID           idCurso,
        @JsonProperty("courseTitle")  String         tituloCurso,
        @JsonProperty("status")       EstadoMatricula estado,
        @JsonProperty("progressPct")  double          porcentajeProgreso,
        @JsonProperty("enrolledAt")   Instant         matriculadoEn,
        @JsonProperty("completedAt")  Instant         completadoEn
) {

    /**
     * Construye un {@code RespuestaMatricula} a partir de la entidad {@link Matricula}.
     *
     * @param  m entidad de matrícula a proyectar; no debe ser {@code null}
     * @return DTO con la representación de la matrícula
     */
    public static RespuestaMatricula desde(Matricula m) {
        return new RespuestaMatricula(
                m.getId(),
                m.obtenerIdUsuario(),
                m.obtenerCurso().getId(),
                m.obtenerCurso().obtenerTitulo(),
                m.obtenerEstado(),
                m.obtenerPorcentajeProgreso(),
                m.obtenerMatriculadoEn(),
                m.obtenerCompletadoEn()
        );
    }
}
