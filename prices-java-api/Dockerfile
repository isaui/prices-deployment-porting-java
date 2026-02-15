# Build stage
FROM gradle:8.6-jdk21-alpine AS builder

WORKDIR /home/gradle/src

# Copy Gradle build definition first to maximize layer caching
COPY build.gradle .
COPY settings.gradle .
COPY gradle.properties .

# Copy source code
COPY src ./src

# Build application (skip tests for faster image builds)
RUN gradle --no-daemon build -x test

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install dependencies for deployment pipeline (docker, docker-compose)
RUN apk --no-cache add ca-certificates docker-cli docker-compose

# Copy JAR from builder (shadow jar is usually named *-all.jar or similar depending on shadow plugin config)
COPY --from=builder /home/gradle/src/build/libs/*-all.jar application.jar

# Create directories for deployments and nginx config
RUN mkdir -p /var/prices/deployments /var/prices/nginx/conf.d

EXPOSE 8080

CMD ["java", "-jar", "application.jar"]
