package com.puj.courses.dto;

import com.puj.courses.entity.Course;
import com.puj.courses.entity.CourseStatus;
import com.puj.courses.entity.Module;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO de salida que representa un curso con sus módulos y lecciones anidados.
 *
 * <p>Se construye a partir de una entidad {@link Course} mediante el método de fábrica
 * {@link #from(Course)}, aplicando filtrado de módulos y lecciones con borrado lógico.
 *
 * @param id           identificador único del curso
 * @param title        título del curso
 * @param description  descripción del curso, puede ser {@code null}
 * @param instructorId identificador del instructor propietario
 * @param status       estado actual del ciclo de vida del curso
 * @param coverImageUrl URL de la imagen de portada, puede ser {@code null}
 * @param maxStudents  capacidad máxima de estudiantes, puede ser {@code null}
 * @param moduleCount  número de módulos activos del curso
 * @param modules      lista de módulos con sus lecciones incluidas
 * @param createdAt    marca temporal de creación del curso
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public record CourseResponse(
        UUID             id,
        String           title,
        String           description,
        UUID             instructorId,
        CourseStatus     status,
        String           coverImageUrl,
        Integer          maxStudents,
        int              moduleCount,
        List<ModuleInfo> modules,
        Instant          createdAt
) {

    /**
     * Proyección de lección usada dentro de {@link ModuleInfo}.
     *
     * @param id              identificador de la lección
     * @param title           título de la lección
     * @param content         contenido textual, puede ser {@code null}
     * @param orderIndex      posición dentro del módulo
     * @param durationMinutes duración estimada en minutos, puede ser {@code null}
     * @param contentType     tipo de contenido (TEXT, VIDEO, PDF, DOCUMENT)
     * @param contentUrl      URL del recurso externo, puede ser {@code null}
     * @param supplementary   {@code true} si la lección no cuenta para el progreso
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public record LessonInfo(
            UUID    id,
            String  title,
            String  content,
            int     orderIndex,
            Integer durationMinutes,
            String  contentType,
            String  contentUrl,
            boolean supplementary) {}

    /**
     * Proyección de módulo que incluye la lista de sus lecciones activas.
     *
     * @param id          identificador del módulo
     * @param title       título del módulo
     * @param description descripción del módulo, puede ser {@code null}
     * @param orderIndex  posición dentro del curso
     * @param lessons     lecciones activas del módulo
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public record ModuleInfo(
            UUID             id,
            String           title,
            String           description,
            int              orderIndex,
            List<LessonInfo> lessons) {}

    /**
     * Construye un {@code CourseResponse} a partir de la entidad {@link Course}.
     *
     * <p>Filtra módulos y lecciones con borrado lógico ({@code deletedAt != null})
     * antes de construir las proyecciones anidadas.
     *
     * @param  c entidad de curso a proyectar; no debe ser {@code null}
     * @return DTO con la representación completa del curso
     */
    public static CourseResponse from(Course c) {
        List<Module> mods = c.getModules() == null
                ? List.of()
                : c.getModules().stream().filter(m -> !m.isDeleted()).toList();

        return new CourseResponse(
                c.getId(),
                c.getTitle(),
                c.getDescription(),
                c.getInstructorId(),
                c.getStatus(),
                c.getCoverImageUrl(),
                c.getMaxStudents(),
                mods.size(),
                mods.stream().map(m -> {
                    List<LessonInfo> lessons = m.getLessons() == null
                            ? List.of()
                            : m.getLessons().stream()
                                    .filter(l -> !l.isDeleted())
                                    .map(l -> new LessonInfo(
                                            l.getId(),
                                            l.getTitle(),
                                            l.getContent(),
                                            l.getOrderIndex(),
                                            l.getDurationMinutes(),
                                            l.getContentType(),
                                            l.getContentUrl(),
                                            l.isSupplementary()))
                                    .toList();
                    return new ModuleInfo(
                            m.getId(),
                            m.getTitle(),
                            m.getDescription(),
                            m.getOrderIndex(),
                            lessons);
                }).toList(),
                c.getCreatedAt()
        );
    }
}
