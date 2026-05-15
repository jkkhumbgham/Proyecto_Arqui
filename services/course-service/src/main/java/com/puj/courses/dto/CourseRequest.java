package com.puj.courses.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CourseRequest(
        @NotBlank @Size(max = 200) String title,
        String description,
        Integer maxStudents
) {}
