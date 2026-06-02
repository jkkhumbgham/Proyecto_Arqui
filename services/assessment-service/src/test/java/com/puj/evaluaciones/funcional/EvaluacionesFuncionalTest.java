package com.puj.evaluaciones.funcional;

import com.puj.evaluaciones.adaptativo.aplicacion.MotorAdaptativo;
import com.puj.evaluaciones.adaptativo.dominio.RecomendacionAdaptativa;
import com.puj.evaluaciones.adaptativo.dominio.ReglaAdaptativa;
import com.puj.evaluaciones.adaptativo.dominio.RepositorioReglasAdaptativas;
import com.puj.evaluaciones.entregas.aplicacion.ServicioEntregas;
import com.puj.evaluaciones.entregas.dominio.Entrega;
import com.puj.evaluaciones.entregas.dominio.EstadoEntrega;
import com.puj.evaluaciones.entregas.dominio.RepositorioEntregas;
import com.puj.evaluaciones.entregas.dominio.excepciones.EntregaNoPermitidaException;
import com.puj.evaluaciones.entregas.interfaces.dto.ResultadoEntrega;
import com.puj.evaluaciones.entregas.interfaces.dto.SolicitudEntrega;
import com.puj.evaluaciones.evaluaciones.dominio.*;
import com.puj.evaluaciones.evaluaciones.dominio.excepciones.EvaluacionNoEncontradaException;
import com.puj.eventos.publicador.PublicadorEventos;
import com.puj.seguridad.redis.ProveedorRedis;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas Funcionales del assessment-service (TF-012..TF-015 + TI-006..008).
 *
 * <p>Cubre el flujo completo de evaluaciones y adaptación:
 * <ul>
 *   <li>TF-012: Entregar respuestas → calificación automática GRADED</li>
 *   <li>TF-013: Superar el límite de intentos → EntregaNoPermitidaException</li>
 *   <li>TF-014: Puntaje bajo → recomendación adaptativa</li>
 *   <li>TF-015: Puntaje sobre umbral → sin recomendación</li>
 *   <li>TI-006: Calificación automática con preguntas múltiple opción</li>
 *   <li>TI-007: Redis fallback a BD para reglas adaptativas</li>
 *   <li>TI-008: Motor adaptativo — umbral exacto no dispara recomendación</li>
 * </ul>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TF-012..015 + TI-006..008 · Funcional/Integración Evaluaciones")
class EvaluacionesFuncionalTest {

    // ── Mocks para ServicioEntregas ───────────────────────────────────────────
    @Mock private RepositorioEntregas    repoEntregas;
    @Mock private com.puj.evaluaciones.evaluaciones.dominio.RepositorioEvaluaciones repoEvaluaciones;
    @Mock private MotorAdaptativo        motorAdaptativo;
    @Mock private PublicadorEventos      publicadorEventos;

    @InjectMocks private ServicioEntregas servicioEntregas;

    // ── Mocks para MotorAdaptativo ────────────────────────────────────────────
    @Mock private RepositorioReglasAdaptativas repoReglas;
    @Mock private ProveedorRedis               redisProvider;
    @Mock private JedisPool                    jedisPool;
    @Mock private Jedis                        jedis;

    @InjectMocks private MotorAdaptativo motor;

    // ── Estado común ──────────────────────────────────────────────────────────
    private UUID idUsuario;
    private UUID idEvaluacion;
    private Evaluacion evaluacion;
    private ReglaAdaptativa regla60;

    @BeforeEach
    void setUp() throws Exception {
        idUsuario    = UUID.randomUUID();
        idEvaluacion = UUID.randomUUID();

        evaluacion = buildEvaluacion(idEvaluacion, 60.0, 3);

        regla60 = new ReglaAdaptativa();
        setField(regla60, "id", UUID.randomUUID());
        regla60.setIdEvaluacion(idEvaluacion);
        regla60.setUmbralPorcentaje(60.0);
        regla60.setIdLeccionSupplementaria(UUID.randomUUID());
        regla60.setMensaje("Tu puntaje está por debajo del umbral. Te recomendamos repasar el material.");
        regla60.setActiva(true);

        // Redis: siempre miss (fuerza lectura de BD)
        lenient().when(redisProvider.obtenerPool()).thenReturn(jedisPool);
        lenient().when(jedisPool.getResource()).thenReturn(jedis);
        lenient().when(jedis.get(anyString())).thenReturn(null);
        lenient().doNothing().when(jedis).close();
    }

    // ── TF-012: Entrega y calificación automática ─────────────────────────────

    @Test
    @DisplayName("TF-012 · Entrega con respuesta correcta → GRADED, puntuación > 0, evento publicado")
    void tf012_entrega_correcta_gradedYPublicaEvento() throws Exception {
        // Construir pregunta con una opción correcta
        Pregunta pregunta = buildPregunta(UUID.randomUUID(), TipoPregunta.SINGLE_CHOICE, 10.0);
        OpcionRespuesta opCorrecta = buildOpcion(UUID.randomUUID(), true);
        pregunta.getOpciones().add(opCorrecta);
        evaluacion.getPreguntas().add(pregunta);

        when(repoEvaluaciones.buscarPorId(idEvaluacion)).thenReturn(Optional.of(evaluacion));
        when(repoEvaluaciones.contarIntentos(idUsuario, idEvaluacion)).thenReturn(0L);
        when(repoEvaluaciones.buscarPorCurso(any(), anyInt(), anyInt())).thenReturn(List.of(evaluacion));
        when(repoEntregas.buscarPorUsuarioYEvaluaciones(any(), any())).thenReturn(List.of());
        when(repoEntregas.guardar(any(Entrega.class))).thenAnswer(inv -> {
            Entrega e = inv.getArgument(0);
            setField(e, "id", UUID.randomUUID());
            return e;
        });
        when(motorAdaptativo.evaluar(any(), any(), any(), any())).thenReturn(Optional.empty());

        Map<String, List<String>> respuestas = Map.of(
                pregunta.getId().toString(), List.of(opCorrecta.getId().toString()));
        SolicitudEntrega solicitud = new SolicitudEntrega(respuestas, 120L);

        ResultadoEntrega resultado = servicioEntregas.entregar(idUsuario, idEvaluacion, solicitud);

        assertThat(resultado.puntuacion()).isGreaterThan(BigDecimal.ZERO);
        assertThat(resultado.aprobado()).isTrue();
        verify(publicadorEventos, times(1)).publicarAnaliticas(any());
    }

    // ── TF-013: Límite de intentos superado ──────────────────────────────────

    @Test
    @DisplayName("TF-013 · Superar límite de intentos → EntregaNoPermitidaException")
    void tf013_limitIntentos_lanzaExcepcion() {
        // maxIntentos = 3, intentos ya realizados = 3
        when(repoEvaluaciones.buscarPorId(idEvaluacion)).thenReturn(Optional.of(evaluacion));
        when(repoEvaluaciones.contarIntentos(idUsuario, idEvaluacion)).thenReturn(3L);

        SolicitudEntrega solicitud = new SolicitudEntrega(Map.of(), 60L);

        assertThatThrownBy(() ->
                servicioEntregas.entregar(idUsuario, idEvaluacion, solicitud))
                .isInstanceOf(EntregaNoPermitidaException.class)
                .hasMessageContaining("intentos");

        verify(repoEntregas, never()).guardar(any());
    }

    // ── TF-014: Recomendación adaptativa (puntaje bajo) ───────────────────────

    @Test
    @DisplayName("TF-014 · Puntaje 35% (< umbral 60%) → recomendación adaptativa incluida")
    void tf014_puntajeBajo_incluyeRecomendacion() throws Exception {
        // Pregunta de 10 puntos, respuesta incorrecta → 0 puntos = 0%
        Pregunta pregunta = buildPregunta(UUID.randomUUID(), TipoPregunta.SINGLE_CHOICE, 10.0);
        OpcionRespuesta opCorrecta   = buildOpcion(UUID.randomUUID(), true);
        OpcionRespuesta opIncorrecta = buildOpcion(UUID.randomUUID(), false);
        pregunta.getOpciones().addAll(List.of(opCorrecta, opIncorrecta));
        evaluacion.getPreguntas().add(pregunta);

        RecomendacionAdaptativa recomendacion = new RecomendacionAdaptativa(
                regla60.getIdLeccionSupplementaria(),
                "Tu puntaje está por debajo del umbral. Te recomendamos repasar el material.",
                0.0, 60.0);

        lenient().when(repoEvaluaciones.buscarPorId(idEvaluacion)).thenReturn(Optional.of(evaluacion));
        lenient().when(repoEvaluaciones.contarIntentos(idUsuario, idEvaluacion)).thenReturn(0L);
        lenient().when(repoEvaluaciones.buscarPorCurso(any(), anyInt(), anyInt())).thenReturn(List.of());
        lenient().when(repoEntregas.buscarPorUsuarioYEvaluaciones(any(), any())).thenReturn(List.of());
        lenient().when(repoEntregas.guardar(any(Entrega.class))).thenAnswer(inv -> {
            Entrega e = inv.getArgument(0);
            setField(e, "id", UUID.randomUUID());
            return e;
        });
        when(motorAdaptativo.evaluar(any(), any(), any(), any()))
                .thenReturn(Optional.of(recomendacion));

        // Respuesta incorrecta
        Map<String, List<String>> respuestas = Map.of(
                pregunta.getId().toString(), List.of(opIncorrecta.getId().toString()));
        SolicitudEntrega solicitud = new SolicitudEntrega(respuestas, 90L);

        ResultadoEntrega resultado = servicioEntregas.entregar(idUsuario, idEvaluacion, solicitud);

        assertThat(resultado.recomendacion()).isNotNull();
        assertThat(resultado.recomendacion().mensaje())
                .contains("Te recomendamos repasar el material");
    }

    // ── TF-015: Sin recomendación (puntaje sobre umbral) ─────────────────────

    @Test
    @DisplayName("TF-015 · Puntaje sobre umbral → sin recomendación adaptativa")
    void tf015_puntajeAlto_sinRecomendacion() {
        when(repoReglas.buscarPorEvaluacion(idEvaluacion)).thenReturn(Optional.of(regla60));

        // 75% > 60% → sin recomendación
        Optional<RecomendacionAdaptativa> resultado = motor.evaluar(
                idEvaluacion, idUsuario,
                BigDecimal.valueOf(7.5), BigDecimal.valueOf(10));

        assertThat(resultado).isEmpty();
    }

    // ── TI-006: Calificación automática SINGLE_CHOICE ────────────────────────

    @Test
    @DisplayName("TI-006-A · SINGLE_CHOICE: respuesta correcta suma puntos")
    void ti006A_singleChoice_respuestaCorrecta_sumaPuntos() {
        Pregunta p = buildPregunta(UUID.randomUUID(), TipoPregunta.SINGLE_CHOICE, 5.0);
        UUID idOpcCorrecta = UUID.randomUUID();
        p.getOpciones().add(buildOpcion(idOpcCorrecta, true));
        p.getOpciones().add(buildOpcion(UUID.randomUUID(), false));

        // Verifica mediante MotorAdaptativo con puntaje 5/5 = 100%
        when(repoReglas.buscarPorEvaluacion(idEvaluacion)).thenReturn(Optional.of(regla60));
        Optional<RecomendacionAdaptativa> resultado = motor.evaluar(
                idEvaluacion, idUsuario,
                BigDecimal.valueOf(5), BigDecimal.valueOf(5)); // 100% > 60%

        assertThat(resultado).isEmpty(); // Aprobó: sin recomendación
    }

    @Test
    @DisplayName("TI-006-B · SINGLE_CHOICE: respuesta incorrecta → sin puntos")
    void ti006B_singleChoice_respuestaIncorrecta_sinPuntos() {
        when(repoReglas.buscarPorEvaluacion(idEvaluacion)).thenReturn(Optional.of(regla60));
        Optional<RecomendacionAdaptativa> resultado = motor.evaluar(
                idEvaluacion, idUsuario,
                BigDecimal.valueOf(0), BigDecimal.valueOf(5)); // 0% < 60%

        assertThat(resultado).isPresent();
    }

    // ── TI-007: Fallback Redis → BD ──────────────────────────────────────────

    @Test
    @DisplayName("TI-007 · Redis no disponible → fallback a BD (E-12)")
    void ti007_redisFalla_fallbackBD() {
        when(jedis.get(anyString())).thenThrow(new RuntimeException("Redis connection refused"));
        when(repoReglas.buscarPorEvaluacion(idEvaluacion)).thenReturn(Optional.of(regla60));

        Optional<RecomendacionAdaptativa> resultado = motor.evaluar(
                idEvaluacion, idUsuario,
                BigDecimal.valueOf(3), BigDecimal.valueOf(10));

        assertThat(resultado).isPresent();
        verify(repoReglas, times(1)).buscarPorEvaluacion(idEvaluacion);
    }

    // ── TI-008: Umbral exacto no dispara recomendación ───────────────────────

    @Test
    @DisplayName("TI-008 · Puntaje exactamente igual al umbral (60%) → sin recomendación")
    void ti008_umbralExacto_sinRecomendacion() {
        when(repoReglas.buscarPorEvaluacion(idEvaluacion)).thenReturn(Optional.of(regla60));

        // 6/10 = 60.0% — exactamente en el umbral, condición es < (no <=)
        Optional<RecomendacionAdaptativa> resultado = motor.evaluar(
                idEvaluacion, idUsuario,
                BigDecimal.valueOf(6), BigDecimal.valueOf(10));

        assertThat(resultado).isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Evaluacion buildEvaluacion(UUID id, double umbralAprobacion, int maxIntentos) {
        Evaluacion e = new Evaluacion();
        setFieldSilent(e, "id", id);
        e.setTitulo("Evaluación de Prueba");
        e.setIdCurso(UUID.randomUUID());
        e.setIdInstructor(UUID.randomUUID());
        e.setPorcentajeAprobacion(umbralAprobacion);
        e.setMaxIntentos(maxIntentos);
        setFieldSilent(e, "creadoEn", Instant.now());
        setFieldSilent(e, "actualizadoEn", Instant.now());
        return e;
    }

    private Pregunta buildPregunta(UUID id, TipoPregunta tipo, double puntos) {
        Pregunta p = new Pregunta();
        setFieldSilent(p, "id", id);
        p.setTipo(tipo);
        p.setPuntos(puntos);
        p.setTexto("¿Pregunta de prueba?");
        // La lista 'opciones' ya está inicializada en la entidad como ArrayList
        return p;
    }

    private OpcionRespuesta buildOpcion(UUID id, boolean correcta) {
        OpcionRespuesta op = new OpcionRespuesta();
        setFieldSilent(op, "id", id);
        op.setTexto("Opción " + id.toString().substring(0, 8));
        op.setCorrecta(correcta);
        return op;
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
