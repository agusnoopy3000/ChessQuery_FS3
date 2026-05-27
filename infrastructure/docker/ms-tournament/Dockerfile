# Multi-stage build: ms-tournament (puerto 8082)
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build
RUN apk add --no-cache maven
COPY ms-tournament/pom.xml .
RUN mvn dependency:go-offline -B -q
COPY ms-tournament/src ./src
RUN mvn clean package -DskipTests -B -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S chessquery && adduser -S chessquery -G chessquery
USER chessquery
ENV JAVA_OPTS="-Xmx384m -Xms128m"
COPY --from=builder /build/target/ms-tournament-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
