package com.puj.security.correlation;

import jakarta.enterprise.context.RequestScoped;

/** Holds the X-Correlation-ID for the current request. Injected by CorrelationIdFilter. */
@RequestScoped
public class CorrelationContext {

    private String correlationId;

    public String get()           { return correlationId; }
    public void   set(String id)  { this.correlationId = id; }
}
