# member-runtimes

Optional per-member **agent-runtime** template. Each team member can run as its own service that
`workmate-api` calls over A2A, instead of running in-process.

## workmate-member-a2a

A standalone Spring Boot service + OpenJiuwen handler + A2A Agent Card, invoked by the
`workmate-api` team orchestrator.

### Run locally

```bash
cd spring-ai-ascend/examples/workmate
../../../mvnw -pl member-runtimes/workmate-member-a2a -am package -DskipTests

WORKMATE_MEMBER_EXPERT_ID=prd-writer SERVER_PORT=8081 \
  WORKMATE_OFFICE_ROOT=./office \
  java -jar member-runtimes/workmate-member-a2a/target/workmate-member-a2a-*.jar
```

### With Compose

```bash
../../../mvnw -pl member-runtimes/workmate-member-a2a -am package -DskipTests
docker compose -f docker/docker-compose.yml --profile members up -d
```

Enable outbound A2A from `workmate-api`:

```bash
export WORKMATE_MEMBER_RUNTIMES_ENABLED=true
```

Default mapping:

| expertId | URL |
|----------|-----|
| `prd-writer` | http://localhost:8081 |
| `fund-analyst` | http://localhost:8082 |
