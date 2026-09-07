# ==============================================================================
# Stage 1: Build & Package with Maven
# ==============================================================================
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build

# Cache Maven dependencies by copying pom.xml first
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build production jar
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==============================================================================
# Stage 2: Minimal Production JRE Runtime
# ==============================================================================
FROM eclipse-temurin:17-jre-alpine AS runner

# Create dedicated unprivileged system group and user (UID/GID 1001)
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup -s /sbin/nologin

WORKDIR /app

# Copy executable jar from builder stage
COPY --from=builder --chown=appuser:appgroup /build/target/backend-0.0.1-SNAPSHOT.jar /app/app.jar

# Container-aware JVM memory settings and secure random source
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Expose internal backend port
EXPOSE 8080

# Health check using BusyBox wget included in Alpine Linux
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/api/health | grep -q "UP" || exit 1

# Create logs directory owned by appuser
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app/logs

# Run as non-root user
USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]
