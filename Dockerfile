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

# Create a volume for the .apikey file if it's not already part of the image
# This assumes the application will look for .apikey in the working directory (/app)
# Alternatively, the key can be passed as an environment variable (more secure for production)
# For now, we'll assume it needs to be present in /app
# RUN touch /app/.apikey && chown appuser:appgroup /app/.apikey

# Copy the .apikey file into the /app directory
COPY .apikey /app/.apikey

# Change to the non-root user
USER appuser

# Run the JAR file
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
