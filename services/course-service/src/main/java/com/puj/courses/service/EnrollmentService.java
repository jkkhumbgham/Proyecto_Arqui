package com.puj.courses.service;

import com.puj.courses.dto.EnrollmentResponse;
import com.puj.courses.entity.Course;
import com.puj.courses.entity.CourseStatus;
import com.puj.courses.entity.Enrollment;
import com.puj.courses.entity.EnrollmentStatus;
import com.puj.courses.repository.CourseRepository;
import com.puj.courses.repository.EnrollmentRepository;
import com.puj.events.CourseEnrolledEvent;
import com.puj.events.publisher.EventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.UUID;

/**
 * Servicio de negocio para la gestión de inscripciones de estudiantes en cursos.
 *
 * <p>Aplica las reglas de negocio de inscripción (curso publicado, sin duplicados,
 * cupo disponible) y publica eventos de análisis mediante {@link EventPublisher}
 * cuando se producen cambios relevantes.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class EnrollmentService {

    @Inject private EnrollmentRepository enrollmentRepo;
    @Inject private CourseRepository     courseRepo;
    @Inject private EventPublisher       eventPublisher;

    /**
     * Inscribe a un estudiante en un curso, validando disponibilidad y unicidad.
     *
     * <p>Publica un {@link CourseEnrolledEvent} al completar la inscripción exitosamente.
     *
     * @param  userId   identificador del estudiante
     * @param  courseId identificador del curso
     * @return DTO de la inscripción creada
     * @throws NotFoundException   si el curso no existe
     * @throws BadRequestException si el curso no está publicado, el estudiante ya
     *                             está inscrito o el curso alcanzó su capacidad máxima
     */
    @Transactional
    public EnrollmentResponse enroll(UUID userId, UUID courseId) {
        Course course = courseRepo.findById(courseId)
                .orElseThrow(() -> new NotFoundException("Curso no encontrado."));

        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new BadRequestException("El curso no está disponible para inscripción.");
        }
        if (enrollmentRepo.isEnrolled(userId, courseId)) {
            throw new BadRequestException("Ya estás inscrito en este curso.");
        }

        long enrolled = enrollmentRepo.countByCourse(courseId);
        if (course.getMaxStudents() != null && enrolled >= course.getMaxStudents()) {
            throw new BadRequestException("El curso ha alcanzado su capacidad máxima.");
        }

        Enrollment enrollment = new Enrollment();
        enrollment.setUserId(userId);
        enrollment.setCourse(course);
        enrollmentRepo.save(enrollment);

        eventPublisher.publishAnalytics(new CourseEnrolledEvent(
                enrollment.getId().toString(),
                userId.toString(),
                courseId.toString(),
                course.getTitle()
        ));

        return EnrollmentResponse.from(enrollment);
    }

    /**
     * Devuelve todas las inscripciones activas de un estudiante.
     *
     * @param  userId identificador del estudiante
     * @return lista de inscripciones del estudiante proyectadas como {@link EnrollmentResponse}
     */
    @Transactional
    public List<EnrollmentResponse> findByUser(UUID userId) {
        return enrollmentRepo.findByUser(userId).stream()
                .map(EnrollmentResponse::from)
                .toList();
    }

    /**
     * Cancela la inscripción de un estudiante en un curso realizando borrado lógico.
     *
     * @param  userId   identificador del estudiante
     * @param  courseId identificador del curso
     * @throws NotFoundException si no existe inscripción activa del usuario en el curso
     */
    @Transactional
    public void cancel(UUID userId, UUID courseId) {
        Enrollment enrollment = enrollmentRepo.findByUserAndCourse(userId, courseId)
                .orElseThrow(() -> new NotFoundException("Inscripción no encontrada."));
        enrollment.setStatus(EnrollmentStatus.CANCELLED);
        enrollment.softDelete();
        enrollmentRepo.merge(enrollment);
    }

    /**
     * Actualiza el porcentaje de progreso de una inscripción.
     *
     * <p>Si el progreso alcanza el 100%, la inscripción pasa automáticamente a
     * estado {@code COMPLETED} y se registra {@code completedAt}.
     *
     * @param  userId      identificador del estudiante
     * @param  courseId    identificador del curso
     * @param  progressPct nuevo porcentaje de progreso (se normaliza al rango 0.0–100.0)
     * @return DTO de la inscripción actualizada
     * @throws NotFoundException si no existe inscripción activa del usuario en el curso
     */
    @Transactional
    public EnrollmentResponse updateProgress(UUID userId, UUID courseId, double progressPct) {
        Enrollment enrollment = enrollmentRepo.findByUserAndCourse(userId, courseId)
                .orElseThrow(() -> new NotFoundException("Inscripción no encontrada."));
        enrollment.setProgressPct(Math.min(100.0, Math.max(0.0, progressPct)));
        if (enrollment.getProgressPct() >= 100.0) {
            enrollment.setStatus(EnrollmentStatus.COMPLETED);
            enrollment.setCompletedAt(java.time.Instant.now());
        }
        return EnrollmentResponse.from(enrollmentRepo.merge(enrollment));
    }
}
