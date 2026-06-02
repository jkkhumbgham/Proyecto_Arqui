package com.puj.evaluaciones.aceptacion.steps;

import com.puj.evaluaciones.adaptativo.aplicacion.MotorAdaptativo;
import com.puj.evaluaciones.adaptativo.dominio.RecomendacionAdaptativa;
import com.puj.evaluaciones.adaptativo.dominio.ReglaAdaptativa;
import com.puj.evaluaciones.adaptativo.dominio.RepositorioReglasAdaptativas;
import com.puj.evaluaciones.entregas.aplicacion.ServicioEntregas;
import com.puj.evaluaciones.entregas.dominio.Entrega;
import com.puj.evaluaciones.entregas.dominio.RepositorioEntregas;
import com.puj.evaluaciones.entregas.dominio.excepciones.EntregaNoPermitidaException;
import com.puj.evaluaciones.entregas.interfaces.dto.ResultadoEntrega;
import com.puj.evaluaciones.entregas.interfaces.dto.SolicitudEntrega;
import com.puj.evaluaciones.evaluaciones.dominio.*;
import com.puj.eventos.publicador.PublicadorEventos;
import com.puj.seguridad.redis.ProveedorRedis;
import io.cucumber.java.es.*;
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
 * Steps de Cucumber para Motor Adaptativo (TA-06, TA-07).
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public class AdaptativoSteps {

    private final RepositorioReglasAdaptativas mockRepoReglas = mock(RepositorioReglasAdaptativas.class);
    private final ProveedorRedis               mockRedis      = mock(ProveedorRedis.class);
    private final JedisPool                    mockPool       = mock(JedisPool.class);
    private final Jedis                        mockJedis      = mock(Jedis.class);

    private final RepositorioEntregas    mockRepoEntregas     = mock(RepositorioEntregas.class);
    private final com.puj.evaluaciones.evaluaciones.dominio.RepositorioEvaluaciones mockRepoEval
            = mock(com.puj.evaluaciones.evaluaciones.dominio.RepositorioEvaluaciones.class);
    private final PublicadorEventos      mockEventos          = mock(PublicadorEventos.class);
    private final MotorAdaptativo        motorAdaptativo;
    private final ServicioEntregas       servicioEntregas;

    // Estado del escenario
    private UUID idEvaluacion;
    private UUID idUsuario;
    private Evaluacion evaluacion;
    private ReglaAdaptativa regla;
    private Optional<RecomendacionAdaptativa> resultadoMotor;
    private ResultadoEntrega resultadoEntrega;
    private Exception excepcion;
    private boolean redisDisponible = true;
    private double puntajeObtenido;
    private double puntajeMaximo = 10.0;

    public AdaptativoSteps() throws Exception {
        motorAdaptativo = new MotorAdaptativo();
        setField(motorAdaptativo, "repoReglas", mockRepoReglas);
        setField(motorAdaptativo, "redisProvider", mockRedis);

        servicioEntregas = new ServicioEntregas();
        setField(servicioEntregas, "repoEntregas",    mockRepoEntregas);
        setField(servicioEntregas, "repoEvaluaciones", mockRepoEval);
        setField(servicioEntregas, "motorAdaptativo",  motorAdaptativo);
        setField(servicioEntregas, "publicadorEventos", mockEventos);

        idUsuario    = UUID.randomUUID();
        idEvaluacion = UUID.randomUUID();
    }

    // ── Dado ──────────────────────────────────────────────────────────────────

    @Dado("que existe una evaluación con umbral de aprobación del {int}%")
    public void queExisteEvaluacionConUmbral(int umbral) {
        evaluacion = new Evaluacion();
        setFieldSilent(evaluacion, "id", idEvaluacion);
        evaluacion.setTitulo("Evaluación Test");
        evaluacion.setIdCurso(UUID.randomUUID());
        evaluacion.setIdInstructor(UUID.randomUUID());
        evaluacion.setPorcentajeAprobacion((double) umbral);
        evaluacion.setMaxIntentos(3);
        setFieldSilent(evaluacion, "creadoEn", Instant.now());
        setFieldSilent(evaluacion, "actualizadoEn", Instant.now());

        regla = new ReglaAdaptativa();
        setFieldSilent(regla, "id", UUID.randomUUID());
        regla.setIdEvaluacion(idEvaluacion);
        regla.setUmbralPorcentaje((double) umbral);
        regla.setIdLeccionSupplementaria(UUID.randomUUID());
        regla.setMensaje("Te recomendamos repasar el material");
        regla.setActiva(true);

        // Setup Redis
        when(mockRedis.obtenerPool()).thenReturn(mockPool);
        when(mockPool.getResource()).thenReturn(mockJedis);
        doNothing().when(mockJedis).close();
        when(mockJedis.get(anyString())).thenReturn(null); // cache miss por defecto
    }

    @Dado("que existe una evaluación con {int} intentos máximos")
    public void queExisteEvaluacionConIntentos(int maxIntentos) {
        queExisteEvaluacionConUmbral(60);
        evaluacion.setMaxIntentos(maxIntentos);
    }

    @Y("el estudiante responde correctamente solo el {int}% de las preguntas")
    public void elEstudianteRespondeCorrectamentePorcentaje(int porcentaje) {
        puntajeObtenido = puntajeMaximo * porcentaje / 100.0;
    }

    @Y("el estudiante obtiene el {int}% del puntaje máximo")
    public void elEstudianteObtieneElPorcentaje(int porcentaje) {
        puntajeObtenido = puntajeMaximo * porcentaje / 100.0;
    }

    @Y("el estudiante obtiene exactamente el {int}% del puntaje máximo")
    public void elEstudianteObtieneExactamentePorcentaje(int porcentaje) {
        puntajeObtenido = puntajeMaximo * porcentaje / 100.0;
    }

    @Y("el servicio Redis no está disponible")
    public void elServicioRedisNoEstaDisponible() {
        redisDisponible = false;
        when(mockJedis.get(anyString())).thenThrow(new RuntimeException("Redis unavailable"));
    }

    @Y("el estudiante ya realizó {int} intentos")
    public void elEstudianteYaRealizo(int intentos) {
        when(mockRepoEval.buscarPorId(idEvaluacion)).thenReturn(Optional.of(evaluacion));
        when(mockRepoEval.contarIntentos(idUsuario, idEvaluacion)).thenReturn((long) intentos);
    }

    // ── Cuando ───────────────────────────────────────────────────────────────

    @Cuando("el motor evaluación finaliza la calificación")
    public void elMotorEvaluacionFinalizaCalificacion() {
        when(mockRepoReglas.buscarPorEvaluacion(idEvaluacion)).thenReturn(Optional.of(regla));
        resultadoMotor = motorAdaptativo.evaluar(
                idEvaluacion, idUsuario,
                BigDecimal.valueOf(puntajeObtenido),
                BigDecimal.valueOf(puntajeMaximo));
    }

    @Cuando("el estudiante intenta realizar un cuarto intento")
    public void elEstudianteIntentaRealizarUnCuartoIntento() {
        SolicitudEntrega solicitud = new SolicitudEntrega(Map.of(), 60L);
        try {
            resultadoEntrega = servicioEntregas.entregar(idUsuario, idEvaluacion, solicitud);
        } catch (Exception e) {
            excepcion = e;
        }
    }

    // ── Entonces ─────────────────────────────────────────────────────────────

    @Entonces("el resultado incluye una recomendación adaptativa con lección suplementaria")
    public void elResultadoIncluyeRecomendacion() {
        assertThat(resultadoMotor).isPresent();
        assertThat(resultadoMotor.get().idLeccionSupplementaria()).isNotNull();
    }

    @Y("el mensaje contiene {string}")
    public void elMensajeContiene(String fragmento) {
        assertThat(resultadoMotor.get().mensaje()).contains(fragmento);
    }

    @Entonces("el resultado no contiene ninguna recomendación")
    public void elResultadoNoContieneRecomendacion() {
        assertThat(resultadoMotor).isEmpty();
    }

    @Entonces("el resultado incluye una recomendación obtenida desde la base de datos")
    public void elResultadoIncluyeRecomendacionDesdeBD() {
        verify(mockRepoReglas, times(1)).buscarPorEvaluacion(idEvaluacion);
        assertThat(resultadoMotor).isPresent();
    }

    @Y("el sistema no lanza ninguna excepción al estudiante")
    public void elSistemaNoPropagaExcepcion() {
        assertThat(excepcion).isNull();
    }

    @Entonces("el sistema rechaza la entrega con mensaje {string}")
    public void elSistemaRechazaConMensaje(String mensaje) {
        assertThat(excepcion).isNotNull()
                .isInstanceOf(EntregaNoPermitidaException.class);
        assertThat(excepcion.getMessage()).contains(mensaje);
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
