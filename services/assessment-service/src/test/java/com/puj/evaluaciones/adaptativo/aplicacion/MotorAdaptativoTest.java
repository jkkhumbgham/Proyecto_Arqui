package com.puj.evaluaciones.adaptativo.aplicacion;

import com.puj.evaluaciones.adaptativo.dominio.RecomendacionAdaptativa;
import com.puj.evaluaciones.adaptativo.dominio.ReglaAdaptativa;
import com.puj.evaluaciones.adaptativo.dominio.RepositorioReglasAdaptativas;
import com.puj.seguridad.redis.ProveedorRedis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias del motor adaptativo {@link MotorAdaptativo}.
 *
 * <p>Cubre los escenarios principales: puntaje por debajo del umbral,
 * puntaje sobre el umbral, ausencia de regla y fallback a base de datos
 * cuando Redis no está disponible.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ExtendWith(MockitoExtension.class)
class MotorAdaptativoTest {

    @Mock private RepositorioReglasAdaptativas repoReglas;
    @Mock private ProveedorRedis          redisProvider;
    @Mock private JedisPool                    jedisPool;
    @Mock private Jedis                        jedis;

    @InjectMocks
    private MotorAdaptativo motor;

    private UUID idEvaluacion;
    private UUID idUsuario;
    private ReglaAdaptativa regla;

    /**
     * Configura los mocks y la regla adaptativa de prueba antes de cada test.
     */
    @BeforeEach
    void configurar() {
        idEvaluacion = UUID.randomUUID();
        idUsuario    = UUID.randomUUID();

        regla = new ReglaAdaptativa();
        establecerCampo(regla, "id", UUID.randomUUID());
        regla.setIdEvaluacion(idEvaluacion);
        regla.setIdCurso(UUID.randomUUID());
        regla.setUmbralPorcentaje(60.0);
        regla.setIdLeccionSupplementaria(UUID.randomUUID());
        regla.setMensaje("Repasa el material antes de continuar.");
        regla.setActiva(true);

        when(redisProvider.obtenerPool()).thenReturn(jedisPool);
        when(jedisPool.getResource()).thenReturn(jedis);
        when(jedis.get(anyString())).thenReturn(null);
        doNothing().when(jedis).close();
    }

    /**
     * Verifica que se retorna una recomendación cuando el puntaje está por
     * debajo del umbral configurado.
     */
    @Test
    void evaluar_puntajeMenor_retornaRecomendacion() {
        when(repoReglas.buscarPorEvaluacion(idEvaluacion)).thenReturn(Optional.of(regla));

        Optional<RecomendacionAdaptativa> resultado = motor.evaluar(
                idEvaluacion, idUsuario,
                BigDecimal.valueOf(4), BigDecimal.valueOf(10)
        );

        assertThat(resultado).isPresent();
        assertThat(resultado.get().porcentajeObtenido()).isEqualTo(40.0);
        assertThat(resultado.get().porcentajeRequerido()).isEqualTo(60.0);
    }

    /**
     * Verifica que se retorna vacío cuando el puntaje supera el umbral.
     */
    @Test
    void evaluar_puntajeMayor_retornaVacio() {
        when(repoReglas.buscarPorEvaluacion(idEvaluacion)).thenReturn(Optional.of(regla));

        Optional<RecomendacionAdaptativa> resultado = motor.evaluar(
                idEvaluacion, idUsuario,
                BigDecimal.valueOf(7), BigDecimal.valueOf(10)
        );

        assertThat(resultado).isEmpty();
    }

    /**
     * Verifica que se retorna vacío cuando no existe una regla adaptativa activa.
     */
    @Test
    void evaluar_sinRegla_retornaVacio() {
        when(repoReglas.buscarPorEvaluacion(idEvaluacion)).thenReturn(Optional.empty());

        Optional<RecomendacionAdaptativa> resultado = motor.evaluar(
                idEvaluacion, idUsuario,
                BigDecimal.valueOf(0), BigDecimal.valueOf(10)
        );

        assertThat(resultado).isEmpty();
    }

    /**
     * Verifica que cuando Redis lanza una excepción, el motor cae en fallback
     * a la base de datos y retorna la recomendación correctamente.
     */
    @Test
    void evaluar_redisFalla_usaFallbackBD() {
        when(jedis.get(anyString())).thenThrow(new RuntimeException("Redis timeout"));
        when(repoReglas.buscarPorEvaluacion(idEvaluacion)).thenReturn(Optional.of(regla));

        Optional<RecomendacionAdaptativa> resultado = motor.evaluar(
                idEvaluacion, idUsuario,
                BigDecimal.valueOf(3), BigDecimal.valueOf(10)
        );

        assertThat(resultado).isPresent();
        verify(repoReglas, times(1)).buscarPorEvaluacion(idEvaluacion);
    }

    /**
     * Establece un campo privado por reflexión para configurar el estado del
     * objeto bajo prueba.
     *
     * @param objetivo objeto sobre el que se establece el campo
     * @param nombre   nombre del campo a modificar
     * @param valor    valor a asignar al campo
     * @throws RuntimeException si el campo no existe o no es accesible
     */
    private void establecerCampo(Object objetivo, String nombre, Object valor) {
        try {
            var campo = objetivo.getClass().getDeclaredField(nombre);
            campo.setAccessible(true);
            campo.set(objetivo, valor);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
