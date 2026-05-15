FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
COPY libs/security/pom.xml     libs/security/pom.xml
COPY libs/events/pom.xml       libs/events/pom.xml
COPY services/collaboration-service/pom.xml services/collaboration-service/pom.xml
COPY services/user-service/pom.xml       services/user-service/pom.xml
COPY services/course-service/pom.xml     services/course-service/pom.xml
COPY services/assessment-service/pom.xml services/assessment-service/pom.xml
COPY frontend/web-ui/pom.xml   frontend/web-ui/pom.xml
RUN mvn dependency:go-offline -pl libs/security,libs/events,services/collaboration-service -am -q
COPY libs/security/src     libs/security/src
COPY libs/events/src       libs/events/src
COPY services/collaboration-service/src services/collaboration-service/src
RUN mvn clean package -pl libs/security,libs/events,services/collaboration-service -am -DskipTests=false -q

FROM quay.io/wildfly/wildfly:31.0.0.Final-jdk17
USER root
RUN mkdir -p /opt/jboss/wildfly/modules/org/postgresql/main
COPY --from=builder /root/.m2/repository/org/postgresql/postgresql/42.7.3/postgresql-42.7.3.jar \
    /opt/jboss/wildfly/modules/org/postgresql/main/postgresql.jar
COPY infra/wildfly/postgresql-module.xml \
    /opt/jboss/wildfly/modules/org/postgresql/main/module.xml
COPY --from=builder /build/services/collaboration-service/target/collaboration-service.war \
    /opt/jboss/wildfly/standalone/deployments/ROOT.war
USER jboss
EXPOSE 8080
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0"]
