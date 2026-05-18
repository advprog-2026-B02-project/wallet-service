FROM gradle:8.12.1-jdk21-alpine AS builder
WORKDIR /home/gradle/project

COPY --chown=gradle:gradle build.gradle.kts settings.gradle.kts ./
COPY --chown=gradle:gradle src src

RUN gradle --no-daemon clean bootJar -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S bidmart && adduser -S bidmart -G bidmart

COPY --from=builder /home/gradle/project/build/libs/*SNAPSHOT.jar app.jar

USER bidmart
EXPOSE 8084

ENTRYPOINT ["java", "-jar", "app.jar"]
