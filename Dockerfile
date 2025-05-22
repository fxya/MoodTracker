# Use an official OpenJDK runtime as a parent image
FROM openjdk:17-jdk-slim

# Arguments for user and group
ARG UID=10001
ARG GID=10001

# Add a non-root user and group
RUN groupadd -g ${GID} appgroup &&     useradd -u ${UID} -g appgroup -m appuser

# Set the working directory in the container
WORKDIR /app

# Copy the build artifacts (the JAR file)
# Assuming the JAR is built in build/libs/ and its name might vary.
# We'll copy all JARs and expect the entrypoint to specify the correct one or for there to be only one.
COPY build/libs/*.jar app.jar

# Make port 8080 available to the world outside this container
EXPOSE 8080

# Change to the non-root user
USER appuser

# Run the JAR file
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
