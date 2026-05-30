package com.puj.courses.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.puj.courses.entity.Course;
import com.puj.security.redis.RedisClientProvider;
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
public class SearchResource {

    /** Tiempo de vida de las entradas de caché en segundos (5 minutos). */
    private static final int CACHE_TTL = 300;

    @PersistenceContext(unitName = "CourseServicePU")
    private EntityManager em;

    @Inject
    private RedisClientProvider redisProvider;

    /** Mapper Jackson configurado para serializar fechas en formato ISO-8601. */
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Busca cursos publicados por texto y categoría con paginación y caché Redis.
     *
     * <p>El resultado de la búsqueda se almacena en Redis con la clave compuesta
     * {@code search:{q}:{category}:{sort}:{page}:{size}}. Si Redis no está disponible,
     * la búsqueda se realiza directamente y la respuesta se devuelve sin cachear.
     *
     * @param  q        texto a buscar en título y descripción (por defecto vacío = todos)
     * @param  category categoría exacta a filtrar (por defecto vacío = todas)
     * @param  sort     criterio de ordenación: {@code "newest"} (por fecha) o
     *                  cualquier otro valor para ordenar por título (por defecto newest)
     * @param  page     número de página, base 0 (por defecto 0)
     * @param  size     tamaño de página, entre 1 y 50 (por defecto 12)
     * @return respuesta 200 con {@code data} (lista de cursos), {@code total},
     *         {@code page}, {@code size} y {@code totalPages}
     */
    @GET
    @Operation(summary = "Buscar cursos publicados por texto y categoría")
    public Response search(
            @QueryParam("q")        @DefaultValue("") String q,
            @QueryParam("category") @DefaultValue("") String category,
            @QueryParam("sort")     @DefaultValue("newest") String sort,
            @QueryParam("page")     @DefaultValue("0")  int page,
            @QueryParam("size")     @DefaultValue("12") int size) {

        if (size < 1 || size > 50) size = 12;
        if (page < 0) page = 0;

        String cacheKey = "search:" + q + ":" + category + ":" + sort
                + ":" + page + ":" + size;

        try (Jedis jedis = redisProvider.getPool().getResource()) {
            String cached = jedis.get(cacheKey);
            if (cached != null) {
                return Response.ok(cached).type(MediaType.APPLICATION_JSON).build();
            }
        } catch (Exception ignored) {}

        String orderClause = "newest".equals(sort) ? "c.createdAt DESC" : "c.title ASC";

        StringBuilder jpql = new StringBuilder(
                "SELECT c FROM Course c"
                + " WHERE c.status = 'PUBLISHED' AND c.deletedAt IS NULL");

        if (!q.isBlank()) {
            jpql.append(" AND (LOWER(c.title) LIKE LOWER(:q)"
                    + " OR LOWER(c.description) LIKE LOWER(:q))");
        }
        if (!category.isBlank()) {
            jpql.append(" AND LOWER(c.category) = LOWER(:cat)");
        }
        jpql.append(" ORDER BY ").append(orderClause);

        var query = em.createQuery(jpql.toString(), Course.class);
        if (!q.isBlank())        query.setParameter("q",   "%" + q + "%");
        if (!category.isBlank()) query.setParameter("cat", category);
        query.setFirstResult(page * size).setMaxResults(size);

        List<Course> results = query.getResultList();

        // Consulta de total para paginación
        StringBuilder countJpql = new StringBuilder(
                "SELECT COUNT(c) FROM Course c"
                + " WHERE c.status = 'PUBLISHED' AND c.deletedAt IS NULL");
        if (!q.isBlank()) {
            countJpql.append(" AND (LOWER(c.title) LIKE LOWER(:q)"
                    + " OR LOWER(c.description) LIKE LOWER(:q))");
        }
        if (!category.isBlank()) {
            countJpql.append(" AND LOWER(c.category) = LOWER(:cat)");
        }

        var countQuery = em.createQuery(countJpql.toString(), Long.class);
        if (!q.isBlank())        countQuery.setParameter("q",   "%" + q + "%");
        if (!category.isBlank()) countQuery.setParameter("cat", category);
        long total = countQuery.getSingleResult();

        List<Map<String, Object>> items = results.stream()
                .map(c -> Map.<String, Object>of(
                        "id",           c.getId(),
                        "title",        c.getTitle(),
                        "description",  c.getDescription()  != null ? c.getDescription()  : "",
                        "category",     c.getCategory()     != null ? c.getCategory()     : "",
                        "coverUrl",     c.getCoverImageUrl() != null ? c.getCoverImageUrl() : "",
                        "instructorId", c.getInstructorId(),
                        "createdAt",    c.getCreatedAt().toString()
                ))
                .toList();

        Map<String, Object> body = Map.of(
                "data",       items,
                "total",      total,
                "page",       page,
                "size",       size,
                "totalPages", (int) Math.ceil((double) total / size)
        );

        try {
            String json = mapper.writeValueAsString(body);
            try (Jedis jedis = redisProvider.getPool().getResource()) {
                jedis.setex(cacheKey, CACHE_TTL, json);
            }
            return Response.ok(json).type(MediaType.APPLICATION_JSON).build();
        } catch (Exception e) {
            return Response.ok(body).build();
        }
    }
}
