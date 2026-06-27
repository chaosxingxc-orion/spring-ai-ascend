# W21 member-runtime image (build from examples/workmate root)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ARG JAR_FILE=member-runtimes/workmate-member-a2a/target/workmate-member-a2a-*.jar
COPY ${JAR_FILE} app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
