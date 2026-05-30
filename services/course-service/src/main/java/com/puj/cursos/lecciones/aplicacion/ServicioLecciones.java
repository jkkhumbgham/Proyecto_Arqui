package com.puj.cursos.lecciones.aplicacion;

import com.puj.cursos.aspectos.registro.Registrable;
import com.puj.cursos.lecciones.dominio.Leccion;
import com.puj.cursos.lecciones.dominio.ProgresoLeccion;
import com.puj.cursos.matriculas.dominio.Matricula;
import com.puj.eventos.EventoLeccionCompletada;
import com.puj.eventos.publicador.PublicadorEventos;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Servicio de aplicación para la gestión del progreso de lecciones.
 *
 * <p>Encapsula la lógica de marcar una lección como completada, recalcular
 * el progreso de la matrícula y publicar el evento de analítica correspondiente.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
@Registrable
public class ServicioLecciones {

    @PersistenceContext(unitName = "CourseServicePU")
    private EntityManager em;

    @Inject private PublicadorEventos publicadorEventos;

    /**
     * Marca una lección como completada por el estudiante indicado.
     *
     * <p>Si la lección ya fue completada, la operación es idempotente y no crea
     * un registro duplicado. Si es la primera vez, persiste un {@link ProgresoLeccion},
     * publica un {@link EventoLeccionCompletada} y recalcula el porcentaje de progreso
     * de la matrícula.
     *
     * @param  leccion   entidad de la lección a completar; no debe ser {@code null}
     * @param  idUsuario identificador del estudiante
     * @return mapa con {@code yaCompletada}, {@code cantidadCompletadas},
     *         {@code totalLecciones} y {@code porcentajeProgreso}
     */
    @Transactional
    public Map<String, Object> completar(Leccion leccion, UUID idUsuario) {
        UUID idCurso  = leccion.obtenerModulo().obtenerCurso().getId();
        UUID idLeccion = leccion.getId();

        boolean yaCompletada;
        try {
            em.createQuery(
                    "SELECT p FROM ProgresoLeccion p"
                    + " WHERE p.idUsuario = :uid AND p.leccion.id = :lid",
                    ProgresoLeccion.class)
                    .setParameter("uid", idUsuario)
                    .setParameter("lid", idLeccion)
                    .getSingleResult();
            yaCompletada = true;
        } catch (NoResultException e) {
            ProgresoLeccion progreso = new ProgresoLeccion();
            progreso.establecerIdUsuario(idUsuario);
            progreso.establecerLeccion(leccion);
            progreso.establecerIdCurso(idCurso);
            em.persist(progreso);
            yaCompletada = false;
            publicadorEventos.publicarAnaliticas(new EventoLeccionCompletada(
                    idUsuario.toString(), idLeccion.toString(), idCurso.toString()));
        }

        long totalLecciones = em.createQuery(
                "SELECT COUNT(l) FROM Leccion l"
                + " WHERE l.modulo.curso.id = :cid"
                + "   AND l.eliminadoEn IS NULL AND l.esSuplementaria = false",
                Long.class)
                .setParameter("cid", idCurso)
                .getSingleResult();

        long completadas = em.createQuery(
                "SELECT COUNT(p) FROM ProgresoLeccion p JOIN p.leccion l"
                + " WHERE p.idUsuario = :uid AND p.idCurso = :cid"
                + "   AND l.esSuplementaria = false",
                Long.class)
                .setParameter("uid", idUsuario)
                .setParameter("cid", idCurso)
                .getSingleResult();

        double pct = totalLecciones > 0 ? (completadas * 100.0 / totalLecciones) : 0.0;

        try {
            Matricula matricula = em.createQuery(
                    "SELECT m FROM Matricula m"
                    + " WHERE m.idUsuario = :uid AND m.curso.id = :cid"
                    + "   AND m.eliminadoEn IS NULL",
                    Matricula.class)
                    .setParameter("uid", idUsuario)
                    .setParameter("cid", idCurso)
                    .getSingleResult();
            matricula.establecerPorcentajeProgreso(Math.min(100.0, pct));
            em.merge(matricula);
        } catch (NoResultException ignorado) {}

        return Map.of(
                "yaCompletada",      yaCompletada,
                "cantidadCompletadas", completadas,
                "totalLecciones",    totalLecciones,
                "porcentajeProgreso", Math.round(pct * 10.0) / 10.0
        );
    }
}
