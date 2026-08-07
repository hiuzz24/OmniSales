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
#
# Tương thích cả docker-compose (local + VPS) lẫn Render.com (free tier):
#   * JAVA_TOOL_OPTIONS được honor từ env → điều chỉnh RAM theo host.
#   * SPRING_PROFILES_ACTIVE để chọn profile (default/render/prod).
#   * Healthcheck path: /api/address/countries (public endpoint).
# =============================================================================

# Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy parent pom
COPY pom.xml .

# Copy backend module
COPY backend/pom.xml backend/
COPY backend/src backend/src

# Build backend module (B flag để batch mode, logs ngắn hơn → cache layer tốt hơn trên Render)
RUN mvn -pl backend -am clean package -DskipTests -B

# Run stage
FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache postgresql-client curl tzdata \
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

# Default JVM tuning for 512MB hosts (Render free tier). Override bằng cách truyền
# JAVA_TOOL_OPTIONS qua docker-compose env block hoặc Render env panel.
ENV JAVA_TOOL_OPTIONS="-Xmx384m -Xms192m -XX:+UseG1GC -XX:MaxRAMPercentage=75.0"

# Entrypoint nhận env từ runtime (Render / compose):
#   * JAVA_TOOL_OPTIONS — JVM tuning (RAM/GC)
#   * SPRING_PROFILES_ACTIVE — chọn profile (default/render/prod)
ENTRYPOINT ["sh", "-c", "exec java $JAVA_TOOL_OPTIONS -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-default} -jar app.jar"]
