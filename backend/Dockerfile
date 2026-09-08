FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline
COPY src src
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
# Runs as a non-root user rather than the image's default root — standard hardening for
# anything reachable from the internet (ECS Express Mode puts this behind a public HTTPS
# endpoint via its managed Application Load Balancer).
RUN addgroup -S techconnect && adduser -S techconnect -G techconnect
COPY --from=build /app/target/*.jar app.jar
USER techconnect
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
