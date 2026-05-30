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

/**
 * Servicio de negocio para la gestión del ciclo de vida de los cursos.
 *
 * <p>Implementa las reglas de negocio sobre creación, actualización, publicación
 * y borrado de cursos. Delega la persistencia en {@link CourseRepository} y devuelve
 * siempre DTOs {@link CourseResponse} para aislar la capa REST de las entidades JPA.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class CourseService {

    @Inject private CourseRepository courseRepo;

    /**
     * Devuelve la página indicada de cursos en estado {@code PUBLISHED}.
     *
     * @param  page número de página (base 0)
     * @param  size cantidad máxima de resultados
     * @return lista de cursos publicados proyectados como {@link CourseResponse}
     */
    @Transactional
    public List<CourseResponse> findPublished(int page, int size) {
        return courseRepo.findPublished(page, size).stream()
                .map(CourseResponse::from)
                .toList();
    }

    /**
     * Devuelve la página indicada de cursos de un instructor específico.
     *
     * @param  instructorId identificador del instructor propietario
     * @param  page         número de página (base 0)
     * @param  size         cantidad máxima de resultados
     * @return lista de cursos del instructor proyectados como {@link CourseResponse}
     */
    @Transactional
    public List<CourseResponse> findByInstructor(UUID instructorId, int page, int size) {
        return courseRepo.findByInstructor(instructorId, page, size).stream()
                .map(CourseResponse::from)
                .toList();
    }

    /**
     * Busca un curso por su identificador.
     *
     * @param  id identificador del curso
     * @return DTO del curso encontrado
     * @throws NotFoundException si el curso no existe o fue borrado lógicamente
     */
    @Transactional
    public CourseResponse findById(UUID id) {
        return courseRepo.findById(id)
                .map(CourseResponse::from)
                .orElseThrow(() -> new NotFoundException("Curso no encontrado: " + id));
    }

    /**
     * Crea un nuevo curso en estado {@code DRAFT} para el instructor indicado.
     *
     * @param  req          datos del curso a crear; no debe ser {@code null}
     * @param  instructorId identificador del instructor propietario
     * @return DTO del curso recién creado
     */
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

    /**
     * Actualiza los campos del curso indicado si el instructor es su propietario.
     *
     * @param  id           identificador del curso a actualizar
     * @param  req          campos a actualizar; los nulos se ignoran
     * @param  instructorId identificador del instructor que realiza la operación
     * @return DTO del curso actualizado
     * @throws NotFoundException  si el curso no existe
     * @throws ForbiddenException si el instructor no es propietario del curso
     */
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

    /**
     * Publica el curso si el instructor es propietario y el curso tiene al menos un módulo.
     *
     * @param  id           identificador del curso a publicar
     * @param  instructorId identificador del instructor que realiza la operación
     * @return DTO del curso publicado
     * @throws NotFoundException   si el curso no existe
     * @throws ForbiddenException  si el instructor no es propietario del curso
     * @throws BadRequestException si el curso no tiene módulos
     */
    @Transactional
    public CourseResponse publish(UUID id, UUID instructorId) {
        Course course = getOwnedCourse(id, instructorId);
        if (course.getModules().isEmpty()) {
            throw new BadRequestException(
                    "El curso debe tener al menos un módulo para publicarse.");
        }
        course.setStatus(CourseStatus.PUBLISHED);
        courseRepo.save(course);
        return CourseResponse.from(course);
    }

    /**
     * Realiza el borrado lógico del curso si no está publicado y el instructor es propietario.
     *
     * @param  id           identificador del curso a eliminar
     * @param  instructorId identificador del instructor que realiza la operación
     * @throws NotFoundException   si el curso no existe
     * @throws ForbiddenException  si el instructor no es propietario del curso
     * @throws BadRequestException si el curso está publicado (debe archivarse primero)
     */
    @Transactional
    public void delete(UUID id, UUID instructorId) {
        Course course = getOwnedCourse(id, instructorId);
        if (course.getStatus() == CourseStatus.PUBLISHED) {
            throw new BadRequestException(
                    "No se puede eliminar un curso publicado. Archívalo primero.");
        }
        course.softDelete();
        courseRepo.save(course);
    }

    /**
     * Devuelve la entidad {@link Course} gestionada por JPA para operaciones internas.
     *
     * <p>Usado por recursos REST que necesitan la entidad cruda para operaciones
     * como la creación de módulos dentro de una transacción activa.
     *
     * @param  courseId     identificador del curso
     * @param  instructorId identificador del instructor propietario
     * @return entidad {@link Course} gestionada por el contexto de persistencia
     * @throws NotFoundException  si el curso no existe
     * @throws ForbiddenException si el instructor no es propietario del curso
     */
    @Transactional
    public Course findRaw(UUID courseId, UUID instructorId) {
        return getOwnedCourse(courseId, instructorId);
    }

    // -------------------------------------------------------------------------
    // Métodos privados
    // -------------------------------------------------------------------------

    /**
     * Aplica el estado al curso si el nombre proporcionado es válido.
     * Los valores nulos, en blanco o desconocidos se ignoran silenciosamente.
     *
     * @param course entidad de curso a modificar
     * @param status nombre del estado a aplicar; puede ser {@code null}
     */
    private void applyStatus(Course course, String status) {
        if (status == null || status.isBlank()) return;
        try { course.setStatus(CourseStatus.valueOf(status)); }
        catch (IllegalArgumentException ignored) {}
    }

    /**
     * Obtiene el curso y verifica que el instructor sea su propietario.
     *
     * @param  courseId     identificador del curso
     * @param  instructorId identificador del instructor
     * @return entidad {@link Course} si existe y pertenece al instructor
     * @throws NotFoundException  si el curso no existe o fue eliminado
     * @throws ForbiddenException si el instructor no es el propietario
     */
    private Course getOwnedCourse(UUID courseId, UUID instructorId) {
        Course course = courseRepo.findById(courseId)
                .orElseThrow(
                        () -> new NotFoundException("Curso no encontrado: " + courseId));
        if (!course.getInstructorId().equals(instructorId)) {
            throw new ForbiddenException("No eres el instructor de este curso.");
        }
        return course;
    }
}
