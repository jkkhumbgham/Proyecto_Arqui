package com.puj.cursos.cursos.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.puj.cursos.cursos.dominio.Curso;
import com.puj.cursos.cursos.dominio.EstadoCurso;
import com.puj.cursos.modulos.dominio.Modulo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO de salida que representa un curso con sus módulos y lecciones anidados.
 *
 * <p>Se construye a partir de una entidad {@link Curso} mediante el método de fábrica
 * {@link #desde(Curso)}, aplicando filtrado de módulos y lecciones con borrado lógico.
 * Los nombres de propiedad JSON mantienen el esquema inglés para compatibilidad
 * con el frontend existente.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public record RespuestaCurso(
        @JsonProperty("id")             UUID         id,
        @JsonProperty("title")          String       titulo,
        @JsonProperty("description")    String       descripcion,
        @JsonProperty("instructorId")   UUID         idInstructor,
        @JsonProperty("status")         EstadoCurso  estado,
        @JsonProperty("coverImageUrl")  String       urlImagenPortada,
        @JsonProperty("maxStudents")    Integer      maxEstudiantes,
        @JsonProperty("moduleCount")    int          cantidadModulos,
        @JsonProperty("modules")        List<InfoModulo> modulos,
        @JsonProperty("createdAt")      Instant      creadoEn
) {

    /**
     * Proyección de lección usada dentro de {@link InfoModulo}.
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public record InfoLeccion(
            @JsonProperty("id")              UUID    id,
            @JsonProperty("title")           String  titulo,
            @JsonProperty("content")         String  contenido,
            @JsonProperty("orderIndex")      int     ordenIndex,
            @JsonProperty("durationMinutes") Integer duracionMinutos,
            @JsonProperty("contentType")     String  tipoContenido,
            @JsonProperty("contentUrl")      String  urlContenido,
            @JsonProperty("supplementary")   boolean esSuplementaria) {}

    /**
     * Proyección de módulo que incluye la lista de sus lecciones activas.
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public record InfoModulo(
            @JsonProperty("id")          UUID            id,
            @JsonProperty("title")       String          titulo,
            @JsonProperty("description") String          descripcion,
            @JsonProperty("orderIndex")  int             ordenIndex,
            @JsonProperty("lessons")     List<InfoLeccion> lecciones) {}

    /**
     * Construye un {@code RespuestaCurso} a partir de la entidad {@link Curso}.
     *
     * <p>Filtra módulos y lecciones con borrado lógico ({@code eliminadoEn != null})
     * antes de construir las proyecciones anidadas.
     *
     * @param  c entidad de curso a proyectar; no debe ser {@code null}
     * @return DTO con la representación completa del curso
     */
    public static RespuestaCurso desde(Curso c) {
        List<Modulo> mods = c.obtenerModulos() == null
                ? List.of()
                : c.obtenerModulos().stream().filter(m -> !m.estaEliminado()).toList();

        return new RespuestaCurso(
                c.getId(),
                c.obtenerTitulo(),
                c.obtenerDescripcion(),
                c.obtenerIdInstructor(),
                c.obtenerEstado(),
                c.obtenerUrlImagenPortada(),
                c.obtenerMaxEstudiantes(),
                mods.size(),
                mods.stream().map(m -> {
                    List<InfoLeccion> lecciones = m.obtenerLecciones() == null
                            ? List.of()
                            : m.obtenerLecciones().stream()
                                    .filter(l -> !l.estaEliminada())
                                    .map(l -> new InfoLeccion(
                                            l.getId(),
                                            l.obtenerTitulo(),
                                            l.obtenerContenido(),
                                            l.obtenerOrdenIndex(),
                                            l.obtenerDuracionMinutos(),
                                            l.obtenerTipoContenido(),
                                            l.obtenerUrlContenido(),
                                            l.esSuplementaria()))
                                    .toList();
                    return new InfoModulo(
                            m.getId(),
                            m.obtenerTitulo(),
                            m.obtenerDescripcion(),
                            m.obtenerOrdenIndex(),
                            lecciones);
                }).toList(),
                c.obtenerCreadoEn()
        );
    }
}
