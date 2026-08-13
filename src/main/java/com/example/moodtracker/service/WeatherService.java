package com.example.moodtracker.service;

import com.example.moodtracker.model.Weather;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Looks up current weather via Open-Meteo (https://open-meteo.com), which is free for
 * non-commercial use and requires no API key. A location name (e.g. "London") is first geocoded to
 * coordinates, then used to fetch current conditions.
 */
@Service
public class WeatherService {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  // A place name's coordinates are effectively permanent, so a long TTL is safe -
  // this exists purely to avoid re-geocoding the same location on every mood save,
  // not because the data goes stale. Bounded size guards against unbounded growth
  // from arbitrary user-entered location strings.
  private static final Duration GEOCODE_CACHE_TTL = Duration.ofDays(7);
  private static final long GEOCODE_CACHE_MAX_SIZE = 500;

  private final WebClient geocodingClient;
  private final WebClient forecastClient;
  private final WebClient archiveClient;
  private final Cache<String, GeocodingResult> geocodeCache;

  public WeatherService(WebClient.Builder webClientBuilder) {
    this.geocodingClient = webClientBuilder.baseUrl("https://geocoding-api.open-meteo.com").build();
    this.forecastClient = webClientBuilder.baseUrl("https://api.open-meteo.com").build();
    this.archiveClient = webClientBuilder.baseUrl("https://archive-api.open-meteo.com").build();
    this.geocodeCache =
        Caffeine.newBuilder()
            .maximumSize(GEOCODE_CACHE_MAX_SIZE)
            .expireAfterWrite(GEOCODE_CACHE_TTL)
            .build();
  }

  public Mono<Weather> fetchWeather(String location) {
    // Callers (e.g. MoodController) block on this from a servlet thread, so it
    // must never hang indefinitely if Open-Meteo is slow or unreachable.
    return geocode(location).flatMap(this::fetchCurrentConditions).timeout(TIMEOUT);
  }

  // For backfilling weather on moods logged before a location was set. Open-Meteo's
  // archive API only has day-level granularity (a daily mean/sum), unlike the
  // current-conditions snapshot fetchWeather uses - there's no way to retroactively
  // get "the exact conditions at 14:32" for a past date, so a daily aggregate is the
  // practical ceiling here, and it's still meaningful for the dry/rainy correlation.
  public Mono<Weather> fetchHistoricalWeather(String location, LocalDate date) {
    return geocode(location)
        .flatMap(coordinates -> fetchDailyConditions(coordinates, date))
        .timeout(TIMEOUT);
  }

  // Lets a caller fail fast on a bad location before doing repeated work (e.g.
  // backfilling many moods) rather than re-attempting - and re-timing-out on -
  // geocoding once per mood. A successful call warms the cache fetchHistoricalWeather
  // and fetchWeather both read from.
  public Mono<Void> validateLocation(String location) {
    return geocode(location).timeout(TIMEOUT).then();
  }

  private Mono<GeocodingResult> geocode(String location) {
    String cacheKey = location.trim().toLowerCase(Locale.ROOT);
    GeocodingResult cached = geocodeCache.getIfPresent(cacheKey);
    if (cached != null) {
      return Mono.just(cached);
    }

    return geocodingClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder
                    .path("/v1/search")
                    .queryParam("name", location)
                    .queryParam("count", 1)
                    .build())
        .retrieve()
        .bodyToMono(GeocodingResponse.class)
        .flatMap(
            response -> {
              List<GeocodingResult> results = response.results();
              if (results == null || results.isEmpty()) {
                return Mono.error(
                    new IllegalStateException("No location found for \"" + location + "\""));
              }
              // Only a successful lookup is cached - a bad/misspelled location isn't
              // memorized as a permanent failure, since it could be a transient issue
              // or the user might fix the spelling next time.
              GeocodingResult result = results.getFirst();
              geocodeCache.put(cacheKey, result);
              return Mono.just(result);
            });
  }

  private Mono<Weather> fetchCurrentConditions(GeocodingResult coordinates) {
    return forecastClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder
                    .path("/v1/forecast")
                    .queryParam("latitude", coordinates.latitude())
                    .queryParam("longitude", coordinates.longitude())
                    .queryParam("current", "temperature_2m,precipitation")
                    .build())
        .retrieve()
        .bodyToMono(ForecastResponse.class)
        .map(
            response -> {
              Weather weather = new Weather();
              weather.setTemperatureC(response.current().temperature2m());
              weather.setPrecipitationMm(response.current().precipitation());
              return weather;
            });
  }

  private Mono<Weather> fetchDailyConditions(GeocodingResult coordinates, LocalDate date) {
    return archiveClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder
                    .path("/v1/archive")
                    .queryParam("latitude", coordinates.latitude())
                    .queryParam("longitude", coordinates.longitude())
                    .queryParam("start_date", date)
                    .queryParam("end_date", date)
                    .queryParam("daily", "temperature_2m_mean,precipitation_sum")
                    .queryParam("timezone", "UTC")
                    .build())
        .retrieve()
        .bodyToMono(ArchiveResponse.class)
        .flatMap(
            response -> {
              DailyConditions daily = response.daily();
              if (daily == null
                  || daily.temperature2mMean() == null
                  || daily.temperature2mMean().isEmpty()) {
                return Mono.error(
                    new IllegalStateException("No historical weather available for " + date));
              }
              Weather weather = new Weather();
              weather.setTemperatureC(daily.temperature2mMean().getFirst());
              weather.setPrecipitationMm(
                  daily.precipitationSum() == null || daily.precipitationSum().isEmpty()
                      ? null
                      : daily.precipitationSum().getFirst());
              return Mono.just(weather);
            });
  }

  // Open-Meteo response shapes - only the fields this service actually uses.

  @JsonIgnoreProperties(ignoreUnknown = true)
  record GeocodingResponse(List<GeocodingResult> results) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record GeocodingResult(double latitude, double longitude) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ForecastResponse(CurrentConditions current) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record CurrentConditions(
      @JsonProperty("temperature_2m") Double temperature2m, Double precipitation) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ArchiveResponse(DailyConditions daily) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record DailyConditions(
      @JsonProperty("temperature_2m_mean") List<Double> temperature2mMean,
      @JsonProperty("precipitation_sum") List<Double> precipitationSum) {}
}
