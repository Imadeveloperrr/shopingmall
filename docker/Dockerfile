# ---------- 1단계: JAR 빌드 ----------
FROM gradle:8.4-jdk17 AS builder
WORKDIR /home/app
COPY . .
RUN gradle bootJar --no-daemon -x test

# ---------- 2단계: 경량 런타임 ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /home/app/build/libs/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
