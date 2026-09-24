# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache dependencies first for faster rebuilds
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S orderflow && adduser -S orderflow -G orderflow
WORKDIR /app

COPY --from=build /workspace/target/orderflow-*.jar app.jar

USER orderflow
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q UP || exit 1

ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/urandom", \
  "-jar", "app.jar"]
