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

@ApplicationScoped
public class EnrollmentService {

    @Inject private EnrollmentRepository enrollmentRepo;
    @Inject private CourseRepository     courseRepo;
    @Inject private EventPublisher       eventPublisher;

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
                enrollment.getId().toString(), userId.toString(),
                courseId.toString(), course.getTitle()
        ));

        return EnrollmentResponse.from(enrollment);
    }

    public List<EnrollmentResponse> findByUser(UUID userId) {
        return enrollmentRepo.findByUser(userId).stream()
                .map(EnrollmentResponse::from)
                .toList();
    }

    @Transactional
    public void cancel(UUID userId, UUID courseId) {
        Enrollment enrollment = enrollmentRepo.findByUserAndCourse(userId, courseId)
                .orElseThrow(() -> new NotFoundException("Inscripción no encontrada."));
        enrollment.setStatus(EnrollmentStatus.CANCELLED);
        enrollment.softDelete();
        enrollmentRepo.merge(enrollment);
    }

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
