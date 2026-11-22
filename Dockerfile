# Stage 1: Build
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY build.gradle settings.gradle gradlew ./
COPY gradle gradle
COPY src src
RUN chmod +x gradlew
RUN ./gradlew bootJar --no-daemon --warning-mode all

# Stage 2: Run
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN groupadd -r appuser && useradd -r -g appuser -m appuser
USER appuser

COPY --from=build /app/build/libs/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
