# Use an official Eclipse Temurin JRE runtime as a parent image
FROM eclipse-temurin:21-jre-jammy

# Arguments for user and group
ARG UID=10001
ARG GID=10001

# Add a non-root user and group
RUN groupadd -g ${GID} appgroup &&     useradd -u ${UID} -g appgroup -m appuser

# curl backs the HEALTHCHECK below - the base JRE image has neither curl nor
# wget. Installed before COPY so this layer stays cached across ordinary
# rebuilds (the jar below changes on nearly every build).
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Set the working directory in the container
WORKDIR /app

# Copy the build artifacts (the JAR file)
# Assuming the JAR is built in build/libs/ and its name might vary.
# We'll copy all JARs and expect the entrypoint to specify the correct one or for there to be only one.
COPY build/libs/*.jar app.jar

# Make port 8080 available to the world outside this container
EXPOSE 8080

# Polls the unauthenticated /actuator/health endpoint (see WebSecurityConfig
# and application.properties). start-period is generous since this app is
# deployed to a Raspberry Pi in production (see README's "Health check"
# section), well below the dev machine's ~4s cold start.
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# Change to the non-root user
USER appuser

# Run the JAR file
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
