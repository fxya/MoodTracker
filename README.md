# MoodTracker

A personal mood-tracking web app. Log a mood with a 1-10 rating and notes, and
MoodTracker automatically saves the local weather (temperature and
precipitation) alongside it, so you can look back and see whether the two
tend to line up. Built with Spring Boot and secured with Spring Security -
every user only ever sees their own data.

## Features

**Mood logging**
- Record a mood with a 1-10 rating and free-text notes.
- Edit or delete any past entry.
- Search mood history by text (mood or notes) and filter by rating range,
  with pagination.
- A weekly summary card shows your average rating for the past 7 days
  and how it compares to the week before.

**Weather**
- Set a location under Settings, and current weather at that location is
  looked up and saved automatically with every mood you log, via
  [Open-Meteo](https://open-meteo.com/) (free, no API key required).
- Weather is shown inline on each mood entry.
- If weather lookup fails or no location is set, the mood still saves -
  weather is a bonus, never a blocker.

**Trends** (on the `/moods` page)
- Mood rating over time.
- Temperature and precipitation over time.
- Average mood on dry days vs. rainy days.

**Account**
- Register and log in; all data is scoped to your own account.
- Change your password or delete your account (and everything in it)
  from Settings.

## Technology

- Java 21
- Spring Boot 4.1 (Spring Framework 7, Spring Security 7, Jackson 3)
- Spring Data JPA / Hibernate
- Thymeleaf
- PostgreSQL
- Chart.js (vendored locally, no CDN dependency)
- Open-Meteo for weather data
- Gradle
- Docker

## Running locally

**Prerequisites:** Java 21 and a running PostgreSQL instance. The included
`./gradlew` wrapper downloads a matching Gradle distribution automatically,
so a separate Gradle install isn't required.

1. Clone the repository and `cd` into it.
2. Create the database:
   ```bash
   createdb moodtracker
   ```
   (or `psql -c "CREATE DATABASE moodtracker;"`)
3. Check the datasource settings in `src/main/resources/application.properties`
   match your local PostgreSQL setup:
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/moodtracker
   spring.datasource.username=postgres
   spring.datasource.password=your_postgres_password
   ```
   The committed values are placeholders for local development only - update
   them to match your own PostgreSQL user, or override them with environment
   variables, before running anywhere beyond your own machine. Tables are
   created automatically on first run via `schema.sql`, and Hibernate adds
   any new columns on top of that as the app evolves (`ddl-auto=update`).
4. Run the app:
   ```bash
   ./gradlew bootRun
   ```
5. Open `http://localhost:8080`, register an account, and log in. Set a
   location under Settings to start recording weather with your moods.

On first startup, a demo account (`testuser` / `password`) is also created
automatically if it doesn't already exist - handy for quickly poking around
without registering, but not something to rely on outside local development.

## Running tests

```bash
./gradlew test
```

The test suite runs against a real local PostgreSQL instance - make sure one
is running and configured as described above before running tests.
`WeatherServiceTest` mocks the HTTP calls to Open-Meteo, so no network access
is needed for that part.

## Docker

1. Build the application JAR:
   ```bash
   ./gradlew clean build
   ```
2. Build the image:
   ```bash
   docker build -t moodtracker-app .
   ```
3. Run the container:
   ```bash
   docker run -p 8080:8080 --name moodtracker moodtracker-app
   ```

The container needs network access to a PostgreSQL database matching the
`spring.datasource.*` settings (create one separately, or point it at a
reachable existing instance), and outbound HTTPS access to Open-Meteo for
weather lookups. No API key is required for Open-Meteo.

To stop and remove the container:
```bash
docker stop moodtracker
docker rm moodtracker
```
