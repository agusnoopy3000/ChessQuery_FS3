# chessquery-ms-archetype

Arquetipo Maven para generar microservicios alineados con el stack de **ChessQuery**.

Stack incluido:
- Spring Boot **3.2.4** (Java 17)
- Spring Data JPA + PostgreSQL + Flyway
- Spring AMQP (RabbitMQ topic exchange `ChessEvents`)
- Spring Cloud Circuit Breaker (Resilience4j)
- Springdoc OpenAPI 2.3.0 (Swagger UI)
- Lombok 1.18.30
- JaCoCo (cobertura)
- Dockerfile multi-stage (builder Maven → runtime JRE)

## Instalación local del arquetipo

```bash
cd archetypes/chessquery-ms-archetype
mvn clean install
```

Esto registra el arquetipo en el repositorio Maven local (`~/.m2`).

## Generar un nuevo microservicio

```bash
mvn archetype:generate \
  -DarchetypeGroupId=cl.chessquery.archetypes \
  -DarchetypeArtifactId=chessquery-ms-archetype \
  -DarchetypeVersion=1.0.0 \
  -DgroupId=cl.chessquery \
  -DartifactId=ms-rating \
  -Dversion=0.0.1-SNAPSHOT \
  -Dpackage=cl.chessquery.rating \
  -DserviceName=rating \
  -DservicePort=8087 \
  -DdbName=rating_db \
  -DdbPort=5439 \
  -DinteractiveMode=false
```

Genera un proyecto listo para `mvn spring-boot:run` o `docker build`.

## Propiedades del arquetipo

| Propiedad | Default | Uso |
|---|---|---|
| `serviceName` | `example` | Nombre lógico del servicio |
| `servicePort` | `8090` | Puerto HTTP |
| `dbName` | `example_db` | Schema PostgreSQL |
| `dbPort` | `5440` | Puerto PostgreSQL en docker-compose |

## Estructura generada

```
ms-<name>/
├── pom.xml                              (Spring Boot parent + deps)
├── Dockerfile                           (multi-stage)
├── README.md
└── src/
    ├── main/
    │   ├── java/<package>/Application.java
    │   └── resources/
    │       ├── application.yml          (datasource + rabbit + r4j)
    │       └── db/migration/V1__init.sql
    └── test/
        └── java/<package>/ApplicationTests.java
```

## Justificación arquitectónica

Ver `docs/ANALISIS_PATRONES.md` para la justificación completa del uso
de este arquetipo y los patrones que estandariza en todos los microservicios.
