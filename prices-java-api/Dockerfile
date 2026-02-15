# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy gradle files
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Download dependencies (if you want to cache them, but gradlew build will do it anyway)
# RUN ./gradlew dependencies

# Copy source code
COPY src src

# Build application
RUN ./gradlew build -x test

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install dependencies for deployment pipeline (docker, docker-compose)
RUN apk --no-cache add ca-certificates docker-cli docker-compose

# Copy JAR from builder (shadow jar is usually named *-all.jar or similar depending on shadow plugin config)
COPY --from=builder /app/build/libs/prices-java-api-0.1-all.jar application.jar

# Create directories for deployments and nginx config
RUN mkdir -p /var/prices/deployments /var/prices/nginx/conf.d

EXPOSE 8080

CMD ["java", "-jar", "application.jar"]
