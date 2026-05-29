# Build Stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy parent POM and all child module POMs to leverage Docker cache for dependencies
COPY pom.xml .
COPY user-service/pom.xml user-service/
COPY payment-service/pom.xml payment-service/
COPY ivr-service/pom.xml ivr-service/
COPY notification-service/pom.xml notification-service/
COPY gateway-service/pom.xml gateway-service/

# Cache dependencies for the target module (gateway-service) and its parent dependencies
RUN mvn dependency:go-offline -B -pl gateway-service

# Copy actual source code
COPY gateway-service/src gateway-service/src

# Compile and package gateway-service
RUN mvn clean package -DskipTests -pl gateway-service

# Runtime Stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as a secure, non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy build artifact
COPY --from=build /app/gateway-service/target/*.jar app.jar

# Expose standard port
EXPOSE 9000

# Tune JVM for containers
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
