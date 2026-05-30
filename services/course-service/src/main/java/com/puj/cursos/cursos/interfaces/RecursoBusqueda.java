package com.puj.cursos.cursos.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.puj.cursos.cursos.dominio.Curso;
import com.puj.seguridad.redis.ProveedorRedis;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import redis.clients.jedis.Jedis;

import java.util.List;
import java.util.Map;

/**
 * Recurso REST que expone la búsqueda de cursos publicados con filtrado y caché Redis.
 *
 * <p>Permite buscar cursos por texto libre ({@code q}) y categoría ({@code category}),
 * con ordenación por fecha de creación o por título. Los resultados se almacenan en
 * caché Redis durante {@value #CACHE_TTL} segundos para reducir la carga en la base de
 * datos. Si Redis no está disponible, la búsqueda se ejecuta directamente contra la BD.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Path("/search")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Búsqueda")
public class RecursoBusqueda {

    /** Tiempo de vida de las entradas de caché en segundos (5 minutos). */
    private static final int CACHE_TTL = 300;

    @PersistenceContext(unitName = "CourseServicePU")
    private EntityManager em;

    @Inject
    private ProveedorRedis proveedorRedis;

    /** Mapper Jackson configurado para serializar fechas en formato ISO-8601. */
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Busca cursos publicados por texto y categoría con paginación y caché Redis.
     *
     * @param  q        texto a buscar en título y descripción (por defecto vacío = todos)
     * @param  categoria categoría exacta a filtrar (por defecto vacío = todas)
     * @param  orden    criterio de ordenación: {@code "newest"} (por fecha) o
     *                  cualquier otro valor para ordenar por título (por defecto newest)
     * @param  pagina   número de página, base 0 (por defecto 0)
     * @param  tamano   tamaño de página, entre 1 y 50 (por defecto 12)
     * @return respuesta 200 con {@code data}, {@code total}, {@code page},
     *         {@code size} y {@code totalPages}
     */
    @GET
    @Operation(summary = "Buscar cursos publicados por texto y categoría")
    public Response buscar(
            @QueryParam("q")        @DefaultValue("") String q,
            @QueryParam("category") @DefaultValue("") String categoria,
            @QueryParam("sort")     @DefaultValue("newest") String orden,
            @QueryParam("page")     @DefaultValue("0")  int pagina,
            @QueryParam("size")     @DefaultValue("12") int tamano) {

        if (tamano < 1 || tamano > 50) tamano = 12;
        if (pagina < 0) pagina = 0;

        String claveCache = "search:" + q + ":" + categoria + ":" + orden
                + ":" + pagina + ":" + tamano;

        try (Jedis jedis = proveedorRedis.obtenerPool().getResource()) {
            String cacheado = jedis.get(claveCache);
            if (cacheado != null) {
                return Response.ok(cacheado).type(MediaType.APPLICATION_JSON).build();
            }
        } catch (Exception ignorado) {}

        String ordenClausula = "newest".equals(orden) ? "c.creadoEn DESC" : "c.titulo ASC";

        StringBuilder jpql = new StringBuilder(
                "SELECT c FROM Curso c"
                + " WHERE c.estado = 'PUBLISHED' AND c.eliminadoEn IS NULL");

        if (!q.isBlank()) {
            jpql.append(" AND (LOWER(c.titulo) LIKE LOWER(:q)"
                    + " OR LOWER(c.descripcion) LIKE LOWER(:q))");
        }
        if (!categoria.isBlank()) {
            jpql.append(" AND LOWER(c.categoria) = LOWER(:cat)");
        }
        jpql.append(" ORDER BY ").append(ordenClausula);

        var consulta = em.createQuery(jpql.toString(), Curso.class);
        if (!q.isBlank())        consulta.setParameter("q",   "%" + q + "%");
        if (!categoria.isBlank()) consulta.setParameter("cat", categoria);
        consulta.setFirstResult(pagina * tamano).setMaxResults(tamano);

        List<Curso> resultados = consulta.getResultList();

        StringBuilder countJpql = new StringBuilder(
                "SELECT COUNT(c) FROM Curso c"
                + " WHERE c.estado = 'PUBLISHED' AND c.eliminadoEn IS NULL");
        if (!q.isBlank()) {
            countJpql.append(" AND (LOWER(c.titulo) LIKE LOWER(:q)"
                    + " OR LOWER(c.descripcion) LIKE LOWER(:q))");
        }
        if (!categoria.isBlank()) {
            countJpql.append(" AND LOWER(c.categoria) = LOWER(:cat)");
        }

        var consultaConteo = em.createQuery(countJpql.toString(), Long.class);
        if (!q.isBlank())        consultaConteo.setParameter("q",   "%" + q + "%");
        if (!categoria.isBlank()) consultaConteo.setParameter("cat", categoria);
        long total = consultaConteo.getSingleResult();

        List<Map<String, Object>> items = resultados.stream()
                .map(c -> Map.<String, Object>of(
                        "id",           c.getId(),
                        "title",        c.obtenerTitulo(),
                        "description",  c.obtenerDescripcion()       != null ? c.obtenerDescripcion()       : "",
                        "category",     c.obtenerCategoria()          != null ? c.obtenerCategoria()          : "",
                        "coverUrl",     c.obtenerUrlImagenPortada()   != null ? c.obtenerUrlImagenPortada()   : "",
                        "instructorId", c.obtenerIdInstructor(),
                        "createdAt",    c.obtenerCreadoEn().toString()
                ))
                .toList();

        Map<String, Object> cuerpo = Map.of(
                "data",       items,
                "total",      total,
                "page",       pagina,
                "size",       tamano,
                "totalPages", (int) Math.ceil((double) total / tamano)
        );

        try {
            String json = mapper.writeValueAsString(cuerpo);
            try (Jedis jedis = proveedorRedis.obtenerPool().getResource()) {
                jedis.setex(claveCache, CACHE_TTL, json);
            }
            return Response.ok(json).type(MediaType.APPLICATION_JSON).build();
        } catch (Exception e) {
            return Response.ok(cuerpo).build();
        }
    }
}
