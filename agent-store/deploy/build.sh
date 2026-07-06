#!/usr/bin/env bash
# agent-store 一键 Maven 打包 + Docker 镜像构建（Linux / WSL）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

cd "${REPO_ROOT}"

echo "==> [1/5] 安装父工程依赖"
./mvnw clean install -DskipTests

echo "==> [2/5] 打包 edp-agent-java"
./mvnw -pl agent-store/edp-agent-java -am package -DskipTests

echo "==> [3/5] 打包 adapter-versatile-agent-java"
./mvnw -f agent-store/adapter-versatile-agent-java/pom.xml package -DskipTests

echo "==> [4/5] 构建 adapter 镜像"
docker build -t adapter-versatile-agent-java:latest \
  -f agent-store/adapter-versatile-agent-java/deploy/Dockerfile \
  agent-store/adapter-versatile-agent-java

echo "==> [5/5] 构建 edp-agent 镜像"
docker build -t edp-agent-java:latest \
  -f agent-store/edp-agent-java/deploy/Dockerfile \
  agent-store/edp-agent-java

echo "Done. 下一步："
echo "  cd agent-store/deploy && cp .env.example .env && vi .env"
echo "  docker compose up -d"
