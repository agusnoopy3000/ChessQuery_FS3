# ${artifactId}

Microservicio ChessQuery generado desde `chessquery-ms-archetype`.

## Build local

```bash
mvn clean package
java -jar target/${artifactId}-${version}.jar
```

## Docker

```bash
docker build -t ${artifactId}:latest .
docker run --rm -p ${servicePort}:${servicePort} ${artifactId}:latest
```

## Endpoints baseline

- `GET /actuator/health`
- `GET /swagger-ui.html`
- `GET /api-docs`

## Configuración

Variables de entorno relevantes (ver `src/main/resources/application.yml`):

| Var | Default |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:${dbPort}/${dbName}` |
| `SPRING_RABBITMQ_HOST` | `localhost` |
| `SERVER_PORT` | `${servicePort}` |
