# ─── Stage 1: Build ─────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /build

# Copiar POMs para cache de dependencias
COPY pom.xml .
COPY libs/events/pom.xml       libs/events/pom.xml
COPY services/email-service/pom.xml services/email-service/pom.xml

# Descargar dependencias offline
RUN mvn dependency:go-offline -pl libs/events,services/email-service -am -q

# Copiar fuentes
COPY libs/events/src     libs/events/src
COPY services/email-service/src services/email-service/src

# Compilar (incluye tests)
RUN mvn clean package -pl libs/events,services/email-service -am -DskipTests=false -q

# ─── Stage 2: Runtime ───────────────────────────────────────────────────────
FROM quay.io/wildfly/wildfly:31.0.0.Final-jdk17
LABEL maintainer="puj-arqui@puj.edu.co"

USER root

# Deploy WAR — no necesita datasource ni módulo PostgreSQL
COPY --from=builder /build/services/email-service/target/email-service.war \
    /opt/jboss/wildfly/standalone/deployments/ROOT.war

USER jboss
EXPOSE 8080

CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0"]
