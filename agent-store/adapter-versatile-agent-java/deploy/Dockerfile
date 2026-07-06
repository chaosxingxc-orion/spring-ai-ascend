# adapter-versatile-agent-java 运行时镜像
#
# 构建前须先在宿主机完成 Maven 打包（在仓库根目录）：
#   ./mvnw clean install -DskipTests
#   ./mvnw -f agent-store/adapter-versatile-agent-java/pom.xml package -DskipTests
#
# 构建命令（在仓库根目录）：
#   docker build -t adapter-versatile-agent-java:latest \
#     -f agent-store/adapter-versatile-agent-java/deploy/Dockerfile \
#     agent-store/adapter-versatile-agent-java

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY target/adapter-versatile-agent-java-*.jar app.jar

ENV VERSATILE_AGENT_PORT=8191
EXPOSE 8191

HEALTHCHECK --interval=10s --timeout=5s --retries=8 --start-period=90s \
  CMD curl -sf http://localhost:8191/.well-known/agent-card.json || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
