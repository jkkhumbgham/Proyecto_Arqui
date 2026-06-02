package com.puj.colaboracion.aceptacion.steps;

import com.puj.colaboracion.foros.dominio.Foro;
import com.puj.colaboracion.foros.dominio.Hilo;
import com.puj.colaboracion.foros.dominio.Publicacion;
import com.puj.colaboracion.foros.dominio.RepositorioForos;
import com.puj.colaboracion.foros.interfaces.RecursoForos;
import com.puj.colaboracion.foros.interfaces.dto.SolicitudHilo;
import com.puj.colaboracion.foros.interfaces.dto.SolicitudPublicacion;
import com.puj.colaboracion.gruposestudio.aplicacion.ServicioGruposEstudio;
import com.puj.colaboracion.gruposestudio.dominio.GrupoEstudio;
import com.puj.colaboracion.gruposestudio.dominio.MiembroGrupo;
import com.puj.colaboracion.gruposestudio.dominio.RepositorioGruposEstudio;
import com.puj.colaboracion.resiliencia.DisyuntorCircuito;
import com.puj.seguridad.rbac.UsuarioAutenticado;
import io.cucumber.java.es.*;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Steps de Cucumber para Colaboración (TA-08, TA-09).
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public class ColaboracionSteps {

    private final RepositorioForos       mockRepoForos  = mock(RepositorioForos.class);
    private final RepositorioGruposEstudio mockRepoGrupos = mock(RepositorioGruposEstudio.class);
    private final UsuarioAutenticado       mockUsuario    = mock(UsuarioAutenticado.class);
    private final DisyuntorCircuito        mockDisyuntor  = mock(DisyuntorCircuito.class);

    private final RecursoForos recursoForos;
    private final ServicioGruposEstudio servicioGrupos;

    // Estado del escenario
    private UUID idForo;
    private UUID idHilo;
    private UUID idCurso;
    private UUID idEstudiante;
    private UUID idInstructor;
    private Foro foro;
    private Hilo hilo;
    private GrupoEstudio grupo;
    private Exception excepcion;

    public ColaboracionSteps() throws Exception {
        recursoForos = new RecursoForos();
        setField(recursoForos, "repoForos", mockRepoForos);
        setField(recursoForos, "usuarioAutenticado", mockUsuario);

        servicioGrupos = new ServicioGruposEstudio();
        setField(servicioGrupos, "repo", mockRepoGrupos);
        setField(servicioGrupos, "usuarioActual", mockUsuario);
        setField(servicioGrupos, "disyuntorEvaluaciones", mockDisyuntor);

        idEstudiante = UUID.randomUUID();
        idInstructor = UUID.randomUUID();
        idCurso = UUID.randomUUID();
    }

    // ── Dado ──────────────────────────────────────────────────────────────────

    @Dado("que existe un foro activo")
    public void queExisteUnForoActivo() {
        idForo = UUID.randomUUID();
        foro = new Foro();
        setFieldSilent(foro, "id", idForo);
        foro.setNombre("Foro General");
        foro.setIdCurso(idCurso);
        foro.setCreadoPor(idInstructor);

        when(mockRepoForos.buscarPorId(idForo)).thenReturn(Optional.of(foro));
    }

    @Y("el estudiante tiene sesión iniciada")
    public void elEstudianteTieneSesionIniciada() {
        when(mockUsuario.obtenerIdUsuario()).thenReturn(idEstudiante.toString());
    }

    @Y("el hilo {string} ha sido bloqueado por el moderador")
    public void elHiloHaSidoBloqueado(String titulo) {
        idHilo = UUID.randomUUID();
        hilo = new Hilo();
        setFieldSilent(hilo, "id", idHilo);
        hilo.setForo(foro);
        hilo.setTitulo(titulo);
        hilo.setIdAutor(idEstudiante);
        hilo.setBloqueado(true);

        when(mockRepoForos.buscarHiloPorId(idHilo)).thenReturn(Optional.of(hilo));
    }

    @Dado("que existe un curso publicado")
    public void queExisteUnCursoPublicado() {
        // Nada específico, el ID de curso es suficiente
        when(mockUsuario.obtenerIdUsuario()).thenReturn(idEstudiante.toString());
    }

    @Dado("que existe un grupo de estudio con capacidad máxima de {int} miembros")
    public void queExisteGrupoEstudioConCapacidad(int capacidad) {
        grupo = new GrupoEstudio();
        setFieldSilent(grupo, "id", UUID.randomUUID());
        grupo.setNombre("Grupo Capacidad");
        grupo.setIdCurso(idCurso);
        grupo.setCreadoPor(idEstudiante);
        grupo.setMaxMiembros(capacidad);

        when(mockUsuario.obtenerIdUsuario()).thenReturn(UUID.randomUUID().toString()); // Otro estudiante
        when(mockRepoGrupos.buscarPorId(grupo.getId())).thenReturn(Optional.of(grupo));
        when(mockRepoGrupos.esMiembro(eq(grupo.getId()), any(UUID.class))).thenReturn(false);
    }

    @Y("el grupo ya tiene {int} miembros activos")
    public void elGrupoYaTieneMiembros(int count) {
        List<MiembroGrupo> miembros = List.of(
                new MiembroGrupo(), new MiembroGrupo(), new MiembroGrupo()
        ).subList(0, count);
        when(mockRepoGrupos.buscarMiembrosActivos(grupo.getId())).thenReturn(miembros);
    }

    // ── Cuando ───────────────────────────────────────────────────────────────

    @Cuando("el estudiante crea un hilo con título {string} y contenido {string}")
    public void elEstudianteCreaUnHilo(String titulo, String contenido) {
        when(mockRepoForos.guardarHilo(any(Hilo.class))).thenAnswer(inv -> {
            Hilo h = inv.getArgument(0);
            setField(h, "id", UUID.randomUUID());
            return h;
        });
        try {
            recursoForos.crearHilo(idForo, new SolicitudHilo(titulo, contenido));
        } catch (Exception e) {
            excepcion = e;
        }
    }

    @Cuando("el estudiante intenta publicar una respuesta en el hilo")
    public void elEstudianteIntentaPublicarRespuesta() {
        try {
            recursoForos.crearPublicacion(idHilo, new SolicitudPublicacion("respuesta"));
        } catch (Exception e) {
            excepcion = e;
        }
    }

    @Cuando("un estudiante crea el grupo de estudio {string}")
    public void unEstudianteCreaElGrupo(String nombre) {
        try {
            grupo = servicioGrupos.crear(nombre, idCurso, 5);
        } catch (Exception e) {
            excepcion = e;
        }
    }

    @Cuando("otro estudiante intenta unirse al grupo")
    public void otroEstudianteIntentaUnirse() {
        try {
            servicioGrupos.unirse(grupo.getId());
        } catch (Exception e) {
            excepcion = e;
        }
    }

    // ── Entonces ─────────────────────────────────────────────────────────────

    @Entonces("el hilo se crea exitosamente")
    public void elHiloSeCreaExitosamente() {
        assertThat(excepcion).isNull();
        verify(mockRepoForos, times(1)).guardarHilo(any(Hilo.class));
    }

    @Y("la primera publicación contiene {string}")
    public void laPrimeraPublicacionContiene(String contenido) {
        verify(mockRepoForos, times(1)).guardarPublicacion(argThat(p ->
                p.getContenido().equals(contenido)
        ));
    }

    @Entonces("recibe un error indicando que el hilo está cerrado")
    public void recibeUnErrorIndicandoHiloCerrado() {
        assertThat(excepcion).isNotNull();
        assertThat(excepcion.getMessage()).contains("cerrado");
    }

    @Entonces("el grupo se crea exitosamente")
    public void elGrupoSeCreaExitosamente() {
        assertThat(excepcion).isNull();
        verify(mockRepoGrupos, times(1)).guardar(any(GrupoEstudio.class));
    }

    @Y("el creador es asignado como tutor del grupo automáticamente")
    public void elCreadorEsAsignadoComoTutor() {
        verify(mockRepoGrupos, times(1)).agregarMiembro(argThat(MiembroGrupo::esTutor));
    }

    @Entonces("recibe un error indicando que el grupo está lleno")
    public void recibeErrorGrupoLleno() {
        assertThat(excepcion).isNotNull();
        assertThat(excepcion.getMessage()).contains("lleno");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
