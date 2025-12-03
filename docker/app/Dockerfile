# Multi-stage build for Spring Boot Kotlin application

# =======================
# Stage 1: Build
# =======================
FROM gradle:8.11.1-jdk21 AS build

WORKDIR /workspace

# 1. Gradle 관련 파일 먼저 복사 (캐시 최대 활용)
#   - build.gradle(.kts), settings.gradle(.kts) 둘 다 커버
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle* settings.gradle* ./

# gradlew 실행권한 부여
RUN chmod +x gradlew

# 2. 의존성만 먼저 받기 (소스 변경과 분리 → 캐시 효율↑)
RUN ./gradlew dependencies --no-daemon

# 3. 소스 코드 복사
COPY src ./src

# 4. 애플리케이션 빌드
RUN ./gradlew bootJar --no-daemon

# =======================
# Stage 2: Runtime
# =======================
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# 1. 기본 환경 (타임존 등) – 필요 없으면 제거해도 됨
ENV TZ=Asia/Seoul \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# 2. non-root 유저 생성
RUN groupadd -r spring && useradd -r -g spring spring

# 3. 빌드 결과 JAR 복사
#   - 빌드 결과가 여러 개인 경우를 대비해서 * 하나만 두고, 실제 패턴에 맞게 조정 가능
COPY --from=build /workspace/build/libs/*.jar app.jar

# 4. 권한 변경
RUN chown spring:spring app.jar

USER spring

EXPOSE 8080

# 5. 엔트리포인트
#   - JAVA_OPTS 외부에서 덮어쓰기 쉽게 sh -c 패턴 사용
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
