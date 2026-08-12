# MoodTracker

Basic mood tracker application allowing user to record moods. 
Saves the local weather alongside each mood to help correlate weather with mood. 
Secured with Spring Security.

## Features

- Register and log in as a user; moods are scoped to your own account.
- Record daily moods with a 1-10 rating and free-text notes.
- View your mood history, including notes.
- Set a location under **Settings**, and the weather (temperature and
  precipitation) at that location is automatically looked up and saved
  with every mood you log. Weather lookup uses [Open-Meteo](https://open-meteo.com/),
  which is free and requires no API key.
- View mood-rating, temperature, and precipitation trend charts on the
  `/moods` page.
- Secure application with Spring Security.

## Technologies Used

- Java 21
- Spring Boot 4.1 (Spring Framework 7, Spring Security 7, Jackson 3)
- Spring Data JPA
- Thymeleaf
- PostgreSQL
- Open-Meteo (for weather data - free, no API key required)
- Docker
- Gradle

## Setup and Run Locally

1.  **Clone the repository:**
    ```bash
    git clone <repository_url>
    cd <repository_directory>
    ```
2.  **Ensure Java 21 is installed.** The included `./gradlew` wrapper downloads
    a matching Gradle distribution automatically, so a separate Gradle install
    isn't required.
3.  **Set up PostgreSQL:** Ensure you have PostgreSQL running. Configure the database connection in `src/main/resources/application.properties` if your setup differs from the default:
    ```properties
    spring.datasource.url=jdbc:postgresql://localhost:5432/moodtracker
    spring.datasource.username=youruser
    spring.datasource.password=yourpassword
    ```
    You will likely need to create the `moodtracker` database manually if it doesn't already exist (e.g., `CREATE DATABASE moodtracker;`).
    The application will automatically create the necessary database tables on startup if they don't exist, thanks to the `schema.sql` script.
4.  **Run the application:**
    ```bash
    ./gradlew bootRun
    ```
5.  **Access the application:** Open your web browser and go to `http://localhost:8080`,
    register an account, then log in. Set a location under **Settings** to
    start recording weather with your moods - no API key or extra setup
    needed, since weather lookup uses [Open-Meteo](https://open-meteo.com/).

## Running Tests

```bash
./gradlew test
```

The test suite (controllers, `WeatherService`, and application context) runs
against a real local PostgreSQL instance — make sure one is running and
configured as described above before running tests. `WeatherServiceTest`
mocks the HTTP calls to Open-Meteo, so no network access or API key is
needed to run it.

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
    ```bash
    docker run -p 8080:8080 --name moodtracker moodtracker-app
    ```
    - `-p 8080:8080`: Maps port 8080 of the container to port 8080 on your host.
    - `--name moodtracker`: Assigns a name to your running container for easier management.
    - `moodtracker-app`: The name of the image to use.

    No API key or extra configuration is needed for weather lookups - Open-Meteo
    requires none.

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
