package com.puj.cursos.cursos.infraestructura;

import com.puj.cursos.cursos.dominio.Curso;
import com.puj.cursos.cursos.dominio.EstadoCurso;
import com.puj.cursos.cursos.dominio.RepositorioCursos;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementación JPA del repositorio de {@link Curso cursos}.
 *
 * <p>Encapsula todas las consultas JPQL relacionadas con cursos. Los métodos de
 * búsqueda excluyen automáticamente los registros con borrado lógico
 * ({@code deleted_at IS NOT NULL}).
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class RepositorioCursosJpa implements RepositorioCursos {

    @PersistenceContext(unitName = "CourseServicePU")
    private EntityManager em;

    /**
     * {@inheritDoc}
     */
    @Override
    public Curso guardar(Curso curso) {
        if (curso.getId() == null) {
            em.persist(curso);
            return curso;
        }
        return em.merge(curso);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Curso> buscarPorId(UUID id) {
        return Optional.ofNullable(em.find(Curso.class, id))
                .filter(c -> !c.estaEliminado());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Curso> buscarPublicados(int pagina, int tamano) {
        return em.createQuery(
                        "SELECT c FROM Curso c"
                        + " WHERE c.estado = :estado AND c.eliminadoEn IS NULL"
                        + " ORDER BY c.creadoEn DESC",
                        Curso.class)
                .setParameter("estado", EstadoCurso.PUBLISHED)
                .setFirstResult(pagina * tamano)
                .setMaxResults(tamano)
                .getResultList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Curso> buscarPorInstructor(UUID idInstructor, int pagina, int tamano) {
        return em.createQuery(
                        "SELECT c FROM Curso c"
                        + " WHERE c.idInstructor = :iid AND c.eliminadoEn IS NULL"
                        + " ORDER BY c.creadoEn DESC",
                        Curso.class)
                .setParameter("iid", idInstructor)
                .setFirstResult(pagina * tamano)
                .setMaxResults(tamano)
                .getResultList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long contarPublicados() {
        return em.createQuery(
                        "SELECT COUNT(c) FROM Curso c"
                        + " WHERE c.estado = :estado AND c.eliminadoEn IS NULL",
                        Long.class)
                .setParameter("estado", EstadoCurso.PUBLISHED)
                .getSingleResult();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean esPropietario(UUID idCurso, UUID idInstructor) {
        try {
            em.createQuery(
                            "SELECT c.id FROM Curso c"
                            + " WHERE c.id = :id"
                            + "   AND c.idInstructor = :iid"
                            + "   AND c.eliminadoEn IS NULL",
                            UUID.class)
                    .setParameter("id",  idCurso)
                    .setParameter("iid", idInstructor)
                    .getSingleResult();
            return true;
        } catch (NoResultException e) {
            return false;
        }
    }
}
