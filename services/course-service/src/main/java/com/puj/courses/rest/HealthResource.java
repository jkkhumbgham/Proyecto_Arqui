package com.puj.courses.rest;

import com.puj.events.publisher.RabbitMQConnectionProvider;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Path("/health")
public class HealthResource {

    @Inject
    private RabbitMQConnectionProvider rabbitMQ;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        boolean mqOk = rabbitMQ != null && rabbitMQ.isAvailable();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status",    "UP");
        body.put("service",   "course-service");
        body.put("timestamp", Instant.now().toString());
        body.put("rabbitMQ",  mqOk);
        return Response.ok(body).build();
    }
}
