# MoodTracker

[![CI](https://github.com/fxya/MoodTracker/actions/workflows/ci.yml/badge.svg)](https://github.com/fxya/MoodTracker/actions/workflows/ci.yml)

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
- Geocoded coordinates for a location are cached for a week, so logging
  moods doesn't re-geocode the same place every time.
- Backfill weather for moods logged before you set a location, from
  Settings - it looks up each missing day's historical conditions for
  your current location.

**Trends** (on the `/moods` page)
- Mood rating over time.
- Temperature and precipitation over time.
- Average mood on dry days vs. rainy days.

**Export**
- Download your full mood history as CSV or JSON from the mood tracker
  page.

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
- Tailwind CSS v4 + Vite (compiles `frontend/` into `src/main/resources/static`,
  wired into the Gradle build - see "Frontend build" below)
- Chart.js (an npm dependency bundled by Vite, not a CDN script)
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

### Frontend build

`src/main/resources/static/css/style.css` and `.../static/js/moods.js` are
**generated** - don't hand-edit them, edit their source under `frontend/`
instead (`frontend/css/app.css`, `frontend/js/`). The
[com.github.node-gradle.node](https://github.com/node-gradle/gradle-node-plugin)
plugin runs `npm run build` (Vite, with Tailwind CSS v4 via
`@tailwindcss/vite`) automatically before `processResources`, so
`./gradlew bootRun`/`build`/`test` always compile fresh frontend output with
no separate manual step - and no host Node install, since the plugin
downloads a pinned Node version into `.gradle/nodejs` the first time it's
needed. For iterating on frontend code alongside `bootRun`, `npm run dev`
(`vite build --watch`) pairs with Spring Boot DevTools' existing LiveReload,
which already watches `static/**` for changes.

## Running tests

```bash
./gradlew test
```

The test suite runs against a real local PostgreSQL instance - make sure one
is running and configured as described above before running tests.
`WeatherServiceTest` mocks the HTTP calls to Open-Meteo, so no network access
is needed for that part.

The pure JS logic behind the `/moods` charts (`frontend/js/mood-analysis.js`:
series building, dry/rainy bucketing) has its own small Vitest suite:
```bash
npm install
npm test
```

### End-to-end tests

The `e2e/` suite drives a real Chromium browser against a real running app
(via [Playwright](https://playwright.dev/)), covering the actual user
journeys: register/login/logout, adding/editing/deleting/searching moods,
CSV/JSON export, and the settings flows (location/time zone, password
change, account deletion). It manages the app process itself
(`playwright.config.js`'s `webServer` runs `./gradlew bootRun` and waits for
it to be ready), so you only need Postgres already running, same as
`./gradlew test`:
```bash
npm install
npx playwright install --with-deps chromium   # first time only
npm run test:e2e
```
Weather/backfill is deliberately **not** covered here - those flows call a
real third-party API (Open-Meteo), and relying on it from e2e tests would
make the suite flaky and non-deterministic. That logic is already covered by
`WeatherServiceTest`/`SettingsControllerTest` with a mocked `WebClient`; e2e
tests simply never set a location, which is itself a real (if implicit)
check of the app's designed-in "weather is optional" degradation path.

Several tests in `e2e/mood-crud.spec.js` share one logged-in session across
tests (`test.describe.configure({ mode: 'serial' })`) rather than
registering/logging in fresh for every single test - a full suite doing that
would run into the login rate limit described above, since it's a real
per-IP limit and all e2e traffic comes from the same machine.

## Code style

Java follows the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html),
enforced via [Spotless](https://github.com/diffplug/spotless) with
[google-java-format](https://github.com/google/google-java-format):
```bash
./gradlew spotlessCheck   # fails the build on a style violation (also runs as part of `check`)
./gradlew spotlessApply   # reformats in place
```

JS/CSS under `frontend/` are formatted with [Prettier](https://prettier.io/)
(config in `.prettierrc.json`; kept single-quote and 4-space to match the
existing code rather than Prettier's bare defaults):
```bash
npm run format:check
npm run format
```
Thymeleaf templates are intentionally not auto-formatted - a generic HTML
formatter risks mangling `th:*` attribute syntax.

## Docker

1. Build the application JAR (this now also runs the frontend build - see
   "Frontend build" above - so it needs npm-registry access and, on the
   first run, a one-time Node download in addition to Maven Central):
   ```bash
   ./gradlew clean build
   ```
2. Build the image (this step itself has no new network dependency - it
   just copies the already-built jar):
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
