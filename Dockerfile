# spring-ai-ascend agent-service Dockerfile.
#
# Per docs/cross-cutting/supply-chain-controls.md: distroless runtime + digest pin.
# The :nonroot tag below should be replaced with a sha256 digest in CI before
# release; see ops/runbooks/digest-pin.md (W2+).
#
# Build stage uses the official Maven image (Java 21 + Maven 3.9). Runtime
# stage is distroless Java 21.
#
# rc12 K-δ: rewritten from the pre-Phase-C agent-platform Dockerfile (post-ADR-0078
# consolidation; agent-platform was consolidated into agent-service and agent-runtime
# was split into agent-runtime-core + agent-execution-engine).

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY spring-ai-ascend-dependencies/pom.xml ./spring-ai-ascend-dependencies/
COPY agent-runtime-core/pom.xml ./agent-runtime-core/
COPY agent-execution-engine/pom.xml ./agent-execution-engine/
COPY agent-middleware/pom.xml ./agent-middleware/
COPY agent-bus/pom.xml ./agent-bus/
COPY agent-client/pom.xml ./agent-client/
COPY agent-evolve/pom.xml ./agent-evolve/
COPY agent-service/pom.xml ./agent-service/
COPY spring-ai-ascend-graphmemory-starter/pom.xml ./spring-ai-ascend-graphmemory-starter/
# Pre-fetch deps to leverage Docker layer cache.
RUN mvn -B -ntp -pl agent-service -am dependency:go-offline -DskipTests
COPY spring-ai-ascend-dependencies/ ./spring-ai-ascend-dependencies/
COPY agent-runtime-core/src ./agent-runtime-core/src
COPY agent-execution-engine/src ./agent-execution-engine/src
COPY agent-middleware/src ./agent-middleware/src
COPY agent-bus/src ./agent-bus/src
COPY agent-client/src ./agent-client/src
COPY agent-evolve/src ./agent-evolve/src
COPY agent-service/src ./agent-service/src
COPY spring-ai-ascend-graphmemory-starter/src ./spring-ai-ascend-graphmemory-starter/src
RUN mvn -B -ntp -pl agent-service -am package -DskipTests

FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=build /workspace/agent-service/target/agent-service-*.jar /app/app.jar

ENV APP_POSTURE=dev
ENV APP_SHA=dev

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
