package com.puj.cursos.matriculas.infraestructura;

import com.puj.cursos.matriculas.dominio.EstadoMatricula;
import com.puj.cursos.matriculas.dominio.Matricula;
import com.puj.cursos.matriculas.dominio.RepositorioMatriculas;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementación JPA del repositorio de {@link Matricula matrículas}.
 *
 * <p>Centraliza todas las consultas JPQL relacionadas con matrículas, incluyendo
 * consultas estadísticas usadas por el panel de administración y el dashboard del
 * instructor. Los métodos excluyen registros con borrado lógico salvo indicación
 * explícita en el nombre del método.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class RepositorioMatriculasJpa implements RepositorioMatriculas {

    @PersistenceContext(unitName = "CourseServicePU")
    private EntityManager em;

    /**
     * {@inheritDoc}
     */
    @Override
    public Matricula guardar(Matricula matricula) {
        em.persist(matricula);
        return matricula;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Matricula fusionar(Matricula matricula) {
        return em.merge(matricula);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Matricula> buscarPorUsuarioYCurso(UUID idUsuario, UUID idCurso) {
        try {
            return Optional.of(em.createQuery(
                            "SELECT m FROM Matricula m"
                            + " WHERE m.idUsuario = :uid"
                            + "   AND m.curso.id = :cid"
                            + "   AND m.eliminadoEn IS NULL",
                            Matricula.class)
                    .setParameter("uid", idUsuario)
                    .setParameter("cid", idCurso)
                    .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Matricula> buscarPorUsuario(UUID idUsuario, int pagina, int cantidad) {
        return em.createQuery(
                        "SELECT m FROM Matricula m"
                        + " WHERE m.idUsuario = :uid AND m.eliminadoEn IS NULL"
                        + " ORDER BY m.matriculadoEn DESC",
                        Matricula.class)
                .setParameter("uid", idUsuario)
                .setFirstResult(pagina * cantidad)
                .setMaxResults(cantidad)
                .getResultList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean estaMatriculado(UUID idUsuario, UUID idCurso) {
        return buscarPorUsuarioYCurso(idUsuario, idCurso).isPresent();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long contarPorCurso(UUID idCurso) {
        return em.createQuery(
                        "SELECT COUNT(m) FROM Matricula m"
                        + " WHERE m.curso.id = :cid AND m.eliminadoEn IS NULL",
                        Long.class)
                .setParameter("cid", idCurso)
                .getSingleResult();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double promedioProgresoPorCurso(UUID idCurso) {
        Double promedio = em.createQuery(
                        "SELECT AVG(m.porcentajeProgreso) FROM Matricula m"
                        + " WHERE m.curso.id = :cid AND m.eliminadoEn IS NULL",
                        Double.class)
                .setParameter("cid", idCurso)
                .getSingleResult();
        return promedio != null ? promedio : 0.0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Object[]> ultimoModuloPorUsuario(UUID idCurso) {
        return em.createQuery(
                        "SELECT p.idUsuario, l.modulo.id, l.modulo.titulo,"
                        + "       l.modulo.ordenIndex, MAX(p.completadoEn)"
                        + " FROM ProgresoLeccion p JOIN p.leccion l"
                        + " WHERE l.modulo.curso.id = :cid"
                        + "   AND l.modulo.eliminadoEn IS NULL"
                        + " GROUP BY p.idUsuario, l.modulo.id,"
                        + "          l.modulo.titulo, l.modulo.ordenIndex",
                        Object[].class)
                .setParameter("cid", idCurso)
                .getResultList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long contarEstudiantesUnicosEnCursos(List<UUID> idsCurso) {
        if (idsCurso == null || idsCurso.isEmpty()) return 0;
        return em.createQuery(
                        "SELECT COUNT(DISTINCT m.idUsuario) FROM Matricula m"
                        + " WHERE m.curso.id IN :ids AND m.eliminadoEn IS NULL",
                        Long.class)
                .setParameter("ids", idsCurso)
                .getSingleResult();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long contarSinIniciar(UUID idCurso) {
        return em.createQuery(
                        "SELECT COUNT(DISTINCT m.idUsuario) FROM Matricula m"
                        + " WHERE m.curso.id = :cid AND m.eliminadoEn IS NULL"
                        + "   AND m.idUsuario NOT IN ("
                        + "     SELECT DISTINCT p.idUsuario FROM ProgresoLeccion p"
                        + "     WHERE p.idCurso = :cid"
                        + "   )",
                        Long.class)
                .setParameter("cid", idCurso)
                .getSingleResult();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long contarTodas() {
        return em.createQuery(
                        "SELECT COUNT(m) FROM Matricula m WHERE m.eliminadoEn IS NULL",
                        Long.class)
                .getSingleResult();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long contarCompletadas() {
        return em.createQuery(
                        "SELECT COUNT(m) FROM Matricula m"
                        + " WHERE m.estado = :estado AND m.eliminadoEn IS NULL",
                        Long.class)
                .setParameter("estado", EstadoMatricula.COMPLETED)
                .getSingleResult();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Object[]> cursosMasPopulares(int limite) {
        return em.createQuery(
                        "SELECT m.curso.id, m.curso.titulo, COUNT(m)"
                        + " FROM Matricula m"
                        + " WHERE m.eliminadoEn IS NULL"
                        + " GROUP BY m.curso.id, m.curso.titulo"
                        + " ORDER BY COUNT(m) DESC",
                        Object[].class)
                .setMaxResults(limite)
                .getResultList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Object[]> cursosMasCompletados(int limite) {
        return em.createQuery(
                        "SELECT m.curso.id, m.curso.titulo, COUNT(m)"
                        + " FROM Matricula m"
                        + " WHERE m.estado = :estado AND m.eliminadoEn IS NULL"
                        + " GROUP BY m.curso.id, m.curso.titulo"
                        + " ORDER BY COUNT(m) DESC",
                        Object[].class)
                .setParameter("estado", EstadoMatricula.COMPLETED)
                .setMaxResults(limite)
                .getResultList();
    }
}
