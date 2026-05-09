FROM maven:3.9.9-eclipse-temurin-17 AS build

ARG SERVICE_MODULE

WORKDIR /workspace

COPY pom.xml ./pom.xml
COPY discovery-service/pom.xml discovery-service/pom.xml
COPY gateway-service/pom.xml gateway-service/pom.xml
COPY user-service/pom.xml user-service/pom.xml
COPY product-service/pom.xml product-service/pom.xml
COPY media-service/pom.xml media-service/pom.xml

RUN mvn -q -pl ${SERVICE_MODULE} -am dependency:go-offline

COPY . .

RUN set -eux; \
    mvn -q -pl "${SERVICE_MODULE}" -am package -DskipTests; \
    artifact="$(find "${SERVICE_MODULE}/target" -maxdepth 1 -type f -name '*.jar' ! -name 'original-*.jar' | head -n 1)"; \
    test -n "${artifact}"; \
    cp "${artifact}" /tmp/app.jar

FROM eclipse-temurin:17-jre-jammy AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /tmp/app.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
