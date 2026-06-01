package com.puj.evaluaciones.entregas.aplicacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.puj.evaluaciones.adaptativo.aplicacion.MotorAdaptativo;
import com.puj.evaluaciones.adaptativo.dominio.RecomendacionAdaptativa;
import com.puj.evaluaciones.entregas.dominio.Entrega;
import com.puj.evaluaciones.entregas.dominio.EstadoEntrega;
import com.puj.evaluaciones.entregas.dominio.RepositorioEntregas;
import com.puj.evaluaciones.entregas.dominio.excepciones.EntregaNoPermitidaException;
import com.puj.evaluaciones.entregas.interfaces.dto.ResultadoEntrega;
import com.puj.evaluaciones.entregas.interfaces.dto.SolicitudEntrega;
import com.puj.evaluaciones.evaluaciones.dominio.Evaluacion;
import com.puj.evaluaciones.evaluaciones.dominio.OpcionRespuesta;
import com.puj.evaluaciones.evaluaciones.dominio.Pregunta;
import com.puj.evaluaciones.evaluaciones.dominio.RepositorioEvaluaciones;
import com.puj.evaluaciones.evaluaciones.dominio.TipoPregunta;
import com.puj.evaluaciones.evaluaciones.dominio.excepciones.EvaluacionNoEncontradaException;
import com.puj.eventos.EventoEvaluacionEntregada;
import com.puj.eventos.publicador.PublicadorEventos;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * Servicio de calificación de evaluaciones.
 *
 * <p>Gestiona dos flujos principales:</p>
 * <ol>
 *   <li>{@link #calificarExistente} — califica una entrega ya creada en estado
 *       {@code IN_PROGRESS} (flujo de dos pasos: primero el estudiante inicia,
 *       luego envía respuestas).</li>
 *   <li>{@link #entregar} — crea y califica la entrega en un único paso
 *       (flujo legado de un paso).</li>
 * </ol>
 *
 * <p>Tras calificar, publica un {@link EventoEvaluacionEntregada} en RabbitMQ
 * y consulta el motor adaptativo para determinar si se debe recomendar
 * contenido suplementario.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class ServicioEntregas {

    @Inject private RepositorioEntregas    repoEntregas;
    @Inject private RepositorioEvaluaciones repoEvaluaciones;
    @Inject private MotorAdaptativo        motorAdaptativo;
    @Inject private PublicadorEventos         publicadorEventos;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Califica una entrega ya persistida en estado {@code IN_PROGRESS}.
     *
     * <p>Mide la duración real en el servidor y la limita al tiempo
     * máximo configurado en la evaluación si existe. Publica el evento de
     * analytics y consulta el motor adaptativo al finalizar.</p>
     *
     * @param entrega entrega en estado {@code IN_PROGRESS} a calificar
     * @param req     respuestas y duración reportada por el cliente
     * @return resultado de la calificación con puntaje y recomendación adaptativa
     */
    @Transactional
    public ResultadoEntrega calificarExistente(Entrega entrega, SolicitudEntrega req) {
        Evaluacion evaluacion = entrega.getEvaluacion();
        persistirRespuestasJson(entrega, req.respuestas());

        long duracionReal   = calcularDuracionReal(entrega, evaluacion);
        ResultadoCalificacion resultado = calificar(evaluacion, req.respuestas());

        aplicarCalificacion(entrega, resultado, duracionReal, Instant.now());
        repoEntregas.actualizar(entrega);

        boolean todasAprobadas = calcularTodasLasEvaluacionesAprobadas(
                entrega.getIdUsuario(), evaluacion.getIdCurso());
        publicarEvento(entrega, evaluacion, resultado, req.duracionSegundos(), todasAprobadas);

        Optional<RecomendacionAdaptativa> recomendacion =
                motorAdaptativo.evaluar(
                        evaluacion.getId(), entrega.getIdUsuario(),
                        resultado.puntuacion(), resultado.puntuacionMaxima());

        return new ResultadoEntrega(
                entrega.getId(), resultado.puntuacion(), resultado.puntuacionMaxima(),
                resultado.aprobado(), resultado.porcentajePuntaje(),
                recomendacion.orElse(null)
        );
    }

    /**
     * Crea y califica una entrega en un único paso (flujo legado).
     *
     * <p>Verifica que el estudiante no haya superado el límite de intentos antes
     * de crear la entrega.</p>
     *
     * @param idUsuario    UUID del estudiante que realiza el intento
     * @param idEvaluacion UUID de la evaluación
     * @param req          respuestas y duración del intento
     * @return resultado de la calificación con puntaje y recomendación adaptativa
     * @throws EvaluacionNoEncontradaException si la evaluación no existe
     * @throws EntregaNoPermitidaException     si el estudiante alcanzó el límite de intentos
     */
    @Transactional
    public ResultadoEntrega entregar(UUID idUsuario, UUID idEvaluacion, SolicitudEntrega req) {
        Evaluacion evaluacion = repoEvaluaciones.buscarPorId(idEvaluacion)
                .orElseThrow(() -> new EvaluacionNoEncontradaException(idEvaluacion));

        long intentos = repoEvaluaciones.contarIntentos(idUsuario, idEvaluacion);
        if (intentos >= evaluacion.getMaxIntentos()) {
            throw new EntregaNoPermitidaException(
                    "Has alcanzado el número máximo de intentos ("
                    + evaluacion.getMaxIntentos() + ").");
        }

        Entrega entrega = construirNuevaEntrega(
                idUsuario, evaluacion, (int) intentos + 1, req);

        ResultadoCalificacion resultado = calificar(evaluacion, req.respuestas());
        aplicarCalificacion(entrega, resultado, req.duracionSegundos(), Instant.now());
        entrega.setEstado(EstadoEntrega.GRADED);
        repoEntregas.guardar(entrega);

        boolean todasAprobadas = calcularTodasLasEvaluacionesAprobadas(
                idUsuario, evaluacion.getIdCurso());
        publicarEvento(entrega, evaluacion, resultado, req.duracionSegundos(), todasAprobadas);

        Optional<RecomendacionAdaptativa> recomendacion =
                motorAdaptativo.evaluar(
                        idEvaluacion, idUsuario,
                        resultado.puntuacion(), resultado.puntuacionMaxima());

        return new ResultadoEntrega(
                entrega.getId(), resultado.puntuacion(), resultado.puntuacionMaxima(),
                resultado.aprobado(), resultado.porcentajePuntaje(),
                recomendacion.orElse(null)
        );
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * Construye una nueva {@link Entrega} en estado {@code SUBMITTED} lista
     * para ser calificada.
     *
     * @param idUsuario     UUID del estudiante
     * @param evaluacion    evaluación asociada
     * @param numeroIntento número de intento (base 1)
     * @param req           request con las respuestas y duración
     * @return instancia de {@link Entrega} con los datos iniciales configurados
     */
    private Entrega construirNuevaEntrega(
            UUID idUsuario,
            Evaluacion evaluacion,
            int numeroIntento,
            SolicitudEntrega req) {

        Entrega e = new Entrega();
        e.setIdUsuario(idUsuario);
        e.setEvaluacion(evaluacion);
        e.setNumeroIntento(numeroIntento);
        e.setEstado(EstadoEntrega.SUBMITTED);
        e.setEntregadaEn(Instant.now());
        e.setDuracionSegundos(req.duracionSegundos());
        persistirRespuestasJson(e, req.respuestas());
        return e;
    }

    /**
     * Serializa las respuestas del estudiante y las almacena en la entrega.
     *
     * <p>Si la serialización falla, almacena {@code "{}"}.</p>
     *
     * @param entrega  entrega donde se almacenarán las respuestas
     * @param respuestas mapa de respuestas (preguntaId → lista de opciones)
     */
    private void persistirRespuestasJson(
            Entrega entrega,
            Map<String, List<String>> respuestas) {
        try {
            entrega.setRespuestasJson(mapper.writeValueAsString(respuestas));
        } catch (Exception e) {
            entrega.setRespuestasJson("{}");
        }
    }

    /**
     * Calcula la duración real del intento medida en el servidor.
     *
     * <p>Si la evaluación tiene tiempo límite configurado, la duración se
     * limita a ese máximo.</p>
     *
     * @param entrega    entrega con la marca de tiempo de inicio
     * @param evaluacion evaluación con la configuración de tiempo límite
     * @return duración en segundos, acotada al tiempo límite si corresponde
     */
    private long calcularDuracionReal(Entrega entrega, Evaluacion evaluacion) {
        Instant ahora = Instant.now();
        long segundos = ahora.getEpochSecond() - entrega.getIniciadaEn().getEpochSecond();
        if (evaluacion.getLimiteMinutos() != null) {
            segundos = Math.min(segundos, evaluacion.getLimiteMinutos() * 60L);
        }
        return segundos;
    }

    /**
     * Aplica el resultado de la calificación sobre la entrega.
     *
     * @param entrega           entrega a actualizar
     * @param resultado         resultado de la calificación
     * @param duracionSegundos  duración del intento en segundos
     * @param calificadaEn      instante de calificación
     */
    private void aplicarCalificacion(
            Entrega entrega,
            ResultadoCalificacion resultado,
            long duracionSegundos,
            Instant calificadaEn) {

        entrega.setPuntuacion(resultado.puntuacion());
        entrega.setPuntuacionMaxima(resultado.puntuacionMaxima());
        entrega.setAprobado(resultado.aprobado());
        entrega.setEstado(EstadoEntrega.GRADED);
        entrega.setEntregadaEn(calificadaEn);
        entrega.setDuracionSegundos(duracionSegundos);
        entrega.setCalificadaEn(calificadaEn);
    }

    /**
     * Publica el evento de entrega completada en el bus de analytics (RabbitMQ).
     *
     * @param entrega       entrega calificada
     * @param evaluacion    evaluación asociada
     * @param resultado     resultado de la calificación
     * @param duracion      duración reportada por el cliente
     * @param todasAprobadas {@code true} si el estudiante aprobó todas las evaluaciones
     *                       del curso
     */
    private void publicarEvento(
            Entrega entrega,
            Evaluacion evaluacion,
            ResultadoCalificacion resultado,
            long duracion,
            boolean todasAprobadas) {

        String idLeccion = evaluacion.getIdLeccion() != null
                ? evaluacion.getIdLeccion().toString() : null;
        EventoEvaluacionEntregada evento = new EventoEvaluacionEntregada(
                entrega.getId().toString(),
                entrega.getIdUsuario().toString(),
                evaluacion.getId().toString(),
                evaluacion.getIdCurso().toString(),
                idLeccion,
                resultado.puntuacion(), resultado.puntuacionMaxima(),
                resultado.aprobado(), duracion, todasAprobadas
        );
        publicadorEventos.publicarAnaliticas(evento);
    }

    /**
     * Califica las respuestas del estudiante contra las opciones correctas
     * de cada pregunta activa de la evaluación.
     *
     * <p>Lógica de puntuación:</p>
     * <ul>
     *   <li>{@code SINGLE_CHOICE} / {@code TRUE_FALSE}: puntos completos si se
     *       seleccionó exactamente la opción correcta.</li>
     *   <li>{@code MULTIPLE_CHOICE}: puntos completos si el conjunto seleccionado
     *       es exactamente igual al conjunto de opciones correctas.</li>
     *   <li>{@code SHORT_ANSWER}: requiere revisión manual; no se puntúa
     *       automáticamente.</li>
     * </ul>
     *
     * @param evaluacion evaluación con sus preguntas y opciones
     * @param respuestas mapa de respuestas del estudiante (preguntaId → opciones)
     * @return {@link ResultadoCalificacion} con puntaje, máximo, porcentaje y aprobación
     */
    private ResultadoCalificacion calificar(
            Evaluacion evaluacion,
            Map<String, List<String>> respuestas) {

        double totalPuntos  = 0.0;
        double puntosGanados = 0.0;

        for (Pregunta p : evaluacion.getPreguntas()) {
            if (p.isEliminada()) continue;
            totalPuntos += p.getPuntos();

            List<String> dadas =
                    respuestas.getOrDefault(p.getId().toString(), List.of());
            Set<String> correctasIds = recopilarIdsCorrectas(p);

            puntosGanados += calcularPuntosGanados(p, dadas, correctasIds);
        }

        BigDecimal puntuacion    = BigDecimal.valueOf(puntosGanados)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal puntuacionMax = BigDecimal.valueOf(totalPuntos)
                .setScale(2, RoundingMode.HALF_UP);
        double porcentaje        = totalPuntos > 0
                ? (puntosGanados / totalPuntos) * 100.0 : 0.0;
        boolean aprobado         = porcentaje >= evaluacion.getPorcentajeAprobacion();

        return new ResultadoCalificacion(puntuacion, puntuacionMax, porcentaje, aprobado);
    }

    /**
     * Recopila los IDs de las opciones correctas de una pregunta.
     *
     * @param p pregunta cuyas opciones se analizan
     * @return conjunto de UUIDs (como {@code String}) de las opciones correctas
     */
    private Set<String> recopilarIdsCorrectas(Pregunta p) {
        Set<String> ids = new HashSet<>();
        for (OpcionRespuesta op : p.getOpciones()) {
            if (op.isCorrecta()) ids.add(op.getId().toString());
        }
        return ids;
    }

    /**
     * Calcula los puntos ganados por el estudiante en una pregunta.
     *
     * @param p            pregunta evaluada
     * @param dadas        lista de IDs de opciones seleccionadas por el estudiante
     * @param correctasIds conjunto de IDs de opciones correctas
     * @return puntos completos de la pregunta si respondió correctamente, 0.0 si no
     */
    private double calcularPuntosGanados(
            Pregunta p,
            List<String> dadas,
            Set<String> correctasIds) {

        if (p.getTipo() == TipoPregunta.SINGLE_CHOICE
                || p.getTipo() == TipoPregunta.TRUE_FALSE) {
            if (dadas.size() == 1 && correctasIds.contains(dadas.get(0))) {
                return p.getPuntos();
            }
        } else if (p.getTipo() == TipoPregunta.MULTIPLE_CHOICE) {
            Set<String> dadasSet = new HashSet<>(dadas);
            if (dadasSet.equals(correctasIds)) {
                return p.getPuntos();
            }
        }
        // SHORT_ANSWER: requiere revisión manual — no se puntúa automáticamente
        return 0.0;
    }

    /**
     * Determina si el estudiante aprobó todas las evaluaciones activas del curso.
     *
     * @param idUsuario UUID del estudiante
     * @param idCurso   UUID del curso
     * @return {@code true} si el estudiante aprobó al menos un intento de cada
     *         evaluación del curso, {@code false} si el curso no tiene evaluaciones
     *         o si alguna aún no ha sido aprobada
     */
    private boolean calcularTodasLasEvaluacionesAprobadas(UUID idUsuario, UUID idCurso) {
        List<Evaluacion> evaluacionesCurso = repoEvaluaciones.buscarPorCurso(idCurso, 0, 1000);
        if (evaluacionesCurso.isEmpty()) return false;

        List<UUID> idsEvaluaciones =
                evaluacionesCurso.stream().map(Evaluacion::getId).toList();
        List<Entrega> entregasUsuario =
                repoEntregas.buscarPorUsuarioYEvaluaciones(idUsuario, idsEvaluaciones);

        return evaluacionesCurso.stream().allMatch(ev ->
                entregasUsuario.stream().anyMatch(e ->
                        e.getEvaluacion().getId().equals(ev.getId())
                        && e.isAprobado()));
    }

    /**
     * Resultado interno de la calificación de una evaluación.
     *
     * @param puntuacion        puntaje bruto obtenido
     * @param puntuacionMaxima  puntaje máximo posible
     * @param porcentajePuntaje porcentaje de aciertos (0-100)
     * @param aprobado          {@code true} si supera el umbral de aprobación
     */
    private record ResultadoCalificacion(
            BigDecimal puntuacion,
            BigDecimal puntuacionMaxima,
            double     porcentajePuntaje,
            boolean    aprobado) {}
}
