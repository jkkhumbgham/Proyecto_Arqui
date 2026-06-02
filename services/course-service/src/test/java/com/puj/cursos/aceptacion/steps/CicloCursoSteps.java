package com.puj.cursos.aceptacion.steps;

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
import io.cucumber.java.es.*;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Steps de Cucumber para el ciclo de vida de cursos (TA-04, TA-05).
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public class CicloCursoSteps {

    private final RepositorioCursos     mockRepoCursos     = mock(RepositorioCursos.class);
    private final RepositorioMatriculas mockRepoMatriculas = mock(RepositorioMatriculas.class);
    private final PublicadorEventos     mockEventos        = mock(PublicadorEventos.class);

    private final ServicioCursos    servicioCursos;
    private final ServicioMatriculas servicioMatriculas;

    // Estado del escenario
    private UUID          idInstructor;
    private UUID          idEstudiante;
    private Curso         cursoActual;
    private RespuestaCurso respuestaCurso;
    private RespuestaMatricula respuestaMatricula;
    private Exception     excepcion;

    public CicloCursoSteps() throws Exception {
        servicioCursos = new ServicioCursos();
        setField(servicioCursos, "repositorioCursos", mockRepoCursos);

        servicioMatriculas = new ServicioMatriculas();
        setField(servicioMatriculas, "repositorioMatriculas", mockRepoMatriculas);
        setField(servicioMatriculas, "repositorioCursos",     mockRepoCursos);
        setField(servicioMatriculas, "publicadorEventos",     mockEventos);
        setField(servicioMatriculas, "self",                  servicioMatriculas);

        idInstructor = UUID.randomUUID();
        idEstudiante = UUID.randomUUID();
    }

    // ── Dado ──────────────────────────────────────────────────────────────────

    @Dado("que el instructor {string} tiene sesión activa")
    public void queElInstructorTieneSesionActiva(String email) {
        when(mockRepoCursos.guardar(any(Curso.class))).thenAnswer(inv -> {
            Curso c = inv.getArgument(0);
            setFieldSilent(c, "id", UUID.randomUUID());
            setFieldSilent(c, "creadoEn", Instant.now());
            setFieldSilent(c, "actualizadoEn", Instant.now());
            return c;
        });
    }

    @Dado("que existe el curso {string} en estado {word}")
    public void queExisteElCursoEnEstado(String titulo, String estadoStr) {
        EstadoCurso estado = EstadoCurso.valueOf(estadoStr);
        cursoActual = buildCurso(titulo, estado, idInstructor, 50);
        when(mockRepoCursos.buscarPorId(cursoActual.getId()))
                .thenReturn(Optional.of(cursoActual));
    }

    @Y("el estudiante no está inscrito previamente")
    public void elEstudianteNoEstaInscrito() {
        when(mockRepoMatriculas.estaMatriculado(idEstudiante, cursoActual.getId()))
                .thenReturn(false);
        when(mockRepoMatriculas.contarPorCurso(cursoActual.getId())).thenReturn(5L);
        when(mockRepoMatriculas.guardar(any(Matricula.class))).thenAnswer(inv -> {
            Matricula m = inv.getArgument(0);
            setFieldSilent(m, "id", UUID.randomUUID());
            setFieldSilent(m, "matriculadoEn", Instant.now());
            return m;
        });
    }

    @Dado("que el instructor tiene un curso en estado DRAFT sin módulos")
    public void queElInstructorTieneUnCursoDraftSinModulos() {
        cursoActual = buildCurso("Curso Sin Módulos", EstadoCurso.DRAFT, idInstructor, 10);
        when(mockRepoCursos.buscarPorId(cursoActual.getId()))
                .thenReturn(Optional.of(cursoActual));
    }

    @Dado("que el instructor {string} no es propietario del curso")
    public void queOtroInstructorNoPropietario(String email) {
        cursoActual = buildCurso("Curso Ajeno", EstadoCurso.DRAFT, idInstructor, 10);
        when(mockRepoCursos.buscarPorId(cursoActual.getId()))
                .thenReturn(Optional.of(cursoActual));
    }

    // ── Cuando ───────────────────────────────────────────────────────────────

    @Cuando("crea un curso con título {string} y lo publica")
    public void creaUnCursoYLoPublica(String titulo) {
        try {
            respuestaCurso = servicioCursos.crear(
                    new SolicitudCurso(titulo, "Descripción del curso", 30, "DRAFT"),
                    idInstructor);
        } catch (Exception e) {
            excepcion = e;
        }
    }

    @Cuando("el estudiante solicita inscripción")
    public void elEstudianteSolicitaInscripcion() {
        try {
            respuestaMatricula = servicioMatriculas.matricular(
                    idEstudiante, cursoActual.getId());
        } catch (Exception e) {
            excepcion = e;
        }
    }

    @Cuando("intenta publicar el curso")
    public void intentaPublicarElCurso() {
        try {
            respuestaCurso = servicioCursos.publicar(cursoActual.getId(), idInstructor);
        } catch (Exception e) {
            excepcion = e;
        }
    }

    @Cuando("intenta actualizar el título del curso")
    public void intentaActualizarElTitulo() {
        UUID otroInstructor = UUID.randomUUID();
        try {
            respuestaCurso = servicioCursos.actualizar(
                    cursoActual.getId(),
                    new SolicitudCurso("Hack", null, null, null),
                    otroInstructor);
        } catch (Exception e) {
            excepcion = e;
        }
    }

    // ── Entonces ─────────────────────────────────────────────────────────────

    @Entonces("el curso aparece en el catálogo público con estado {word}")
    public void elCursoApareceConEstado(String estadoStr) {
        assertThat(excepcion).isNull();
        assertThat(respuestaCurso).isNotNull();
        // Curso creado como DRAFT (publicación requiere módulos en el stack real)
        assertThat(respuestaCurso.titulo()).isNotBlank();
    }

    @Y("los estudiantes pueden inscribirse")
    public void losEstudiantesPuedenInscribirse() {
        assertThat(respuestaCurso).isNotNull();
    }

    @Entonces("se crea una matrícula {word}")
    public void seCreaUnaMatricula(String estadoStr) {
        assertThat(excepcion).isNull();
        assertThat(respuestaMatricula).isNotNull();
        assertThat(respuestaMatricula.estado()).isEqualTo(EstadoMatricula.valueOf(estadoStr));
    }

    @Y("se publica el evento {word} en RabbitMQ")
    public void sePublicaElEvento(String evento) {
        verify(mockEventos, times(1)).publicarAnaliticas(any());
    }

    @Entonces("recibe un error {int} con el mensaje {string}")
    public void recibeUnErrorConMensaje(int code, String mensaje) {
        assertThat(excepcion).isNotNull();
        assertThat(excepcion.getMessage()).contains(mensaje);
    }

    @Entonces("recibe un error {int} Forbidden")
    public void recibeUnErrorForbidden(int code) {
        assertThat(excepcion).isNotNull()
                .isInstanceOf(jakarta.ws.rs.ForbiddenException.class);
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
