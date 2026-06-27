# workmate-api image — uses a pre-built Spring Boot jar (same pattern as member-runtimes).
# Build the jar first, then build this image from the examples/workmate directory:
#   make build                  # or: cd workmate-api && ../../../mvnw -DskipTests package
#   docker build -f workmate-api/Dockerfile -t workmate-api .
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ARG JAR_FILE=workmate-api/target/workmate-api-*.jar
COPY ${JAR_FILE} app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
