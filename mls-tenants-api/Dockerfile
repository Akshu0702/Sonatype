# Runtime image only. The jar is built by the pipeline (mvn package) before this runs,
# so the build needs no access to the private platform-security-commons artifact.
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY app.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
