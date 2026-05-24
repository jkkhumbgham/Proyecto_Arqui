package com.puj.courses.service;

import com.puj.courses.dto.CourseRequest;
import com.puj.courses.dto.CourseResponse;
import com.puj.courses.entity.Course;
import com.puj.courses.entity.CourseStatus;
import com.puj.courses.repository.CourseRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class CourseService {

    @Inject private CourseRepository courseRepo;

    @Transactional
    public List<CourseResponse> findPublished(int page, int size) {
        return courseRepo.findPublished(page, size).stream()
                .map(CourseResponse::from)
                .toList();
    }

    @Transactional
    public List<CourseResponse> findByInstructor(UUID instructorId, int page, int size) {
        return courseRepo.findByInstructor(instructorId, page, size).stream()
                .map(CourseResponse::from)
                .toList();
    }

    @Transactional
    public CourseResponse findById(UUID id) {
        return courseRepo.findById(id)
                .map(CourseResponse::from)
                .orElseThrow(() -> new NotFoundException("Curso no encontrado: " + id));
    }

    @Transactional
    public CourseResponse create(CourseRequest req, UUID instructorId) {
        Course course = new Course();
        course.setTitle(req.title());
        course.setDescription(req.description());
        course.setInstructorId(instructorId);
        course.setMaxStudents(req.maxStudents());
        applyStatus(course, req.status());
        courseRepo.save(course);
        return CourseResponse.from(course);
    }

    @Transactional
    public CourseResponse update(UUID id, CourseRequest req, UUID instructorId) {
        Course course = getOwnedCourse(id, instructorId);
        if (req.title() != null)       course.setTitle(req.title());
        if (req.description() != null) course.setDescription(req.description());
        if (req.maxStudents() != null) course.setMaxStudents(req.maxStudents());
        applyStatus(course, req.status());
        courseRepo.save(course);
        return CourseResponse.from(course);
    }

    private void applyStatus(Course course, String status) {
        if (status == null || status.isBlank()) return;
        try { course.setStatus(CourseStatus.valueOf(status)); }
        catch (IllegalArgumentException ignored) {}
    }

    @Transactional
    public CourseResponse publish(UUID id, UUID instructorId) {
        Course course = getOwnedCourse(id, instructorId);
        if (course.getModules().isEmpty()) {
            throw new BadRequestException("El curso debe tener al menos un módulo para publicarse.");
        }
        course.setStatus(CourseStatus.PUBLISHED);
        courseRepo.save(course);
        return CourseResponse.from(course);
    }

    @Transactional
    public void delete(UUID id, UUID instructorId) {
        Course course = getOwnedCourse(id, instructorId);
        if (course.getStatus() == CourseStatus.PUBLISHED) {
            throw new BadRequestException("No se puede eliminar un curso publicado. Archívalo primero.");
        }
        course.softDelete();
        courseRepo.save(course);
    }

    @Transactional
    public Course findRaw(UUID courseId, UUID instructorId) {
        return getOwnedCourse(courseId, instructorId);
    }

    private Course getOwnedCourse(UUID courseId, UUID instructorId) {
        Course course = courseRepo.findById(courseId)
                .orElseThrow(() -> new NotFoundException("Curso no encontrado: " + courseId));
        if (!course.getInstructorId().equals(instructorId)) {
            throw new ForbiddenException("No eres el instructor de este curso.");
        }
        return course;
    }
}
