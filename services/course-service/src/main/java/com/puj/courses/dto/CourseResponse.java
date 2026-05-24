package com.puj.courses.dto;

import com.puj.courses.entity.Course;
import com.puj.courses.entity.CourseStatus;
import com.puj.courses.entity.Module;

import java.time.Instant;
import java.util.List;
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
        List<ModuleInfo> modules,
        Instant      createdAt
) {
    public record LessonInfo(UUID id, String title, String content, int orderIndex, Integer durationMinutes,
                             String contentType, String contentUrl, boolean supplementary) {}
    public record ModuleInfo(UUID id, String title, String description, int orderIndex, List<LessonInfo> lessons) {}

    public static CourseResponse from(Course c) {
        List<Module> mods = c.getModules() == null ? List.of() :
                c.getModules().stream().filter(m -> !m.isDeleted()).toList();
        return new CourseResponse(
                c.getId(), c.getTitle(), c.getDescription(),
                c.getInstructorId(), c.getStatus(), c.getCoverImageUrl(),
                c.getMaxStudents(),
                mods.size(),
                mods.stream().map(m -> {
                    List<LessonInfo> lessons = m.getLessons() == null ? List.of() :
                            m.getLessons().stream()
                                    .filter(l -> !l.isDeleted())
                                    .map(l -> new LessonInfo(l.getId(), l.getTitle(), l.getContent(),
                                            l.getOrderIndex(), l.getDurationMinutes(),
                                            l.getContentType(), l.getContentUrl(), l.isSupplementary()))
                                    .toList();
                    return new ModuleInfo(m.getId(), m.getTitle(), m.getDescription(), m.getOrderIndex(), lessons);
                }).toList(),
                c.getCreatedAt()
        );
    }
}
