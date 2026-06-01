package com.puj.cursos.cursos.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada para las operaciones de creación y actualización de cursos.
 *
 * <p>Todos los campos son opcionales en la operación de actualización parcial (PUT),
 * salvo {@code title}, que está marcado como obligatorio en validación de creación.
 * Los nombres de propiedad JSON mantienen el esquema inglés para compatibilidad
 * con el frontend existente.
 *
 * @param titulo       título del curso; no debe ser vacío, máximo 200 caracteres
 * @param descripcion  descripción del curso en texto libre; puede ser {@code null}
 * @param maxEstudiantes capacidad máxima de estudiantes; {@code null} indica sin límite
 * @param estado       nombre del estado deseado ({@code DRAFT}, {@code PUBLISHED},
 *                     {@code ARCHIVED}); si es {@code null} o inválido, se ignora
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public record SolicitudCurso(
        @JsonProperty("title")
        @NotBlank @Size(max = 200)
        String titulo,

        @JsonProperty("description")
        String descripcion,

        @JsonProperty("maxStudents")
        Integer maxEstudiantes,

        @JsonProperty("status")
        String estado
) {}
