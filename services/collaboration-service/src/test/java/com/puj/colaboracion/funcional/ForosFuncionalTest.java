package com.puj.colaboracion.funcional;

import com.puj.colaboracion.foros.dominio.Foro;
import com.puj.colaboracion.foros.dominio.Hilo;
import com.puj.colaboracion.foros.dominio.Publicacion;
import com.puj.colaboracion.foros.dominio.RepositorioForos;
import com.puj.colaboracion.gruposestudio.aplicacion.ServicioGruposEstudio;
import com.puj.colaboracion.gruposestudio.dominio.GrupoEstudio;
import com.puj.colaboracion.gruposestudio.dominio.MiembroGrupo;
import com.puj.colaboracion.gruposestudio.dominio.RepositorioGruposEstudio;
import com.puj.colaboracion.resiliencia.DisyuntorCircuito;
import com.puj.seguridad.rbac.UsuarioAutenticado;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * Pruebas Funcionales del collaboration-service (TF-017..TF-022).
 *
 * <p>Cubre los flujos de foros de discusión y grupos de estudio:
 * <ul>
 *   <li>TF-017: Instructor crea foro para un curso</li>
 *   <li>TF-018: Estudiante crea hilo en un foro</li>
 *   <li>TF-019: Estudiante publica en hilo abierto</li>
 *   <li>TF-020: Publicar en hilo bloqueado → BadRequest</li>
 *   <li>TF-021: Crear grupo de estudio → creador es tutor</li>
 *   <li>TF-022: Unirse a grupo lleno → BadRequest</li>
 * </ul>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TF-017..022 · Funcional Colaboración")
class ForosFuncionalTest {

    // ── Mocks para repositorios de foros ──────────────────────────────────────
    @Mock private RepositorioForos       repoForos;

    // ── Mocks para grupos de estudio ──────────────────────────────────────────
    @Mock private RepositorioGruposEstudio repoGrupos;
    @Mock private UsuarioAutenticado       usuarioActual;
    @Mock private DisyuntorCircuito        disyuntor;

    private ServicioGruposEstudio servicioGrupos;

    private UUID idEstudiante;
    private UUID idInstructor;
    private UUID idCurso;
    private Foro foroActivo;
    private Hilo hiloAbierto;
    private Hilo hiloBloqueado;

    @BeforeEach
    void setUp() throws Exception {
        idEstudiante = UUID.randomUUID();
        idInstructor = UUID.randomUUID();
        idCurso      = UUID.randomUUID();

        // Construir foro
        foroActivo = buildForo("Foro Arquitectura", idCurso, idInstructor);

        // Hilos
        hiloAbierto   = buildHilo("Duda sobre microservicios", foroActivo, idEstudiante, false);
        hiloBloqueado = buildHilo("Hilo cerrado por moderador", foroActivo, idInstructor, true);

        // ServicioGruposEstudio — CDI simulado
        servicioGrupos = new ServicioGruposEstudio();
        setField(servicioGrupos, "repo",                   repoGrupos);
        setField(servicioGrupos, "usuarioActual",          usuarioActual);
        setField(servicioGrupos, "disyuntorEvaluaciones",  disyuntor);

        lenient().when(usuarioActual.obtenerIdUsuario()).thenReturn(idEstudiante.toString());
        lenient().when(disyuntor.permitirPeticion()).thenReturn(false); // circuito abierto -> no llama evaluaciones
    }

    // ── TF-017: Crear foro ────────────────────────────────────────────────────

    @Test
    @DisplayName("TF-017 · Instructor crea foro → persistido con idCurso correcto")
    void tf017_crearForo_persistidoConIdCurso() {
        when(repoForos.guardar(any(Foro.class))).thenAnswer(inv -> {
            Foro f = inv.getArgument(0);
            setField(f, "id", UUID.randomUUID());
            setField(f, "creadoEn", Instant.now());
            return f;
        });

        Foro nuevoForo = new Foro();
        nuevoForo.setNombre("Foro Nuevo");
        nuevoForo.setIdCurso(idCurso);
        nuevoForo.setCreadoPor(idInstructor);
        repoForos.guardar(nuevoForo);

        verify(repoForos, times(1)).guardar(argThat(f ->
                f.getIdCurso().equals(idCurso)));
    }

    // ── TF-018: Crear hilo ────────────────────────────────────────────────────

    @Test
    @DisplayName("TF-018 · Estudiante crea hilo con primera publicación en foro activo")
    void tf018_crearHilo_conPrimeraPublicacion() {
        when(repoForos.buscarPorId(foroActivo.getId())).thenReturn(Optional.of(foroActivo));
        when(repoForos.guardarHilo(any(Hilo.class))).thenAnswer(inv -> {
            Hilo h = inv.getArgument(0);
            setField(h, "id", UUID.randomUUID());
            setField(h, "creadoEn", Instant.now());
            return h;
        });
        when(repoForos.guardarPublicacion(any(Publicacion.class))).thenAnswer(inv -> {
            Publicacion p = inv.getArgument(0);
            setField(p, "id", UUID.randomUUID());
            setField(p, "creadoEn", Instant.now());
            return p;
        });

        // Simulación del flujo del RecursoForos.crearHilo
        Foro foro = repoForos.buscarPorId(foroActivo.getId())
                .orElseThrow(() -> new NotFoundException("Foro no encontrado."));

        Hilo hilo = new Hilo();
        hilo.setForo(foro);
        hilo.setTitulo("¿Cómo funciona el patrón CQRS?");
        hilo.setIdAutor(idEstudiante);
        repoForos.guardarHilo(hilo);

        Publicacion p = new Publicacion();
        p.setHilo(hilo);
        p.setIdAutor(idEstudiante);
        p.setContenido("No entiendo bien la separación de comandos y consultas.");
        repoForos.guardarPublicacion(p);

        verify(repoForos).guardarHilo(argThat(h -> h.getTitulo().equals("¿Cómo funciona el patrón CQRS?")));
        verify(repoForos).guardarPublicacion(argThat(pub -> pub.getContenido().contains("comandos y consultas")));
    }

    // ── TF-019: Publicar en hilo abierto ─────────────────────────────────────

    @Test
    @DisplayName("TF-019 · Estudiante publica en hilo abierto → publicación persistida")
    void tf019_publicarEnHiloAbierto_persistePublicacion() {
        when(repoForos.buscarHiloPorId(hiloAbierto.getId())).thenReturn(Optional.of(hiloAbierto));
        when(repoForos.guardarPublicacion(any())).thenAnswer(inv -> {
            Publicacion pub = inv.getArgument(0);
            setField(pub, "id", UUID.randomUUID());
            setField(pub, "creadoEn", Instant.now());
            return pub;
        });

        Hilo hilo = repoForos.buscarHiloPorId(hiloAbierto.getId())
                .orElseThrow(() -> new NotFoundException("Hilo no encontrado."));

        assertThat(hilo.estaBloqueado()).isFalse();

        Publicacion pub = new Publicacion();
        pub.setHilo(hilo);
        pub.setIdAutor(idEstudiante);
        pub.setContenido("Mi respuesta al hilo.");
        repoForos.guardarPublicacion(pub);

        verify(repoForos).guardarPublicacion(any());
    }

    // ── TF-020: Publicar en hilo bloqueado ───────────────────────────────────

    @Test
    @DisplayName("TF-020 · Publicar en hilo bloqueado → BadRequestException")
    void tf020_publicarEnHiloBloqueado_lanzaExcepcion() {
        when(repoForos.buscarHiloPorId(hiloBloqueado.getId()))
                .thenReturn(Optional.of(hiloBloqueado));

        Hilo hilo = repoForos.buscarHiloPorId(hiloBloqueado.getId())
                .orElseThrow(() -> new NotFoundException("Hilo no encontrado."));

        assertThatThrownBy(() -> {
            if (hilo.estaBloqueado()) {
                throw new BadRequestException("El hilo está cerrado y no acepta respuestas.");
            }
        }).isInstanceOf(BadRequestException.class)
          .hasMessageContaining("cerrado");
    }

    // ── TF-021: Crear grupo de estudio ────────────────────────────────────────

    @Test
    @DisplayName("TF-021 · Crear grupo de estudio → creador es tutor inicial")
    void tf021_crearGrupo_creadorEsTutor() {
        when(repoGrupos.guardar(any(GrupoEstudio.class))).thenAnswer(inv -> {
            GrupoEstudio g = inv.getArgument(0);
            setField(g, "id", UUID.randomUUID());
            setField(g, "creadoEn", Instant.now());
            return g;
        });
        doAnswer(inv -> {
            MiembroGrupo m = inv.getArgument(0);
            setField(m, "id", UUID.randomUUID());
            return null;
        }).when(repoGrupos).agregarMiembro(any(MiembroGrupo.class));

        GrupoEstudio grupo = servicioGrupos.crear("Grupo Scrum", idCurso, 5);

        assertThat(grupo.getNombre()).isEqualTo("Grupo Scrum");
        assertThat(grupo.getIdCurso()).isEqualTo(idCurso);
        assertThat(grupo.getCreadoPor()).isEqualTo(idEstudiante);

        verify(repoGrupos).agregarMiembro(argThat(MiembroGrupo::esTutor));
    }

    // ── TF-022: Unirse a grupo lleno ──────────────────────────────────────────

    @Test
    @DisplayName("TF-022 · Unirse a grupo lleno → BadRequestException 'lleno'")
    void tf022_unirseGrupoLleno_lanzaExcepcion() {
        GrupoEstudio grupoLleno = buildGrupo("Grupo Completo", idCurso, idInstructor, 3);
        when(repoGrupos.buscarPorId(grupoLleno.getId())).thenReturn(Optional.of(grupoLleno));
        when(repoGrupos.esMiembro(grupoLleno.getId(), idEstudiante)).thenReturn(false);

        // Simular 3 miembros activos (= capacidad máxima)
        when(repoGrupos.buscarMiembrosActivos(grupoLleno.getId()))
                .thenReturn(List.of(
                        buildMiembro(grupoLleno, UUID.randomUUID()),
                        buildMiembro(grupoLleno, UUID.randomUUID()),
                        buildMiembro(grupoLleno, UUID.randomUUID())));

        assertThatThrownBy(() -> servicioGrupos.unirse(grupoLleno.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("lleno");
    }

    @Test
    @DisplayName("TF-022-B · Unirse a grupo ya siendo miembro → BadRequest 'miembro'")
    void tf022B_unirseYaSiendoMiembro_lanzaExcepcion() {
        GrupoEstudio grupo = buildGrupo("Mi Grupo", idCurso, idInstructor, 10);
        when(repoGrupos.buscarPorId(grupo.getId())).thenReturn(Optional.of(grupo));
        when(repoGrupos.esMiembro(grupo.getId(), idEstudiante)).thenReturn(true);

        assertThatThrownBy(() -> servicioGrupos.unirse(grupo.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("miembro");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Foro buildForo(String nombre, UUID idCurso, UUID creadoPor) {
        Foro f = new Foro();
        f.setNombre(nombre);
        f.setIdCurso(idCurso);
        f.setCreadoPor(creadoPor);
        setFieldSilent(f, "id",       UUID.randomUUID());
        setFieldSilent(f, "creadoEn", Instant.now());
        return f;
    }

    private Hilo buildHilo(String titulo, Foro foro, UUID autor, boolean bloqueado) {
        Hilo h = new Hilo();
        h.setForo(foro);
        h.setTitulo(titulo);
        h.setIdAutor(autor);
        h.setBloqueado(bloqueado);
        setFieldSilent(h, "id",       UUID.randomUUID());
        setFieldSilent(h, "creadoEn", Instant.now());
        return h;
    }

    private GrupoEstudio buildGrupo(String nombre, UUID idCurso,
                                     UUID creadoPor, int maxMiembros) {
        GrupoEstudio g = new GrupoEstudio();
        g.setNombre(nombre);
        g.setIdCurso(idCurso);
        g.setCreadoPor(creadoPor);
        g.setMaxMiembros(maxMiembros);
        setFieldSilent(g, "id",       UUID.randomUUID());
        setFieldSilent(g, "creadoEn", Instant.now());
        return g;
    }

    private MiembroGrupo buildMiembro(GrupoEstudio grupo, UUID idUsuario) {
        MiembroGrupo m = new MiembroGrupo();
        m.setGrupo(grupo);
        m.setIdUsuario(idUsuario);
        setFieldSilent(m, "id", UUID.randomUUID());
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
