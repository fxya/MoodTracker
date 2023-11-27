package com.example.moodtracker.service;

import com.example.moodtracker.model.Weather;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

@Service
public class WeatherService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public WeatherService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.baseUrl("http://api.weatherapi.com").build();
        this.objectMapper = objectMapper;
    }

    public Mono<Weather> fetchWeather(String location) {
        return Mono.fromCallable(this::readApiKey)
                .flatMap(apiKey -> this.webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/v1/current.json")
                                .queryParam("key", apiKey)
                                .queryParam("q", location)
                                .build())
                        .retrieve()
                        .bodyToMono(String.class)
                        .flatMap(jsonString -> {
                            try {
                                Weather weather = objectMapper.readValue(jsonString, Weather.class);
                                return Mono.just(weather);
                            } catch (IOException e) {
                                return Mono.error(e);
                            }
                        })
                );
    }

    private String readApiKey() throws IOException {
        try (Stream<String> lines = Files.lines(Paths.get(".apikey"))) {
            return lines.findFirst().orElseThrow(() -> new IOException("API key not found in file"));
        }
    }

}
