# MoodTracker

Basic mood tracker application allowing user to record moods. 
Saves data from external weather API to correlate weather with mood. 
Secured with Spring Security.

## Features

- Record daily moods with a rating.
- View mood history.
- Correlate moods with weather data from an external API.
- Secure application with Spring Security.

## Technologies Used

- Java
- Spring Boot
- Spring Security
- Spring Data JPA
- Thymeleaf
- PostgreSQL
- WeatherAPI (for weather data)
- Docker
- Gradle

## Setup and Run Locally

1. Clone the repository.
2. Create a file named `.apikey` in the root directory and add your WeatherAPI key to it.
3. Ensure you have Java 17 and Gradle installed.
4. Ensure you have PostgreSQL running and configure the connection in `src/main/resources/application.properties` if it's not using the default (e.g., `spring.datasource.url=jdbc:postgresql://localhost:5432/moodtracker`, `spring.datasource.username=youruser`, `spring.datasource.password=yourpassword`).
5. Run the application using `./gradlew bootRun`.
6. Access the application at `http://localhost:8080`.

## Docker

This application can be built and run using Docker.

**Prerequisites:**
- Docker installed on your system.
- An API key for WeatherAPI. Create a file named `.apikey` in the root of this project and place your API key in it. This file will be copied into the Docker image.

**Build the Docker Image:**

1.  **Build the application JAR:**
    Before building the Docker image, you need to create the application JAR file using Gradle:
    ```bash
    ./gradlew build
    ```
    This command will generate the JAR file in the `build/libs/` directory. Make sure to exclude tests by using `build` instead of `bootJar` if `bootJar` includes extra devtools. Or, more simply, `./gradlew clean build` is common. The Dockerfile copies `build/libs/*.jar`, so any fat JAR there will work.

2.  **Build the Docker image:**
    Navigate to the project's root directory (where the `Dockerfile` is located) and run:
    ```bash
    docker build -t moodtracker-app .
    ```
    This will build a Docker image tagged as `moodtracker-app`.

**Run the Docker Container:**

1.  **Run the container:**
    ```bash
    docker run -p 8080:8080 --name moodtracker moodtracker-app
    ```
    - `-p 8080:8080`: Maps port 8080 of the container to port 8080 on your host.
    - `--name moodtracker`: Assigns a name to your running container for easier management.
    - `moodtracker-app`: The name of the image to use.

    *Note on API Key*: The Dockerfile copies the `.apikey` file from your project root into the image. This is convenient for development. For more secure production deployments, consider using Docker secrets or environment variables to handle the API key instead of baking it into the image.

2.  **Access the application:**
    Once the container is running, you can access the application at `http://localhost:8080` in your web browser.

**Stopping and Removing the Container:**

-   To stop the container:
    ```bash
    docker stop moodtracker
    ```
-   To remove the container (after stopping it):
    ```bash
    docker rm moodtracker
    ```
