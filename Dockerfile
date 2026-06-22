# spring-ai-ascend agent-runtime Dockerfile.
#
# Per docs/cross-cutting/supply-chain-controls.md: distroless runtime + digest pin.
# The :nonroot tag below should be replaced with a sha256 digest in CI before
# release; see ops/runbooks/digest-pin.md (W2+).
#
# Build stage uses the official Maven image (Java 21 + Maven 3.9). Runtime
# stage is distroless Java 21.
#
# rc12 K-delta + rc13 ADR-0088: rewritten from the pre-Phase-C agent-platform Dockerfile
# (Phase C consolidated agent-platform into agent-runtime per ADR-0078; ADR-0079
# briefly added agent-runtime-core for shared kernel SPI; rc13 dissolved
# agent-runtime-core per ADR-0088 and redistributed its sources to agent-runtime /
# agent-core / agent-bus).

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY spring-ai-ascend-dependencies/pom.xml ./spring-ai-ascend-dependencies/
COPY agent-core/pom.xml ./agent-core/
COPY agent-middleware/pom.xml ./agent-middleware/
COPY agent-bus/pom.xml ./agent-bus/
COPY agent-client/pom.xml ./agent-client/
COPY agent-evolve/pom.xml ./agent-evolve/
COPY agent-runtime/pom.xml ./agent-runtime/
# Pre-fetch deps to leverage Docker layer cache.
RUN mvn -B -ntp -pl agent-runtime -am dependency:go-offline -DskipTests
COPY spring-ai-ascend-dependencies/ ./spring-ai-ascend-dependencies/
COPY agent-core/src ./agent-core/src
COPY agent-middleware/src ./agent-middleware/src
COPY agent-bus/src ./agent-bus/src
COPY agent-client/src ./agent-client/src
COPY agent-evolve/src ./agent-evolve/src
COPY agent-runtime/src ./agent-runtime/src
RUN mvn -B -ntp -pl agent-runtime -am package -DskipTests

FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
# agent-runtime currently ships as a plain library. The image keeps the built
# artifact available for downstream embedding images; it does not assume a
# Spring Boot main class or boot repackage.
COPY --from=build /workspace/agent-runtime/target/agent-runtime-*.jar /app/agent-runtime.jar

ENV APP_POSTURE=dev
ENV APP_SHA=dev

EXPOSE 8080

CMD ["java", "-version"]

