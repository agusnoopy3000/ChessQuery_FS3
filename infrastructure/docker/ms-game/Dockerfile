# Multi-stage build: ms-game (puerto 8083)
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build
RUN apk add --no-cache maven
COPY ms-game/pom.xml .
RUN mvn dependency:go-offline -B -q
COPY ms-game/src ./src
RUN mvn clean package -DskipTests -B -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S chessquery && adduser -S chessquery -G chessquery
USER chessquery
ENV JAVA_OPTS="-Xmx384m -Xms128m"
COPY --from=builder /build/target/ms-game-*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
