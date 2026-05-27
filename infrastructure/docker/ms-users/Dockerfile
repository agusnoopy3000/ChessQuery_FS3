# Multi-stage build: ms-users (puerto 8081)
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build
RUN apk add --no-cache maven
COPY ms-users/pom.xml .
RUN mvn dependency:go-offline -B -q
COPY ms-users/src ./src
RUN mvn clean package -DskipTests -B -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S chessquery && adduser -S chessquery -G chessquery
USER chessquery
ENV JAVA_OPTS="-Xmx384m -Xms128m"
COPY --from=builder /build/target/ms-users-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
