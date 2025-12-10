# Multi-stage build for Spring Boot Kotlin application

# =======================
# Stage 1: Build
# =======================
FROM gradle:8.11.1-jdk21 AS build

WORKDIR /workspace

# 1. Gradle 관련 파일 먼저 복사 (캐시 최대 활용)
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle* settings.gradle* ./

# gradlew 실행권한 부여
RUN chmod +x gradlew

# 2. 소스 코드 전체 복사
COPY src ./src

# 3. 애플리케이션 빌드
#   - bootJar만 빌드 (스프링 부트 실행용 JAR)
RUN ./gradlew clean bootWar -x test --no-daemon

# =======================
# Stage 2: Runtime
# =======================
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# 1. 기본 환경
ENV TZ=Asia/Seoul \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# 2. non-root 유저 생성
RUN groupadd -r spring && useradd -r -g spring spring

# 빌드된 WAR 복사 (하나만 있다고 가정)
COPY --from=build /workspace/build/libs/*.war app.war

# 디버깅 + 권한 변경
RUN ls -al && chown spring:spring app.war

USER spring

EXPOSE 8080

# 5. 애플리케이션 실행
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.war"]
