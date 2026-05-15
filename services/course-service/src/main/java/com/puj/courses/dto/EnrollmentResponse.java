package com.puj.courses.dto;

import com.puj.courses.entity.Enrollment;
import com.puj.courses.entity.EnrollmentStatus;

import java.time.Instant;
import java.util.UUID;

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
    public static EnrollmentResponse from(Enrollment e) {
        return new EnrollmentResponse(
                e.getId(), e.getUserId(),
                e.getCourse().getId(), e.getCourse().getTitle(),
                e.getStatus(), e.getProgressPct(),
                e.getEnrolledAt(), e.getCompletedAt()
        );
    }
}
