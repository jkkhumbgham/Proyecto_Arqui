package com.puj.cursos.integracion;

import com.puj.cursos.cursos.dominio.Curso;
import com.puj.cursos.cursos.dominio.EstadoCurso;
import com.puj.cursos.cursos.dominio.RepositorioCursos;
import com.puj.cursos.cursos.aplicacion.ServicioCursos;
import com.puj.cursos.cursos.interfaces.dto.SolicitudCurso;
import com.puj.cursos.cursos.interfaces.dto.RespuestaCurso;
import com.puj.cursos.matriculas.dominio.EstadoMatricula;
import com.puj.cursos.matriculas.dominio.Matricula;
import com.puj.cursos.matriculas.dominio.RepositorioMatriculas;
import com.puj.cursos.matriculas.aplicacion.ServicioMatriculas;
import com.puj.eventos.publicador.PublicadorEventos;
import jakarta.ws.rs.BadRequestException;
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
 * TI-003..005 — Pruebas de integración del course-service.
 *
 * <p>Verifica el ciclo de vida de cursos y matrículas usando mocks de repositorios:
 * <ul>
 *   <li>TI-003: Curso en estado DRAFT → publicación con módulo</li>
 *   <li>TI-004: Inscripción a curso publicado → estado ACTIVE</li>
 *   <li>TI-005: Inscripción duplicada → BadRequestException</li>
 * </ul>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TI-003..005 · Integración course-service")
class MatriculaIntegracionTest {

    // ── ServicioCursos ────────────────────────────────────────────────────────
    @Mock private RepositorioCursos    repoCursos;
    @InjectMocks private ServicioCursos servicioCursos;

    // ── ServicioMatriculas ────────────────────────────────────────────────────
    @Mock private RepositorioMatriculas repoMatriculas;
    @Mock private PublicadorEventos     publicadorEventos;
    // ServicioMatriculas también necesita RepositorioCursos
    private ServicioMatriculas servicioMatriculas;

    private UUID idInstructor;
    private UUID idEstudiante;
    private Curso cursoPublicado;

    @BeforeEach
    void setUp() throws Exception {
        idInstructor = UUID.randomUUID();
        idEstudiante = UUID.randomUUID();

        cursoPublicado = buildCurso("Arquitectura de Software",
                EstadoCurso.PUBLISHED, idInstructor, 30);

        // Construcción manual de ServicioMatriculas (CDI no disponible en tests)
        servicioMatriculas = new ServicioMatriculas();
        setField(servicioMatriculas, "repositorioMatriculas", repoMatriculas);
        setField(servicioMatriculas, "repositorioCursos",     repoCursos);
        setField(servicioMatriculas, "publicadorEventos",     publicadorEventos);
        setField(servicioMatriculas, "self",                  servicioMatriculas);
    }

    // ── TI-003: Publicación de curso ──────────────────────────────────────────

    @Test
    @DisplayName("TI-003-A · Curso DRAFT sin módulos no puede publicarse")
    void ti003A_cursoDraftSinModulos_noSePuedePublicar() {
        Curso cursoDraft = buildCurso("Sin Módulos", EstadoCurso.DRAFT, idInstructor, 10);
        when(repoCursos.buscarPorId(cursoDraft.getId())).thenReturn(Optional.of(cursoDraft));

        assertThatThrownBy(() -> servicioCursos.publicar(cursoDraft.getId(), idInstructor))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("módulo");
    }

    @Test
    @DisplayName("TI-003-B · Instructor no propietario no puede publicar → ForbiddenException")
    void ti003B_instructorNoPopietario_noPublica() {
        UUID otroInstructor = UUID.randomUUID();
        when(repoCursos.buscarPorId(cursoPublicado.getId()))
                .thenReturn(Optional.of(cursoPublicado));

        assertThatThrownBy(() -> servicioCursos.publicar(cursoPublicado.getId(), otroInstructor))
                .isInstanceOf(jakarta.ws.rs.ForbiddenException.class);
    }

    @Test
    @DisplayName("TI-003-C · Buscar curso por ID existente devuelve DTO correcto")
    void ti003C_buscarCursoPorId_devuelveDTO() {
        when(repoCursos.buscarPorId(cursoPublicado.getId()))
                .thenReturn(Optional.of(cursoPublicado));

        RespuestaCurso dto = servicioCursos.buscarPorId(cursoPublicado.getId());

        assertThat(dto.titulo()).isEqualTo("Arquitectura de Software");
        assertThat(dto.estado()).isEqualTo(EstadoCurso.PUBLISHED);
    }

    @Test
    @DisplayName("TI-003-D · Buscar curso inexistente lanza NotFoundException")
    void ti003D_cursoInexistente_lanzaNotFoundException() {
        UUID idFalso = UUID.randomUUID();
        when(repoCursos.buscarPorId(idFalso)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicioCursos.buscarPorId(idFalso))
                .isInstanceOf(NotFoundException.class);
    }

    // ── TI-004: Inscripción exitosa ───────────────────────────────────────────

    @Test
    @DisplayName("TI-004 · Inscripción a curso PUBLISHED → matrícula ACTIVE")
    void ti004_inscripcion_exitosa_creaMatriculaActive() throws Exception {
        when(repoCursos.buscarPorId(cursoPublicado.getId()))
                .thenReturn(Optional.of(cursoPublicado));
        when(repoMatriculas.estaMatriculado(idEstudiante, cursoPublicado.getId()))
                .thenReturn(false);
        when(repoMatriculas.contarPorCurso(cursoPublicado.getId())).thenReturn(5L);
        when(repoMatriculas.guardar(any(Matricula.class))).thenAnswer(inv -> {
            Matricula m = inv.getArgument(0);
            setField(m, "id", UUID.randomUUID());
            setField(m, "matriculadoEn", Instant.now());
            return m;
        });

        var respuesta = servicioMatriculas.matricular(idEstudiante, cursoPublicado.getId());

        assertThat(respuesta).isNotNull();
        assertThat(respuesta.tituloCurso()).isEqualTo("Arquitectura de Software");
        assertThat(respuesta.estado()).isEqualTo(EstadoMatricula.ACTIVE);
        verify(publicadorEventos, times(1)).publicarAnaliticas(any());
    }

    // ── TI-005: Inscripción duplicada ─────────────────────────────────────────

    @Test
    @DisplayName("TI-005 · Inscripción duplicada lanza BadRequestException")
    void ti005_inscripcionDuplicada_lanzaExcepcion() {
        when(repoCursos.buscarPorId(cursoPublicado.getId()))
                .thenReturn(Optional.of(cursoPublicado));
        when(repoMatriculas.estaMatriculado(idEstudiante, cursoPublicado.getId()))
                .thenReturn(true);

        assertThatThrownBy(() ->
                servicioMatriculas.matricular(idEstudiante, cursoPublicado.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("matriculado");
    }

    @Test
    @DisplayName("TI-005-B · Inscripción a curso DRAFT lanza BadRequestException")
    void ti005B_cursoNoPublicado_lanzaExcepcion() {
        Curso cursoDraft = buildCurso("Borrador", EstadoCurso.DRAFT, idInstructor, 30);
        when(repoCursos.buscarPorId(cursoDraft.getId()))
                .thenReturn(Optional.of(cursoDraft));

        assertThatThrownBy(() ->
                servicioMatriculas.matricular(idEstudiante, cursoDraft.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("disponible");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Curso buildCurso(String titulo, EstadoCurso estado,
                              UUID idInstructor, int maxEstudiantes) {
        Curso c = new Curso();
        c.establecerTitulo(titulo);
        c.establecerDescripcion("Descripción de " + titulo);
        c.establecerIdInstructor(idInstructor);
        c.establecerEstado(estado);
        c.establecerMaxEstudiantes(maxEstudiantes);
        setFieldSilent(c, "id", UUID.randomUUID());
        setFieldSilent(c, "creadoEn", Instant.now());
        setFieldSilent(c, "actualizadoEn", Instant.now());
        return c;
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field f = findField(target.getClass(), name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private void setFieldSilent(Object target, String name, Object value) {
        try { setField(target, name, value); } catch (Exception e) { throw new RuntimeException(e); }
    }

    private Field findField(Class<?> cls, String name) throws NoSuchFieldException {
        try {
            return cls.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            if (cls.getSuperclass() != null) return findField(cls.getSuperclass(), name);
            throw e;
        }
    }
}
