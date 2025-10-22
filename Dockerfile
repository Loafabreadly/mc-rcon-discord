# Use Maven with Eclipse Temurin JDK 21 for building
FROM maven:3.9.5-eclipse-temurin-21 AS builder

# Add metadata
LABEL org.opencontainers.image.title="Minecraft RCON Discord Bot"
LABEL org.opencontainers.image.description="A comprehensive Discord bot for managing Minecraft servers via RCON"
LABEL org.opencontainers.image.version="1.0.0"
LABEL org.opencontainers.image.authors="Brad Snurka brad@snurka.tech"
LABEL org.opencontainers.image.source="https://github.com/loafabreadly/minecraft-rcon-discord-bot"
LABEL org.opencontainers.image.documentation="https://github.com/loafabreadly/minecraft-rcon-discord-bot/blob/main/README.md"
LABEL org.opencontainers.image.vendor="Open Source"
LABEL org.opencontainers.image.licenses="MIT"

WORKDIR /app

# Copy Maven files
COPY pom.xml .
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Use Eclipse Temurin JRE 21 for runtime (smaller image)
FROM eclipse-temurin:21-jre

# Create app directory and user
RUN groupadd -r mcbot && useradd -r -g mcbot mcbot
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/target/mc-rcon-discord-*.jar app.jar

# Create directories for logs and config
RUN mkdir -p logs config && \
    chown -R mcbot:mcbot /app

# Switch to non-root user
USER mcbot

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD ps aux | grep '[j]ava' || exit 1

# Set JVM options optimized for containers
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseContainerSupport"

# Expose port for health monitoring (if you add a health endpoint later)
EXPOSE 8080

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]