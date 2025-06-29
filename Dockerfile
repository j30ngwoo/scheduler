FROM eclipse-temurin:21-jre-alpine

ARG JAR_FILE=build/libs/scheduler-0.0.1-SNAPSHOT.jar

COPY ${JAR_FILE} app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app.jar"]