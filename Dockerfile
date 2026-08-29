FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY .mvn /workspace/.mvn
COPY mvnw pom.xml /workspace/
RUN sed -i 's/\r$//' /workspace/mvnw \
    && chmod +x /workspace/mvnw \
    && ./mvnw -B dependency:go-offline

COPY src /workspace/src
RUN ./mvnw -B clean test package

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/target/ARVELLO.jar /app/app.jar

USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
