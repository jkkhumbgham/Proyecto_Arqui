package com.puj.courses.dto;

import com.puj.courses.entity.Enrollment;
import com.puj.courses.entity.EnrollmentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de salida que representa la inscripción de un estudiante en un curso.
 *
 * <p>Se construye a partir de una entidad {@link Enrollment} mediante el método
 * de fábrica {@link #from(Enrollment)}.
 *
 * @param id          identificador único de la inscripción
 * @param userId      identificador del estudiante inscrito
 * @param courseId    identificador del curso
 * @param courseTitle título del curso (desnormalizado para evitar joins en el cliente)
 * @param status      estado actual de la inscripción
 * @param progressPct porcentaje de progreso del estudiante (0.0 – 100.0)
 * @param enrolledAt  marca temporal del momento de inscripción
 * @param completedAt marca temporal de finalización; {@code null} si no ha completado
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public record EnrollmentResponse(
        UUID             id,
        UUID             userId,
        UUID             courseId,
        String           courseTitle,
        EnrollmentStatus status,
        double           progressPct,
        Instant          enrolledAt,
        Instant          completedAt
) {

    /**
     * Construye un {@code EnrollmentResponse} a partir de la entidad {@link Enrollment}.
     *
     * @param  e entidad de inscripción a proyectar; no debe ser {@code null}
     * @return DTO con la representación de la inscripción
     */
    public static EnrollmentResponse from(Enrollment e) {
        return new EnrollmentResponse(
                e.getId(),
                e.getUserId(),
                e.getCourse().getId(),
                e.getCourse().getTitle(),
                e.getStatus(),
                e.getProgressPct(),
                e.getEnrolledAt(),
                e.getCompletedAt()
        );
    }
}
