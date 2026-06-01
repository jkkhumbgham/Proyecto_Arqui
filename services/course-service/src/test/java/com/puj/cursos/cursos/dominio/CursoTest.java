package com.puj.cursos.cursos.dominio;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios del dominio {@link Curso}.
 *
 * <p>Verifica las invariantes y comportamientos del modelo de dominio sin necesidad
 * de contexto de persistencia ni contenedor CDI.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
class CursoTest {

    /**
     * Un curso recién creado con título válido debe tener estado DRAFT y no estar eliminado.
     */
    @Test
    void cursoNuevoTieneEstadoBorradorYNoEstaEliminado() {
        Curso curso = new Curso();
        curso.establecerTitulo("Introducción a Java");
        curso.establecerIdInstructor(UUID.randomUUID());

        assertEquals("Introducción a Java", curso.obtenerTitulo());
        assertEquals(EstadoCurso.DRAFT, curso.obtenerEstado());
        assertFalse(curso.estaEliminado());
    }

    /**
     * El borrado lógico debe marcar el curso como eliminado sin modificar otros campos.
     */
    @Test
    void eliminarLogicamenteMarcaCursoComoEliminado() {
        Curso curso = new Curso();
        curso.establecerTitulo("Curso de Prueba");
        curso.establecerIdInstructor(UUID.randomUUID());

        assertFalse(curso.estaEliminado());
        curso.eliminarLogicamente();
        assertTrue(curso.estaEliminado());
        assertNotNull(curso.obtenerEliminadoEn());
    }

    /**
     * Cambiar el estado del curso debe reflejarse correctamente.
     */
    @Test
    void cambiarEstadoCursoFuncionaCorrectamente() {
        Curso curso = new Curso();
        curso.establecerTitulo("Curso de Arquitectura");
        curso.establecerIdInstructor(UUID.randomUUID());

        assertEquals(EstadoCurso.DRAFT, curso.obtenerEstado());

        curso.establecerEstado(EstadoCurso.PUBLISHED);
        assertEquals(EstadoCurso.PUBLISHED, curso.obtenerEstado());

        curso.establecerEstado(EstadoCurso.ARCHIVED);
        assertEquals(EstadoCurso.ARCHIVED, curso.obtenerEstado());
    }

    /**
     * El título y la descripción se pueden modificar mediante los setters correspondientes.
     */
    @Test
    void establecerTituloYDescripcionActualizaCamposCorrectamente() {
        Curso curso = new Curso();
        curso.establecerTitulo("Título Original");
        curso.establecerDescripcion("Descripción original");

        curso.establecerTitulo("Título Actualizado");
        curso.establecerDescripcion("Descripción actualizada");

        assertEquals("Título Actualizado", curso.obtenerTitulo());
        assertEquals("Descripción actualizada", curso.obtenerDescripcion());
    }

    /**
     * El identificador del instructor se asigna correctamente.
     */
    @Test
    void idInstructorSeAsignaCorrectamente() {
        UUID idInstructor = UUID.randomUUID();
        Curso curso = new Curso();
        curso.establecerTitulo("Curso con Instructor");
        curso.establecerIdInstructor(idInstructor);

        assertEquals(idInstructor, curso.obtenerIdInstructor());
    }

    // ── TU-011: borrado lógico registra timestamp de eliminación ─────────────
    @Test
    void eliminarLogicamente_registraEliminadoEnConTimestamp() {
        Curso curso = new Curso();
        curso.establecerTitulo("Curso con Timestamp");
        curso.establecerIdInstructor(UUID.randomUUID());

        assertNull(curso.obtenerEliminadoEn(),
                "eliminadoEn debe ser null antes del borrado lógico");

        curso.eliminarLogicamente();

        assertTrue(curso.estaEliminado());
        assertNotNull(curso.obtenerEliminadoEn(),
                "eliminadoEn debe tener timestamp tras el borrado lógico");
    }

    // ── TU-012: transición completa de estados DRAFT → PUBLISHED → ARCHIVED ─
    @Test
    void establecerEstado_transicionCompletaDraftPublishedArchived() {
        Curso curso = new Curso();
        curso.establecerTitulo("Curso de Arquitectura de Software");
        curso.establecerIdInstructor(UUID.randomUUID());

        // Estado inicial debe ser DRAFT
        assertEquals(EstadoCurso.DRAFT, curso.obtenerEstado(),
                "Estado inicial debe ser DRAFT");

        // DRAFT → PUBLISHED
        curso.establecerEstado(EstadoCurso.PUBLISHED);
        assertEquals(EstadoCurso.PUBLISHED, curso.obtenerEstado());

        // PUBLISHED → ARCHIVED
        curso.establecerEstado(EstadoCurso.ARCHIVED);
        assertEquals(EstadoCurso.ARCHIVED, curso.obtenerEstado());
    }

    // ── TU adicional: maxEstudiantes se asigna y recupera correctamente ──────
    @Test
    void maxEstudiantesSeAsignaCorrectamente() {
        Curso curso = new Curso();
        curso.establecerTitulo("Curso con límite");
        curso.establecerIdInstructor(UUID.randomUUID());
        curso.establecerMaxEstudiantes(30);

        assertEquals(30, curso.obtenerMaxEstudiantes());
    }
}
