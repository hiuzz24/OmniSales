# =============================================================================
# Backend production image
#
# Run stage highlights:
#   * `eclipse-temurin:17-jre-alpine` keeps the image small.
#   * `apk add postgresql-client` provides `pg_dump`, which is required by
#     `BackupServiceImpl` to produce .backup / .sql dumps.
#   * `curl` is installed for the HEALTHCHECK probe.
#   * A non-root `osms` user owns the working directory and `/app/backups`
#     so the BackupScheduler can write there safely.
# =============================================================================

# Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy parent pom
COPY pom.xml .

# Copy backend module
COPY backend/pom.xml backend/
COPY backend/src backend/src

# Build backend module
RUN mvn -pl backend -am clean package -DskipTests

# Run stage
FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache postgresql-client curl \
    && addgroup -S osms && adduser -S osms -G osms
WORKDIR /app
COPY --from=build /app/backend/target/*.jar app.jar
RUN mkdir -p /app/backups && chown -R osms:osms /app

USER osms

# Expose backend port
EXPOSE 8080

# Liveness probe — pings a public read-only endpoint.
# Compose / orchestrator can override this via its own healthcheck block.
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD curl -fsS http://localhost:8080/api/address/countries || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
