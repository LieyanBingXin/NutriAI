# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache tzdata curl \
    && addgroup -S nutriai \
    && adduser -S -G nutriai -h /app nutriai \
    && mkdir -p /app/logs \
    && chown -R nutriai:nutriai /app
WORKDIR /app
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"
COPY --from=build --chown=nutriai:nutriai /build/target/nutri-ai-0.1.0.jar /app/app.jar
USER nutriai
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8080/api/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
