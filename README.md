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

1.  **Clone the repository:**
    ```bash
    git clone <repository_url>
    cd <repository_directory>
    ```
2.  **Configure WeatherAPI Key:**
    To fetch weather data, the application requires a WeatherAPI key.
    *   **Environment Variable (Recommended):** Set the `WEATHER_API_KEY` environment variable to your API key. This is the preferred method for both local development and Docker deployment.
        ```bash
        export WEATHER_API_KEY="your_actual_api_key"
        ```
        The application will always check for this environment variable first.
    *   **`.apikey` File (Alternative for Local Development):** For local development only, if the `WEATHER_API_KEY` environment variable is not set, the application can fall back to reading the key from a file named `.apikey` located in the project's root directory.
        Example content for `.apikey`:
        ```
        your_actual_api_key
        ```
        This method is not used for Docker deployments.
3.  **Ensure Java 17 and Gradle are installed.**
4.  **Set up PostgreSQL:** Ensure you have PostgreSQL running. Configure the database connection in `src/main/resources/application.properties` if your setup differs from the default:
    ```properties
    spring.datasource.url=jdbc:postgresql://localhost:5432/moodtracker
    spring.datasource.username=youruser
    spring.datasource.password=yourpassword
    ```
    You will likely need to create the `moodtracker` database manually if it doesn't already exist (e.g., `CREATE DATABASE moodtracker;`).
    The application will automatically create the necessary database tables on startup if they don't exist, thanks to the `schema.sql` script.
5.  **Run the application:**
    ```bash
    ./gradlew bootRun
    ```
6.  **Access the application:** Open your web browser and go to `http://localhost:8080`.

## Docker

This application can be built and run using Docker.

**Prerequisites:**
- Docker installed on your system.

**Build the Docker Image:**

1.  **Build the application JAR:**
    Before building the Docker image, you need to create the application JAR file using Gradle:
    ```bash
    ./gradlew clean build
    ```
    This command cleans previous builds and then generates the JAR file in the `build/libs/` directory. Make sure to exclude tests by using `build` instead of `bootJar` if `bootJar` includes extra devtools. Or, more simply, `./gradlew clean build` is common. The Dockerfile copies `build/libs/*.jar`, so any fat JAR there will work.

2.  **Build the Docker image:**
    Navigate to the project's root directory (where the `Dockerfile` is located) and run:
    ```bash
    docker build -t moodtracker-app .
    ```
    This will build a Docker image tagged as `moodtracker-app`.

**Run the Docker Container:**

1.  **Run the container:**
    To run the container, you should provide the WeatherAPI key via an environment variable.
    ```bash
    docker run -e WEATHER_API_KEY="your_actual_api_key" -p 8080:8080 --name moodtracker moodtracker-app
    ```
    - `-e WEATHER_API_KEY="your_actual_api_key"`: Sets the `WEATHER_API_KEY` environment variable inside the container. Replace `"your_actual_api_key"` with your actual WeatherAPI key.
    - `-p 8080:8080`: Maps port 8080 of the container to port 8080 on your host.
    - `--name moodtracker`: Assigns a name to your running container for easier management.
    - `moodtracker-app`: The name of the image to use.

    The `WEATHER_API_KEY` environment variable is the **sole method** for providing the API key to the Docker container, as the `.apikey` file is not copied into the image.

    Similar to local setup, the application within the Docker container will automatically create the necessary database tables (schema) in the `moodtracker` database upon startup if they don't already exist, based on the `src/main/resources/schema.sql` script. You must ensure that the PostgreSQL database (whether it's a separate Docker container or a cloud instance) is accessible to the application container and that the `moodtracker` database exists.

2.  **Access the application:**
    Once the container is running, you can access the application at `http://localhost:8080` in your web browser.

**Note on Database in Docker:** When running the Docker container, the application will attempt to connect to the PostgreSQL database specified in `application.properties` (which typically defaults to `jdbc:postgresql://localhost:5432/moodtracker` unless changed). Ensure your PostgreSQL server is accessible to the Docker container (e.g., it's not running on `localhost` *inside* another container, or your Docker networking is configured accordingly). The `schema.sql` script will automatically create the necessary tables in the `moodtracker` database if they don't already exist, provided the database itself exists and is accessible.

**Stopping and Removing the Container:**

-   To stop the container:
    ```bash
    docker stop moodtracker
    ```
-   To remove the container (after stopping it):
    ```bash
    docker rm moodtracker
    ```
