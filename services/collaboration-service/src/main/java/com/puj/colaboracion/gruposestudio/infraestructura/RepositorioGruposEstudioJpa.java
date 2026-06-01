package com.puj.colaboracion.gruposestudio.infraestructura;

import com.puj.colaboracion.gruposestudio.dominio.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementación JPA del repositorio de grupos de estudio, miembros y mensajes.
 *
 * <p>Las consultas de membresía filtran por {@code deleted_at IS NULL} para obtener
 * solo miembros activos. El historial de chat se recupera en orden descendente
 * y se invierte en memoria para presentar los mensajes en orden cronológico.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class RepositorioGruposEstudioJpa implements RepositorioGruposEstudio {

    @PersistenceContext(unitName = "CollaborationServicePU")
    private EntityManager em;

    /** {@inheritDoc} */
    @Override
    public GrupoEstudio guardar(GrupoEstudio grupo) {
        em.persist(grupo);
        return grupo;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<GrupoEstudio> buscarPorId(UUID id) {
        return Optional.ofNullable(em.find(GrupoEstudio.class, id))
                .filter(g -> !g.estaEliminado());
    }

    /** {@inheritDoc} */
    @Override
    public List<GrupoEstudio> buscarPorIdCurso(UUID idCurso) {
        return em.createQuery(
                "SELECT g FROM GrupoEstudio g "
                + "WHERE g.idCurso = :c AND g.eliminadoEn IS NULL",
                GrupoEstudio.class)
                .setParameter("c", idCurso)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public void agregarMiembro(MiembroGrupo miembro) { em.persist(miembro); }

    /** {@inheritDoc} */
    @Override
    public MiembroGrupo fusionarMiembro(MiembroGrupo miembro) {
        return em.merge(miembro);
    }

    /** {@inheritDoc} */
    @Override
    public List<MiembroGrupo> buscarMiembrosActivos(UUID idGrupo) {
        return em.createQuery(
                "SELECT m FROM MiembroGrupo m "
                + "WHERE m.grupo.id = :g AND m.eliminadoEn IS NULL",
                MiembroGrupo.class)
                .setParameter("g", idGrupo).getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<MiembroGrupo> buscarMiembroPorGrupoYUsuario(
            UUID idGrupo, UUID idUsuario) {
        try {
            return Optional.of(em.createQuery(
                    "SELECT m FROM MiembroGrupo m "
                    + "WHERE m.grupo.id = :g "
                    + "AND m.idUsuario = :u "
                    + "AND m.eliminadoEn IS NULL",
                    MiembroGrupo.class)
                    .setParameter("g", idGrupo)
                    .setParameter("u", idUsuario)
                    .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /** {@inheritDoc} */
    @Override
    public Optional<MiembroGrupo> buscarMiembroPorGrupoYUsuarioCualquiera(
            UUID idGrupo, UUID idUsuario) {
        try {
            return Optional.of(em.createQuery(
                    "SELECT m FROM MiembroGrupo m "
                    + "WHERE m.grupo.id = :g AND m.idUsuario = :u",
                    MiembroGrupo.class)
                    .setParameter("g", idGrupo)
                    .setParameter("u", idUsuario)
                    .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean esMiembro(UUID idGrupo, UUID idUsuario) {
        Long conteo = em.createQuery(
                "SELECT COUNT(m) FROM MiembroGrupo m "
                + "WHERE m.grupo.id = :g "
                + "AND m.idUsuario = :u "
                + "AND m.eliminadoEn IS NULL",
                Long.class)
                .setParameter("g", idGrupo)
                .setParameter("u", idUsuario)
                .getSingleResult();
        return conteo > 0;
    }

    /** {@inheritDoc} */
    @Override
    public void guardarMensaje(MensajeChat mensaje) { em.persist(mensaje); }

    /** {@inheritDoc} */
    @Override
    public List<MensajeChat> buscarMensajesRecientes(UUID idGrupo, int limite) {
        List<MensajeChat> mensajes = em.createQuery(
                "SELECT m FROM MensajeChat m "
                + "WHERE m.idGrupo = :g "
                + "ORDER BY m.enviadoEn DESC",
                MensajeChat.class)
                .setParameter("g", idGrupo)
                .setMaxResults(limite)
                .getResultList();
        Collections.reverse(mensajes);
        return mensajes;
    }
}
