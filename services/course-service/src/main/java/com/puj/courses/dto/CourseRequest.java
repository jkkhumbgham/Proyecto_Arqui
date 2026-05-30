package com.puj.courses.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada para las operaciones de creación y actualización de cursos.
 *
 * <p>Todos los campos son opcionales en la operación de actualización parcial (PUT),
 * salvo {@code title}, que está marcado como obligatorio en validación de creación.
 *
 * @param title       título del curso; no debe ser vacío, máximo 200 caracteres
 * @param description descripción del curso en texto libre; puede ser {@code null}
 * @param maxStudents capacidad máxima de estudiantes; {@code null} indica sin límite
 * @param status      nombre del estado deseado ({@code DRAFT}, {@code PUBLISHED},
 *                    {@code ARCHIVED}); si es {@code null} o inválido, se ignora
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public record CourseRequest(
        @NotBlank @Size(max = 200) String title,
        String  description,
        Integer maxStudents,
        String  status
) {}
