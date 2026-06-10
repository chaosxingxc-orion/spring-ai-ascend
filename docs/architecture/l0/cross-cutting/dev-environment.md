# Developer Environment Setup

Required toolchain for the spring-ai-ascend SDK (W0 baseline; 2026-05-10).

## Tier B toolchain

| Tool | Version | Install |
|---|---|---|
| Java (Eclipse Temurin JDK) | 21.x LTS | `winget install --id EclipseAdoptium.Temurin.21.JDK -e` (Windows) / `sdk install java 21-tem` (macOS/Linux) |
| Maven | 3.9.15 | Provided by Maven Wrapper (`./mvnw`). No system Maven required. |
| Docker Desktop | latest | [docs.docker.com/get-docker](https://docs.docker.com/get-docker/) |
| Git | 2.x+ | System package manager |
| gh CLI | latest | `winget install --id GitHub.cli` / `brew install gh` |

## Verify toolchain

```sh
java -version                # must report 21.x
./mvnw --version             # must report Apache Maven 3.9.15
docker info                  # must succeed
git --version
gh --version
```

## Build

```sh
# Resolve all dependencies to local cache:
./mvnw -B -ntp dependency:resolve

# Compile + test (excluding integration tests requiring Docker):
./mvnw -B -ntp compile test-compile test -Dsurefire.failIfNoSpecifiedTests=false

# Full verify (includes integration tests; requires Docker):
./mvnw -B -ntp verify
```

## Architecture gate

```sh
bash gate/check_architecture_sync.sh          # clean-tree gate (delivery evidence)
bash gate/check_architecture_sync.sh --local-only   # local-only gate (dirty-tree OK)
bash gate/test_architecture_sync_gate.sh      # self-test (4/4 checks must pass)
bash gate/check_spring_ai_milestone.sh        # fails after 2026-08-01 if AI still on -M
```

## Environment variables

| Variable | Purpose | Required for |
|---|---|---|
| `OPENAI_API_KEY` | LLM provider | Sidecar mem0, Graphiti |
| `ANTHROPIC_API_KEY` | LLM provider | agent-service (dev posture; post-Phase-C / ADR-0078 — pre-Phase-C this was the agent-runtime module) |
| `NEO4J_PASSWORD` | Graph DB password | sidecar-graphmemory.yml |
| `APP_POSTURE` | `dev` / `research` / `prod` | Runtime posture (default: `dev`) |
| `JAVA_HOME` | JDK path | Set if `./mvnw` cannot find Java 21 |

## Notes

- Maven Wrapper (`mvnw` / `mvnw.cmd`) pins Maven 3.9.15 via `.mvn/wrapper/maven-wrapper.properties`. Never install a different Maven version system-wide for this project.
- Spring AI 2.0.0-M5 artifacts are hosted at `https://repo.spring.io/milestone`. The parent POM declares this repository; no manual configuration needed.
- The Spring AI milestone gate (`gate/check_spring_ai_milestone.sh`) fails CI after 2026-08-01 if `spring-ai.version` still contains `-M`, forcing upgrade to the GA release.
