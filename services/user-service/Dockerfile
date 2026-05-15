# ─── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /build

# Copiar POMs del monorepo para aprovechar la caché de capas de Maven
COPY pom.xml .
COPY libs/security/pom.xml     libs/security/pom.xml
COPY libs/events/pom.xml       libs/events/pom.xml
COPY services/user-service/pom.xml services/user-service/pom.xml
COPY services/course-service/pom.xml       services/course-service/pom.xml
COPY services/assessment-service/pom.xml   services/assessment-service/pom.xml
COPY services/collaboration-service/pom.xml services/collaboration-service/pom.xml
COPY frontend/web-ui/pom.xml   frontend/web-ui/pom.xml

RUN mvn dependency:go-offline -pl libs/security,libs/events,services/user-service -am -q

# Copiar código fuente
COPY libs/security/src     libs/security/src
COPY libs/events/src       libs/events/src
COPY services/user-service/src services/user-service/src

# Compilar sólo los módulos necesarios
RUN mvn clean package -pl libs/security,libs/events,services/user-service -am \
    -DskipTests=false -q

# ─── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM quay.io/wildfly/wildfly:31.0.0.Final-jdk17
LABEL maintainer="puj-arqui@puj.edu.co"

USER root

# Módulo PostgreSQL JDBC para WildFly
RUN mkdir -p /opt/jboss/wildfly/modules/org/postgresql/main
COPY --from=builder /root/.m2/repository/org/postgresql/postgresql/42.7.3/postgresql-42.7.3.jar \
    /opt/jboss/wildfly/modules/org/postgresql/main/postgresql.jar
COPY infra/wildfly/postgresql-module.xml \
    /opt/jboss/wildfly/modules/org/postgresql/main/module.xml

# Script de configuración del datasource
COPY services/user-service/src/main/resources/jboss-cli-datasource.cli \
    /opt/jboss/wildfly/configure-ds.cli

# Configurar WildFly con el datasource en tiempo de build
RUN /opt/jboss/wildfly/bin/add-user.sh admin admin_secret --silent && \
    /opt/jboss/wildfly/bin/standalone.sh --admin-only & \
    sleep 20 && \
    /opt/jboss/wildfly/bin/jboss-cli.sh --connect --file=/opt/jboss/wildfly/configure-ds.cli ; \
    /opt/jboss/wildfly/bin/jboss-cli.sh --connect command=:shutdown ; \
    wait

# Deploy WAR
COPY --from=builder /build/services/user-service/target/user-service.war \
    /opt/jboss/wildfly/standalone/deployments/ROOT.war

USER jboss
EXPOSE 8080 9990

CMD ["/opt/jboss/wildfly/bin/standalone.sh", \
     "-b", "0.0.0.0", \
     "-bmanagement", "0.0.0.0"]
