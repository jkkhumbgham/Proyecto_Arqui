package com.puj.courses.dto;

import com.puj.courses.entity.Course;
import com.puj.courses.entity.CourseStatus;

import java.time.Instant;
import java.util.UUID;

public record CourseResponse(
        UUID         id,
        String       title,
        String       description,
        UUID         instructorId,
        CourseStatus status,
        String       coverImageUrl,
        Integer      maxStudents,
        int          moduleCount,
        Instant      createdAt
) {
    public static CourseResponse from(Course c) {
        return new CourseResponse(
                c.getId(), c.getTitle(), c.getDescription(),
                c.getInstructorId(), c.getStatus(), c.getCoverImageUrl(),
                c.getMaxStudents(),
                c.getModules() == null ? 0 : (int) c.getModules().stream().filter(m -> !m.isDeleted()).count(),
                c.getCreatedAt()
        );
    }
}
