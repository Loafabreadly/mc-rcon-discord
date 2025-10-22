# Use Maven with Eclipse Temurin JDK 21 for building
FROM maven:3.9.5-eclipse-temurin-21 AS builder

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
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# Set JVM options
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Expose port (if you add a health endpoint later)
EXPOSE 8080

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]