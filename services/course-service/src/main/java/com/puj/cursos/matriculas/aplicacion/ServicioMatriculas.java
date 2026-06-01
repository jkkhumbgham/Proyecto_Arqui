package com.puj.cursos.matriculas.aplicacion;

import com.puj.cursos.aspectos.registro.Registrable;
import com.puj.cursos.cursos.dominio.Curso;
import com.puj.cursos.cursos.dominio.EstadoCurso;
import com.puj.cursos.cursos.dominio.RepositorioCursos;
import com.puj.cursos.matriculas.dominio.EstadoMatricula;
import com.puj.cursos.matriculas.dominio.Matricula;
import com.puj.cursos.matriculas.dominio.RepositorioMatriculas;
import com.puj.cursos.matriculas.interfaces.dto.RespuestaMatricula;
import com.puj.eventos.EventoMatriculaCurso;
import com.puj.eventos.publicador.PublicadorEventos;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.UUID;

/**
 * Servicio de aplicación para la gestión de matrículas de estudiantes en cursos.
 *
 * <p>Aplica las reglas de negocio de matriculación (curso publicado, sin duplicados,
 * cupo disponible) y publica eventos de analítica mediante {@link PublicadorEventos}
 * cuando se producen cambios relevantes.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
@Registrable
public class ServicioMatriculas {

    @Inject private RepositorioMatriculas repositorioMatriculas;
    @Inject private RepositorioCursos     repositorioCursos;
    @Inject private PublicadorEventos     publicadorEventos;
    // Self-injection para llamar métodos @Transactional a través del proxy CDI
    @Inject private ServicioMatriculas    self;

    /**
     * Matricula a un estudiante en un curso, validando disponibilidad y unicidad.
     *
     * <p>Publica un {@link EventoMatriculaCurso} al completar la matriculación exitosamente.
     *
     * @param  idUsuario identificador del estudiante
     * @param  idCurso   identificador del curso
     * @return DTO de la matrícula creada
     * @throws NotFoundException   si el curso no existe
     * @throws BadRequestException si el curso no está publicado, el estudiante ya
     *                             está matriculado o el curso alcanzó su capacidad máxima
     */
    public RespuestaMatricula matricular(UUID idUsuario, UUID idCurso) {
        RespuestaMatricula respuesta = self.persistirMatricula(idUsuario, idCurso);
        publicadorEventos.publicarAnaliticas(new EventoMatriculaCurso(
                respuesta.id().toString(),
                idUsuario.toString(),
                idCurso.toString(),
                respuesta.tituloCurso()));
        return respuesta;
    }

    /** Parte transaccional de la matriculación — solo BD, sin RabbitMQ. */
    @Transactional
    public RespuestaMatricula persistirMatricula(UUID idUsuario, UUID idCurso) {
        Curso curso = repositorioCursos.buscarPorId(idCurso)
                .orElseThrow(() -> new NotFoundException("Curso no encontrado."));

        if (curso.obtenerEstado() != EstadoCurso.PUBLISHED) {
            throw new BadRequestException("El curso no está disponible para matriculación.");
        }
        if (repositorioMatriculas.estaMatriculado(idUsuario, idCurso)) {
            throw new BadRequestException("Ya estás matriculado en este curso.");
        }

        long matriculados = repositorioMatriculas.contarPorCurso(idCurso);
        if (curso.obtenerMaxEstudiantes() != null && matriculados >= curso.obtenerMaxEstudiantes()) {
            throw new BadRequestException("El curso ha alcanzado su capacidad máxima.");
        }

        Matricula matricula = new Matricula();
        matricula.establecerIdUsuario(idUsuario);
        matricula.establecerCurso(curso);
        repositorioMatriculas.guardar(matricula);
        return RespuestaMatricula.desde(matricula);
    }

    /**
     * Devuelve todas las matrículas activas de un estudiante.
     *
     * @param  idUsuario identificador del estudiante
     * @return lista de matrículas del estudiante proyectadas como {@link RespuestaMatricula}
     */
    @Transactional
    public List<RespuestaMatricula> buscarPorUsuario(UUID idUsuario, int pagina, int cantidad) {
        return repositorioMatriculas.buscarPorUsuario(idUsuario, pagina, cantidad).stream()
                .map(RespuestaMatricula::desde)
                .toList();
    }

    /**
     * Cancela la matrícula de un estudiante en un curso realizando borrado lógico.
     *
     * @param  idUsuario identificador del estudiante
     * @param  idCurso   identificador del curso
     * @throws NotFoundException si no existe matrícula activa del usuario en el curso
     */
    @Transactional
    public void cancelar(UUID idUsuario, UUID idCurso) {
        Matricula matricula = repositorioMatriculas.buscarPorUsuarioYCurso(idUsuario, idCurso)
                .orElseThrow(() -> new NotFoundException("Matrícula no encontrada."));
        matricula.establecerEstado(EstadoMatricula.CANCELLED);
        matricula.eliminarLogicamente();
        repositorioMatriculas.fusionar(matricula);
    }

    /**
     * Actualiza el porcentaje de progreso de una matrícula.
     *
     * <p>Si el progreso alcanza el 100%, la matrícula pasa automáticamente a
     * estado {@code COMPLETED} y se registra {@code completadoEn}.
     *
     * @param  idUsuario        identificador del estudiante
     * @param  idCurso          identificador del curso
     * @param  porcentajeProgreso nuevo porcentaje de progreso (se normaliza al rango 0.0–100.0)
     * @return DTO de la matrícula actualizada
     * @throws NotFoundException si no existe matrícula activa del usuario en el curso
     */
    @Transactional
    public RespuestaMatricula actualizarProgreso(UUID idUsuario, UUID idCurso, double porcentajeProgreso) {
        Matricula matricula = repositorioMatriculas.buscarPorUsuarioYCurso(idUsuario, idCurso)
                .orElseThrow(() -> new NotFoundException("Matrícula no encontrada."));
        matricula.establecerPorcentajeProgreso(Math.min(100.0, Math.max(0.0, porcentajeProgreso)));
        if (matricula.obtenerPorcentajeProgreso() >= 100.0) {
            matricula.establecerEstado(EstadoMatricula.COMPLETED);
            matricula.establecerCompletadoEn(java.time.Instant.now());
        }
        return RespuestaMatricula.desde(repositorioMatriculas.fusionar(matricula));
    }
}
