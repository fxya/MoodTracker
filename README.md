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
2.  **Configure WeatherAPI Key:** You need to provide your WeatherAPI key for the application to fetch weather data. You can do this in one of two ways:
    *   **Environment Variable (Recommended):** Set the `WEATHER_API_KEY` environment variable to your API key.
        ```bash
        export WEATHER_API_KEY="your_actual_api_key"
        ```
        If you use this method, it will take precedence. The application will first check for this environment variable.
    *   **`.apikey` File:** Create a file named `.apikey` in the root directory of the project and place your WeatherAPI key in it (just the key, nothing else).
        Example content for `.apikey`:
        ```
        your_actual_api_key
        ```
        This method will be used if the `WEATHER_API_KEY` environment variable is not set.
3.  **Ensure Java 17 and Gradle are installed.**
4.  **Set up PostgreSQL:** Ensure you have PostgreSQL running. Configure the database connection in `src/main/resources/application.properties` if your setup differs from the default:
    ```properties
    spring.datasource.url=jdbc:postgresql://localhost:5432/moodtracker
    spring.datasource.username=youruser
    spring.datasource.password=yourpassword
    ```
    You might need to create the `moodtracker` database if it doesn't exist.
5.  **Run the application:**
    ```bash
    ./gradlew bootRun
    ```
6.  **Access the application:** Open your web browser and go to `http://localhost:8080`.

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
    To run the container, you should provide the WeatherAPI key via an environment variable.
    ```bash
    docker run -e WEATHER_API_KEY="your_actual_api_key" -p 8080:8080 --name moodtracker moodtracker-app
    ```
    - `-e WEATHER_API_KEY="your_actual_api_key"`: Sets the `WEATHER_API_KEY` environment variable inside the container. Replace `"your_actual_api_key"` with your actual WeatherAPI key.
    - `-p 8080:8080`: Maps port 8080 of the container to port 8080 on your host.
    - `--name moodtracker`: Assigns a name to your running container for easier management.
    - `moodtracker-app`: The name of the image to use.

    *Note on API Key*: It is recommended to provide the API key using the `WEATHER_API_KEY` environment variable as shown above. This is more secure and flexible for production environments. If this environment variable is not set, the application will attempt to fall back to using the `.apikey` file if it was included in the Docker image (the Dockerfile is set up to copy this file if present in the project root during the image build).

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
