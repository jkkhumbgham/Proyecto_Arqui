package com.puj.cursos.funcional;

import com.puj.cursos.cursos.aplicacion.ServicioCursos;
import com.puj.cursos.cursos.dominio.Curso;
import com.puj.cursos.cursos.dominio.EstadoCurso;
import com.puj.cursos.cursos.dominio.RepositorioCursos;
import com.puj.cursos.cursos.interfaces.dto.RespuestaCurso;
import com.puj.cursos.cursos.interfaces.dto.SolicitudCurso;
import com.puj.cursos.matriculas.aplicacion.ServicioMatriculas;
import com.puj.cursos.matriculas.dominio.EstadoMatricula;
import com.puj.cursos.matriculas.dominio.Matricula;
import com.puj.cursos.matriculas.dominio.RepositorioMatriculas;
import com.puj.cursos.matriculas.interfaces.dto.RespuestaMatricula;
import com.puj.eventos.publicador.PublicadorEventos;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas Funcionales del course-service (TF-007..TF-011).
 *
 * <p>Cubre los flujos completos sobre cursos y matrículas:
 * <ul>
 *   <li>TF-007: Instructor crea curso en estado DRAFT</li>
 *   <li>TF-008: Instructor actualiza curso — solo campos enviados</li>
 *   <li>TF-009: Instructor no propietario no puede actualizar → 403</li>
 *   <li>TF-010: Estudiante se inscribe a curso publicado → ACTIVE + evento</li>
 *   <li>TF-011: Cancelar matrícula → soft delete</li>
 * </ul>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TF-007..011 · Funcional Cursos y Matrículas")
class CursosFuncionalTest {

    @Mock private RepositorioCursos     repoCursos;
    @InjectMocks private ServicioCursos servicioCursos;

    @Mock private RepositorioMatriculas repoMatriculas;
    @Mock private PublicadorEventos     publicadorEventos;
    private ServicioMatriculas servicioMatriculas;

    private UUID idInstructor;
    private UUID idEstudiante;
    private Curso cursoPublicado;
    private Curso cursoDraft;

    @BeforeEach
    void setUp() throws Exception {
        idInstructor = UUID.randomUUID();
        idEstudiante = UUID.randomUUID();

        cursoPublicado = buildCurso("Arquitectura de Software", EstadoCurso.PUBLISHED, idInstructor, 50);
        cursoDraft     = buildCurso("Borrador Inicial",         EstadoCurso.DRAFT,     idInstructor, 20);

        servicioMatriculas = new ServicioMatriculas();
        setField(servicioMatriculas, "repositorioMatriculas", repoMatriculas);
        setField(servicioMatriculas, "repositorioCursos",     repoCursos);
        setField(servicioMatriculas, "publicadorEventos",     publicadorEventos);
        setField(servicioMatriculas, "self",                  servicioMatriculas);
    }

    // ── TF-007: Creación de curso ─────────────────────────────────────────────

    @Test
    @DisplayName("TF-007 · Instructor crea curso → estado DRAFT por defecto")
    void tf007_crearCurso_estadoDraftPorDefecto() {
        when(repoCursos.guardar(any(Curso.class))).thenAnswer(inv -> {
            Curso c = inv.getArgument(0);
            setField(c, "id", UUID.randomUUID());
            setField(c, "creadoEn", Instant.now());
            setField(c, "actualizadoEn", Instant.now());
            return c;
        });

        SolicitudCurso solicitud = new SolicitudCurso(
                "Nuevas Tecnologías", "Descripción completa", 30, null);

        RespuestaCurso respuesta = servicioCursos.crear(solicitud, idInstructor);

        assertThat(respuesta.titulo()).isEqualTo("Nuevas Tecnologías");
        assertThat(respuesta.idInstructor()).isEqualTo(idInstructor);
        assertThat(respuesta.estado()).isEqualTo(EstadoCurso.DRAFT);
        assertThat(respuesta.id()).isNotNull();
    }

    // ── TF-008: Actualización de curso ────────────────────────────────────────

    @Test
    @DisplayName("TF-008 · Instructor actualiza solo el título del curso")
    void tf008_actualizarCurso_soloTituloModificado() {
        when(repoCursos.buscarPorId(cursoDraft.getId())).thenReturn(Optional.of(cursoDraft));
        when(repoCursos.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        SolicitudCurso solicitudActualizacion = new SolicitudCurso(
                "Título Actualizado", null, null, null); // titulo, descripcion, maxEst, estado

        RespuestaCurso respuesta = servicioCursos.actualizar(
                cursoDraft.getId(), solicitudActualizacion, idInstructor);

        assertThat(respuesta.titulo()).isEqualTo("Título Actualizado");
        assertThat(respuesta.descripcion()).isEqualTo("Descripción de Borrador Inicial");
    }

    // ── TF-009: Propietario único ─────────────────────────────────────────────

    @Test
    @DisplayName("TF-009 · Instructor no propietario no puede actualizar → 403 Forbidden")
    void tf009_instructorNoProperties_noActualiza() {
        UUID otroInstructor = UUID.randomUUID();
        when(repoCursos.buscarPorId(cursoDraft.getId())).thenReturn(Optional.of(cursoDraft));

        SolicitudCurso solicitud = new SolicitudCurso("Hack", null, null, null); // titulo,desc,max,estado

        assertThatThrownBy(() ->
                servicioCursos.actualizar(cursoDraft.getId(), solicitud, otroInstructor))
                .isInstanceOf(ForbiddenException.class);

        verify(repoCursos, never()).guardar(any());
    }

    @Test
    @DisplayName("TF-009-B · Eliminar curso publicado lanza BadRequest")
    void tf009B_eliminarCursoPublicado_lanzaExcepcion() {
        when(repoCursos.buscarPorId(cursoPublicado.getId()))
                .thenReturn(Optional.of(cursoPublicado));

        assertThatThrownBy(() ->
                servicioCursos.eliminar(cursoPublicado.getId(), idInstructor))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("publicado");
    }

    // ── TF-010: Inscripción exitosa ───────────────────────────────────────────

    @Test
    @DisplayName("TF-010 · Estudiante inscrito a curso PUBLISHED → matrícula ACTIVE + evento RabbitMQ")
    void tf010_inscripcion_exitosa_activa_yPublicaEvento() throws Exception {
        when(repoCursos.buscarPorId(cursoPublicado.getId()))
                .thenReturn(Optional.of(cursoPublicado));
        when(repoMatriculas.estaMatriculado(idEstudiante, cursoPublicado.getId()))
                .thenReturn(false);
        when(repoMatriculas.contarPorCurso(cursoPublicado.getId())).thenReturn(10L);
        when(repoMatriculas.guardar(any(Matricula.class))).thenAnswer(inv -> {
            Matricula m = inv.getArgument(0);
            setField(m, "id", UUID.randomUUID());
            setField(m, "matriculadoEn", Instant.now());
            return m;
        });

        RespuestaMatricula resp = servicioMatriculas.matricular(
                idEstudiante, cursoPublicado.getId());

        assertThat(resp.estado()).isEqualTo(EstadoMatricula.ACTIVE);
        assertThat(resp.tituloCurso()).isEqualTo("Arquitectura de Software");
        assertThat(resp.idUsuario()).isEqualTo(idEstudiante);
        assertThat(resp.porcentajeProgreso()).isZero();

        // Debe publicar evento CourseEnrolled en RabbitMQ (analíticas)
        verify(publicadorEventos, times(1)).publicarAnaliticas(any());
    }

    // ── TF-011: Cancelar matrícula ────────────────────────────────────────────

    @Test
    @DisplayName("TF-011 · Cancelar matrícula existente → soft delete")
    void tf011_cancelarMatricula_softDelete() throws Exception {
        Matricula matricula = buildMatricula(idEstudiante, cursoPublicado);
        when(repoMatriculas.buscarPorUsuarioYCurso(idEstudiante, cursoPublicado.getId()))
                .thenReturn(Optional.of(matricula));
        when(repoMatriculas.fusionar(any())).thenAnswer(inv -> inv.getArgument(0));

        servicioMatriculas.cancelar(idEstudiante, cursoPublicado.getId());

        assertThat(matricula.obtenerEstado()).isEqualTo(EstadoMatricula.CANCELLED);
        assertThat(matricula.estaEliminada()).isTrue();
        verify(repoMatriculas).fusionar(matricula);
    }

    @Test
    @DisplayName("TF-011-B · Actualizar progreso a 100% → estado COMPLETED automático")
    void tf011B_progreso100_completaMatricula() throws Exception {
        Matricula matricula = buildMatricula(idEstudiante, cursoPublicado);
        when(repoMatriculas.buscarPorUsuarioYCurso(idEstudiante, cursoPublicado.getId()))
                .thenReturn(Optional.of(matricula));
        when(repoMatriculas.fusionar(any())).thenAnswer(inv -> inv.getArgument(0));

        RespuestaMatricula resp = servicioMatriculas.actualizarProgreso(
                idEstudiante, cursoPublicado.getId(), 100.0);

        assertThat(resp.estado()).isEqualTo(EstadoMatricula.COMPLETED);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Curso buildCurso(String titulo, EstadoCurso estado,
                              UUID idInstructor, int max) {
        Curso c = new Curso();
        c.establecerTitulo(titulo);
        c.establecerDescripcion("Descripción de " + titulo);
        c.establecerIdInstructor(idInstructor);
        c.establecerEstado(estado);
        c.establecerMaxEstudiantes(max);
        setFieldSilent(c, "id",            UUID.randomUUID());
        setFieldSilent(c, "creadoEn",      Instant.now());
        setFieldSilent(c, "actualizadoEn", Instant.now());
        return c;
    }

    private Matricula buildMatricula(UUID idUsuario, Curso curso) {
        Matricula m = new Matricula();
        m.establecerIdUsuario(idUsuario);
        m.establecerCurso(curso);
        setFieldSilent(m, "id",            UUID.randomUUID());
        setFieldSilent(m, "matriculadoEn", Instant.now());
        return m;
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field f = findField(target.getClass(), name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private void setFieldSilent(Object o, String n, Object v) {
        try { setField(o, n, v); } catch (Exception e) { throw new RuntimeException(e); }
    }

    private Field findField(Class<?> cls, String name) throws NoSuchFieldException {
        try { return cls.getDeclaredField(name); }
        catch (NoSuchFieldException e) {
            if (cls.getSuperclass() != null) return findField(cls.getSuperclass(), name);
            throw e;
        }
    }
}
