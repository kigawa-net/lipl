FROM gradle:9.7-jdk21-alpine AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradle.properties /app/
COPY gradle /app/gradle
RUN gradle dependencies --no-daemon || true
COPY src /app/src
RUN gradle shadowJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar /app/lipl-api.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/lipl-api.jar"]
