# Multi-stage build: ms-analytics (puerto 8084)
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build
RUN apk add --no-cache maven
COPY ms-analytics/pom.xml .
RUN mvn dependency:go-offline -B -q
COPY ms-analytics/src ./src
RUN mvn clean package -DskipTests -B -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S chessquery && adduser -S chessquery -G chessquery
USER chessquery
ENV JAVA_OPTS="-Xmx384m -Xms128m"
COPY --from=builder /build/target/ms-analytics-*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
