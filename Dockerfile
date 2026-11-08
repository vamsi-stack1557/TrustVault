FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /data/private-files && chown -R 10001:0 /app /data && chmod -R g=u /app /data
COPY --from=build /workspace/target/trustvault-1.0.0.jar /app/trustvault.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/trustvault.jar"]
