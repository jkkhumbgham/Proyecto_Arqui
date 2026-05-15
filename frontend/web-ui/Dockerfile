FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
COPY libs/security/pom.xml     libs/security/pom.xml
COPY libs/events/pom.xml       libs/events/pom.xml
COPY frontend/web-ui/pom.xml   frontend/web-ui/pom.xml
COPY services/user-service/pom.xml       services/user-service/pom.xml
COPY services/course-service/pom.xml     services/course-service/pom.xml
COPY services/assessment-service/pom.xml services/assessment-service/pom.xml
COPY services/collaboration-service/pom.xml services/collaboration-service/pom.xml
RUN mvn dependency:go-offline -pl libs/security,frontend/web-ui -am -q
COPY libs/security/src libs/security/src
COPY frontend/web-ui/src frontend/web-ui/src
RUN mvn clean package -pl libs/security,frontend/web-ui -am -DskipTests -q

FROM quay.io/wildfly/wildfly:31.0.0.Final-jdk17
COPY --from=builder /build/frontend/web-ui/target/web-ui.war \
    /opt/jboss/wildfly/standalone/deployments/ROOT.war
EXPOSE 8080
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0"]
