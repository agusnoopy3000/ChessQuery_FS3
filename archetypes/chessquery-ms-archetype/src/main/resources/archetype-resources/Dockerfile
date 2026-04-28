# ─── Stage 1: build ──────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# ─── Stage 2: runtime ────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system chessquery && useradd --system --gid chessquery --shell /bin/false chessquery
USER chessquery
COPY --from=builder /app/target/${artifactId}-*.jar app.jar
EXPOSE ${servicePort}
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","app.jar"]
