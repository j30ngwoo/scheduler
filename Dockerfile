FROM eclipse-temurin:21-jre-alpine

ARG JAR_FILE=target/scheduler-0.0.1.jar

COPY ${JAR_FILE} app.jar

EXPOSE 9003

ENTRYPOINT ["java", "-jar", "/app.jar"]