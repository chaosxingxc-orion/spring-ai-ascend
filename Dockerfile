# spring-ai-ascend Dockerfile.
#
# Per docs/cross-cutting/supply-chain-controls.md: distroless runtime + digest pin.
# The :nonroot tag below should be replaced with a sha256 digest in CI before
# release; see ops/runbooks/digest-pin.md (W2+).
#
# Build stage uses the official Maven image (Java 21 + Maven 3.9). Runtime
# stage is distroless Java 21.
#
# agent-runtime ships as a LIBRARY (consumers embed app.RuntimeApp); it has no
# executable jar. The runnable artifact is the openJiuwen A2A example server
# (examples/agent-runtime-a2a-llm-e2e), which this image builds and runs.
# Reactor = 4 modules: spring-ai-ascend-dependencies (BoM), agent-bus,
# agent-runtime, agent-service.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY spring-ai-ascend-dependencies/pom.xml ./spring-ai-ascend-dependencies/
COPY agent-bus/pom.xml ./agent-bus/
COPY agent-runtime/pom.xml ./agent-runtime/
COPY agent-service/pom.xml ./agent-service/
COPY examples/agent-runtime-a2a-llm-e2e/pom.xml ./examples/agent-runtime-a2a-llm-e2e/
COPY spring-ai-ascend-dependencies/ ./spring-ai-ascend-dependencies/
COPY agent-bus/src ./agent-bus/src
COPY agent-runtime/src ./agent-runtime/src
COPY agent-service/src ./agent-service/src
COPY examples/agent-runtime-a2a-llm-e2e/src ./examples/agent-runtime-a2a-llm-e2e/src
# Install the reactor (library) to the local repo, then build the example server
# into an executable Spring Boot fat jar.
RUN mvn -B -ntp -pl agent-runtime -am install -DskipTests \
 && mvn -B -ntp -f examples/agent-runtime-a2a-llm-e2e/pom.xml package -DskipTests

FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=build /workspace/examples/agent-runtime-a2a-llm-e2e/target/agent-runtime-a2a-llm-e2e-example-*.jar /app/app.jar

ENV APP_POSTURE=dev
ENV APP_SHA=dev

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
