FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
RUN mkdir -p /app/data
ENV GENDER_REVEAL_DB_PATH=/app/data/gender-reveal.db
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
