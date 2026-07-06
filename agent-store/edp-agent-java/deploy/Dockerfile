# edp-agent-java 运行时镜像
#
# 构建前须先在宿主机完成 Maven 打包（在仓库根目录）：
#   ./mvnw clean install -DskipTests
#   ./mvnw -pl agent-store/edp-agent-java -am package -DskipTests
#
# 或一键：bash agent-store/deploy/build.sh
#
# 构建命令（在仓库根目录）：
#   docker build -t edp-agent-java:latest \
#     -f agent-store/edp-agent-java/deploy/Dockerfile \
#     agent-store/edp-agent-java

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY deploy/requirements-mcp.txt /tmp/requirements-mcp.txt
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl python3 python3-pip \
    && ln -sf /usr/bin/python3 /usr/bin/python \
    && pip3 install --no-cache-dir -r /tmp/requirements-mcp.txt \
    && rm -rf /var/lib/apt/lists/*

COPY engine/target/edp-agent-engine-*.jar app.jar
COPY engine/src/main/resources/governance/ /app/config/governance/
COPY deploy/config/edp-config.yaml /app/config/edp-config.yaml
COPY scenarios/wealth-demo/ /app/scenarios/wealth-demo/

ENV SERVER_PORT=8190
ENV EDPA_AGENT_CONFIG_PATH=/app/config/edp-config.yaml
ENV EDP_AGENT_SCENARIO_HOME=/app/scenarios/wealth-demo
ENV PYTHONIOENCODING=utf-8

EXPOSE 8190

HEALTHCHECK --interval=10s --timeout=5s --retries=5 --start-period=60s \
  CMD curl -sf http://localhost:8190/.well-known/agent-card.json || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
