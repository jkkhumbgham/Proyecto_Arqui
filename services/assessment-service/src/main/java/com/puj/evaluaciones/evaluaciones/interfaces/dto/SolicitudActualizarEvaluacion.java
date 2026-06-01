package com.puj.evaluaciones.evaluaciones.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/**
 * DTO de entrada para la actualización parcial de una evaluación existente.
 *
 * <p>Todos los campos son opcionales: si un campo es {@code null} el valor
 * actual de la entidad se conserva. Si se proporciona {@code questions}, la
 * lista completa de preguntas se reemplaza (no se fusiona).</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public record SolicitudActualizarEvaluacion(
        /** Nuevo título de la evaluación. Si es {@code null} o en blanco, se ignora. */
        @JsonProperty("title")
        String titulo,

        /** Nueva descripción de la evaluación. {@code null} conserva el valor actual. */
        @JsonProperty("description")
        String descripcion,

        /** Nuevo identificador de lección asociada. {@code null} conserva el valor actual. */
        @JsonProperty("lessonId")
        UUID idLeccion,

        /** Nuevo porcentaje mínimo de aprobación. {@code null} conserva el valor actual. */
        @JsonProperty("passingScorePct")
        Double porcentajeAprobacion,

        /** Nuevo número máximo de intentos. {@code null} conserva el valor actual. */
        @JsonProperty("maxAttempts")
        Integer maxIntentos,

        /**
         * Nueva lista completa de preguntas. Si es no-{@code null}, reemplaza todas
         * las preguntas existentes de la evaluación.
         */
        @JsonProperty("questions")
        List<SolicitudCrearEvaluacion.SolicitudPregunta> preguntas
) {}
