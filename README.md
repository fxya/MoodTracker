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
- Optionally set your time zone in Settings so mood dates/times display in
  your own zone instead of the server's.
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
3. The datasource defaults in `src/main/resources/application.properties`
   assume PostgreSQL on `localhost:5432` with user `postgres`:
   ```properties
   spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/moodtracker}
   spring.datasource.username=${DB_USERNAME:postgres}
   spring.datasource.password=${DB_PASSWORD:your_postgres_password}
   ```
   If your local setup differs, either edit those defaults or set the
   `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` environment variables - the same
   variables are how you'd override the committed placeholder credentials
   before running anywhere beyond your own machine. Tables are created
   automatically on first run via `schema.sql`, and Hibernate adds any new
   columns on top of that as the app evolves (`ddl-auto=update`).
4. Run the app:
   ```bash
   ./gradlew bootRun
   ```
5. Open `http://localhost:8080`, register an account, and log in. Set a
   location under Settings to start recording weather with your moods.

### Profiles

The `dev` Spring profile is active by default (see `spring.profiles.active` in
`application.properties`), which is what makes step 4 work with no further
setup: on first startup it seeds a demo account (`testuser` / `password`) -
handy for quickly poking around without registering, but not something to
rely on outside local development. Running with any other profile active
(`SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun`, for example) skips that
seeding. A real deployment should set `SPRING_PROFILES_ACTIVE` to something
other than `dev`, in addition to overriding the database credentials above.

### Login/registration rate limiting

`POST /login` and `POST /register` are rate-limited per client IP (20
attempts per 5-minute window, in-memory, reset on restart) to blunt naive
automated credential-stuffing and account-enumeration attempts. It's a
single-instance, best-effort deterrent, not a replacement for a proper
WAF/gateway-level defense in a real deployment.

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
3. Run the container, pointing it at a reachable PostgreSQL instance:
   ```bash
   docker run -p 8080:8080 --name moodtracker \
     -e DB_URL=jdbc:postgresql://<host>:5432/moodtracker \
     -e DB_USERNAME=<username> \
     -e DB_PASSWORD=<password> \
     -e SPRING_PROFILES_ACTIVE=prod \
     moodtracker-app
   ```

The container needs network access to that PostgreSQL database (`localhost`
won't resolve to the host machine from inside the container - use the host's
address, a linked container, or a managed database instead), and outbound
HTTPS access to Open-Meteo for weather lookups. No API key is required for
Open-Meteo.

To stop and remove the container:
```bash
docker stop moodtracker
docker rm moodtracker
```
