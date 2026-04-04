FROM gradle:8.14-jdk21 AS builder

WORKDIR /app

COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY gradle.properties settings.gradle.kts build.gradle.kts ./

COPY domain/build.gradle.kts domain/
COPY server/build.gradle.kts server/

RUN gradle dependencies --no-daemon || true

COPY domain/src domain/src
COPY server/src server/src

RUN gradle :server:buildFatJar --no-daemon

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

COPY --from=builder /app/server/build/libs/*-all.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
