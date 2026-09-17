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
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/target/ARVELLO.jar /app/app.jar
RUN mkdir -p /app/logs && chown 10001:0 /app/logs

USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
