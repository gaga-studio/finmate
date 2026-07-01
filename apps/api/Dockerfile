FROM eclipse-temurin:17-jdk AS build

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY apps/api/build.gradle apps/api/build.gradle

RUN ./gradlew :apps:api:dependencies --no-daemon

COPY apps/api apps/api

RUN ./gradlew :apps:api:bootJar --no-daemon

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /workspace/apps/api/build/libs/*.jar app.jar
COPY seed /app/seed

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
