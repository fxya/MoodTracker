package com.example.moodtracker.service;

import com.example.moodtracker.model.Weather;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Looks up current weather via Open-Meteo (https://open-meteo.com), which is free
 * for non-commercial use and requires no API key. A location name (e.g. "London")
 * is first geocoded to coordinates, then used to fetch current conditions.
 */
@Service
public class WeatherService {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WebClient geocodingClient;
    private final WebClient forecastClient;

    public WeatherService(WebClient.Builder webClientBuilder) {
        this.geocodingClient = webClientBuilder.baseUrl("https://geocoding-api.open-meteo.com").build();
        this.forecastClient = webClientBuilder.baseUrl("https://api.open-meteo.com").build();
    }

    public Mono<Weather> fetchWeather(String location) {
        // Callers (e.g. MoodController) block on this from a servlet thread, so it
        // must never hang indefinitely if Open-Meteo is slow or unreachable.
        return geocode(location).flatMap(this::fetchCurrentConditions).timeout(TIMEOUT);
    }

    private Mono<GeocodingResult> geocode(String location) {
        return geocodingClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/search")
                        .queryParam("name", location)
                        .queryParam("count", 1)
                        .build())
                .retrieve()
                .bodyToMono(GeocodingResponse.class)
                .flatMap(response -> {
                    List<GeocodingResult> results = response.results();
                    if (results == null || results.isEmpty()) {
                        return Mono.error(new IllegalStateException("No location found for \"" + location + "\""));
                    }
                    return Mono.just(results.get(0));
                });
    }

    private Mono<Weather> fetchCurrentConditions(GeocodingResult coordinates) {
        return forecastClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/forecast")
                        .queryParam("latitude", coordinates.latitude())
                        .queryParam("longitude", coordinates.longitude())
                        .queryParam("current", "temperature_2m,precipitation")
                        .build())
                .retrieve()
                .bodyToMono(ForecastResponse.class)
                .map(response -> {
                    Weather weather = new Weather();
                    weather.setTemperatureC(response.current().temperature2m());
                    weather.setPrecipitationMm(response.current().precipitation());
                    return weather;
                });
    }

    // Open-Meteo response shapes - only the fields this service actually uses.

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeocodingResponse(List<GeocodingResult> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeocodingResult(double latitude, double longitude) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ForecastResponse(CurrentConditions current) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CurrentConditions(@JsonProperty("temperature_2m") Double temperature2m, Double precipitation) {
    }
}
