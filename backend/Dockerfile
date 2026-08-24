FROM gradle:9.7-jdk21-alpine AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradle.properties /app/
COPY gradle /app/gradle
RUN gradle dependencies --no-daemon || true
COPY src /app/src
RUN gradle shadowJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S lipl && adduser -S lipl -G lipl
WORKDIR /app
COPY --from=build --chown=lipl:lipl /app/build/libs/*-all.jar /app/lipl-api.jar
USER lipl
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/lipl-api.jar"]
