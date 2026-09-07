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
# anything reachable from the internet (App Runner puts this behind a public HTTPS endpoint).
RUN addgroup -S techdispatch && adduser -S techdispatch -G techdispatch
COPY --from=build /app/target/*.jar app.jar
USER techdispatch
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
