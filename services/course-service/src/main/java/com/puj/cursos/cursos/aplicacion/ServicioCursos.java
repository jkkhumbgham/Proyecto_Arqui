package com.puj.cursos.cursos.aplicacion;

import com.puj.cursos.aspectos.registro.Registrable;
import com.puj.cursos.cursos.dominio.Curso;
import com.puj.cursos.cursos.dominio.EstadoCurso;
import com.puj.cursos.cursos.dominio.RepositorioCursos;
import com.puj.cursos.cursos.interfaces.dto.RespuestaCurso;
import com.puj.cursos.cursos.interfaces.dto.SolicitudCurso;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.UUID;

/**
 * Servicio de aplicación para la gestión del ciclo de vida de los cursos.
 *
 * <p>Implementa las reglas de negocio sobre creación, actualización, publicación
 * y borrado de cursos. Delega la persistencia en {@link RepositorioCursos} y devuelve
 * siempre DTOs {@link RespuestaCurso} para aislar la capa REST de las entidades JPA.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
@Registrable
public class ServicioCursos {

    @Inject private RepositorioCursos repositorioCursos;

    /**
     * Devuelve la página indicada de cursos en estado {@code PUBLISHED}.
     *
     * @param  pagina número de página (base 0)
     * @param  tamano cantidad máxima de resultados
     * @return lista de cursos publicados proyectados como {@link RespuestaCurso}
     */
    @Transactional
    public List<RespuestaCurso> buscarPublicados(int pagina, int tamano) {
        return repositorioCursos.buscarPublicados(pagina, tamano).stream()
                .map(RespuestaCurso::desde)
                .toList();
    }

    /**
     * Devuelve la página indicada de cursos de un instructor específico.
     *
     * @param  idInstructor identificador del instructor propietario
     * @param  pagina       número de página (base 0)
     * @param  tamano       cantidad máxima de resultados
     * @return lista de cursos del instructor proyectados como {@link RespuestaCurso}
     */
    @Transactional
    public List<RespuestaCurso> buscarPorInstructor(UUID idInstructor, int pagina, int tamano) {
        return repositorioCursos.buscarPorInstructor(idInstructor, pagina, tamano).stream()
                .map(RespuestaCurso::desde)
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
    public RespuestaCurso buscarPorId(UUID id) {
        return repositorioCursos.buscarPorId(id)
                .map(RespuestaCurso::desde)
                .orElseThrow(() -> new NotFoundException("Curso no encontrado: " + id));
    }

    /**
     * Crea un nuevo curso en estado {@code DRAFT} para el instructor indicado.
     *
     * @param  solicitud    datos del curso a crear; no debe ser {@code null}
     * @param  idInstructor identificador del instructor propietario
     * @return DTO del curso recién creado
     */
    @Transactional
    public RespuestaCurso crear(SolicitudCurso solicitud, UUID idInstructor) {
        Curso curso = new Curso();
        curso.establecerTitulo(solicitud.titulo());
        curso.establecerDescripcion(solicitud.descripcion());
        curso.establecerIdInstructor(idInstructor);
        curso.establecerMaxEstudiantes(solicitud.maxEstudiantes());
        aplicarEstado(curso, solicitud.estado());
        repositorioCursos.guardar(curso);
        return RespuestaCurso.desde(curso);
    }

    /**
     * Actualiza los campos del curso indicado si el instructor es su propietario.
     *
     * @param  id           identificador del curso a actualizar
     * @param  solicitud    campos a actualizar; los nulos se ignoran
     * @param  idInstructor identificador del instructor que realiza la operación
     * @return DTO del curso actualizado
     * @throws NotFoundException  si el curso no existe
     * @throws ForbiddenException si el instructor no es propietario del curso
     */
    @Transactional
    public RespuestaCurso actualizar(UUID id, SolicitudCurso solicitud, UUID idInstructor) {
        Curso curso = obtenerCursoPropietario(id, idInstructor);
        if (solicitud.titulo() != null)       curso.establecerTitulo(solicitud.titulo());
        if (solicitud.descripcion() != null)  curso.establecerDescripcion(solicitud.descripcion());
        if (solicitud.maxEstudiantes() != null) curso.establecerMaxEstudiantes(solicitud.maxEstudiantes());
        aplicarEstado(curso, solicitud.estado());
        repositorioCursos.guardar(curso);
        return RespuestaCurso.desde(curso);
    }

    /**
     * Publica el curso si el instructor es propietario y el curso tiene al menos un módulo.
     *
     * @param  id           identificador del curso a publicar
     * @param  idInstructor identificador del instructor que realiza la operación
     * @return DTO del curso publicado
     * @throws NotFoundException   si el curso no existe
     * @throws ForbiddenException  si el instructor no es propietario del curso
     * @throws BadRequestException si el curso no tiene módulos
     */
    @Transactional
    public RespuestaCurso publicar(UUID id, UUID idInstructor) {
        Curso curso = obtenerCursoPropietario(id, idInstructor);
        if (curso.obtenerModulos().isEmpty()) {
            throw new BadRequestException(
                    "El curso debe tener al menos un módulo para publicarse.");
        }
        curso.establecerEstado(EstadoCurso.PUBLISHED);
        repositorioCursos.guardar(curso);
        return RespuestaCurso.desde(curso);
    }

    /**
     * Realiza el borrado lógico del curso si no está publicado y el instructor es propietario.
     *
     * @param  id           identificador del curso a eliminar
     * @param  idInstructor identificador del instructor que realiza la operación
     * @throws NotFoundException   si el curso no existe
     * @throws ForbiddenException  si el instructor no es propietario del curso
     * @throws BadRequestException si el curso está publicado (debe archivarse primero)
     */
    @Transactional
    public void eliminar(UUID id, UUID idInstructor) {
        Curso curso = obtenerCursoPropietario(id, idInstructor);
        if (curso.obtenerEstado() == EstadoCurso.PUBLISHED) {
            throw new BadRequestException(
                    "No se puede eliminar un curso publicado. Archívalo primero.");
        }
        curso.eliminarLogicamente();
        repositorioCursos.guardar(curso);
    }

    /**
     * Devuelve la entidad {@link Curso} gestionada por JPA para operaciones internas.
     *
     * <p>Usado por recursos REST que necesitan la entidad cruda para operaciones
     * como la creación de módulos dentro de una transacción activa.
     *
     * @param  idCurso      identificador del curso
     * @param  idInstructor identificador del instructor propietario
     * @return entidad {@link Curso} gestionada por el contexto de persistencia
     * @throws NotFoundException  si el curso no existe
     * @throws ForbiddenException si el instructor no es propietario del curso
     */
    @Transactional
    public Curso buscarCrudo(UUID idCurso, UUID idInstructor) {
        return obtenerCursoPropietario(idCurso, idInstructor);
    }

    // -------------------------------------------------------------------------
    // Métodos privados
    // -------------------------------------------------------------------------

    /**
     * Aplica el estado al curso si el nombre proporcionado es válido.
     * Los valores nulos, en blanco o desconocidos se ignoran silenciosamente.
     *
     * @param curso  entidad de curso a modificar
     * @param estado nombre del estado a aplicar; puede ser {@code null}
     */
    private void aplicarEstado(Curso curso, String estado) {
        if (estado == null || estado.isBlank()) return;
        try { curso.establecerEstado(EstadoCurso.valueOf(estado)); }
        catch (IllegalArgumentException ignorado) {}
    }

    /**
     * Obtiene el curso y verifica que el instructor sea su propietario.
     *
     * @param  idCurso      identificador del curso
     * @param  idInstructor identificador del instructor
     * @return entidad {@link Curso} si existe y pertenece al instructor
     * @throws NotFoundException  si el curso no existe o fue eliminado
     * @throws ForbiddenException si el instructor no es el propietario
     */
    private Curso obtenerCursoPropietario(UUID idCurso, UUID idInstructor) {
        Curso curso = repositorioCursos.buscarPorId(idCurso)
                .orElseThrow(
                        () -> new NotFoundException("Curso no encontrado: " + idCurso));
        if (!curso.obtenerIdInstructor().equals(idInstructor)) {
            throw new ForbiddenException("No eres el instructor de este curso.");
        }
        return curso;
    }
}
