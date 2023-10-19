package com.example.moodtracker.service;

import com.example.moodtracker.model.Weather;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;

@Service
public class WeatherService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public WeatherService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.baseUrl("http://api.weatherapi.com").build();
        this.objectMapper = objectMapper;
    }

    public Mono<Weather> fetchWeather(String location) {
        return this.webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/current.json")
                        .queryParam("key", "790f798596a94c18a21162940231810")
                        .queryParam("q", location)
                        .build())
                .retrieve()
                .bodyToMono(String.class)  // Retrieve the body as a String
                .flatMap(jsonString -> {
                    try {
                        Weather weather = objectMapper.readValue(jsonString, Weather.class);
                        return Mono.just(weather);
                    } catch (IOException e) {
                        return Mono.error(e);
                    }
                });
    }
}
