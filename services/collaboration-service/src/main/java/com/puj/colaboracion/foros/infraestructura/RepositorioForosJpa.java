package com.puj.colaboracion.foros.infraestructura;

import com.puj.colaboracion.foros.dominio.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementación JPA del repositorio de foros, hilos y publicaciones.
 *
 * <p>Todas las consultas de listado excluyen registros eliminados lógicamente
 * ({@code deleted_at IS NULL}). La consulta de hilos con conteo de publicaciones
 * utiliza una subconsulta correlacionada para obtener el número de respuestas
 * activas de cada hilo.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class RepositorioForosJpa implements RepositorioForos {

    @PersistenceContext(unitName = "CollaborationServicePU")
    private EntityManager em;

    /** {@inheritDoc} */
    @Override
    public Foro guardar(Foro foro) {
        if (foro.getId() == null) { em.persist(foro); return foro; }
        return em.merge(foro);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Foro> buscarPorId(UUID id) {
        return Optional.ofNullable(em.find(Foro.class, id))
                .filter(f -> !f.estaEliminado());
    }

    /** {@inheritDoc} */
    @Override
    public List<Foro> buscarTodos(int pagina, int cantidad) {
        return em.createQuery(
                "SELECT f FROM Foro f "
                + "WHERE f.eliminadoEn IS NULL "
                + "ORDER BY f.creadoEn DESC",
                Foro.class)
                .setFirstResult(pagina * cantidad)
                .setMaxResults(cantidad)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Foro> buscarPorCurso(UUID idCurso) {
        try {
            return Optional.of(em.createQuery(
                            "SELECT f FROM Foro f "
                            + "WHERE f.idCurso = :cid AND f.eliminadoEn IS NULL",
                            Foro.class)
                    .setParameter("cid", idCurso)
                    .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /** {@inheritDoc} */
    @Override
    public Hilo guardarHilo(Hilo hilo) {
        em.persist(hilo);
        return hilo;
    }

    /** {@inheritDoc} */
    @Override
    public Hilo fusionarHilo(Hilo hilo) {
        return em.merge(hilo);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Hilo> buscarHiloPorId(UUID id) {
        return Optional.ofNullable(em.find(Hilo.class, id))
                .filter(h -> !h.estaEliminado());
    }

    /** {@inheritDoc} */
    @Override
    public List<Object[]> buscarHilosConConteoPublicaciones(UUID idForo) {
        return em.createQuery(
                "SELECT h, "
                + "(SELECT COUNT(p) FROM Publicacion p "
                + " WHERE p.hilo = h AND p.eliminadoEn IS NULL) "
                + "FROM Hilo h "
                + "WHERE h.foro.id = :fid AND h.eliminadoEn IS NULL "
                + "ORDER BY h.fijado DESC, h.creadoEn DESC",
                Object[].class)
                .setParameter("fid", idForo).getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public List<Publicacion> buscarPublicacionesPorHilo(UUID idHilo) {
        return em.createQuery(
                "SELECT p FROM Publicacion p "
                + "WHERE p.hilo.id = :tid AND p.eliminadoEn IS NULL "
                + "ORDER BY p.creadoEn ASC",
                Publicacion.class)
                .setParameter("tid", idHilo).getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public Publicacion guardarPublicacion(Publicacion publicacion) {
        if (publicacion.getId() == null) { em.persist(publicacion); return publicacion; }
        return em.merge(publicacion);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Publicacion> buscarPublicacionPorId(UUID id) {
        return Optional.ofNullable(em.find(Publicacion.class, id))
                .filter(p -> !p.estaEliminada());
    }
}
