package com.example.moodtracker.service;

import com.example.moodtracker.model.Weather;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class WeatherService {

    private final WebClient webClient;

    public WeatherService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("http://api.weatherapi.com").build();
    }

    public Mono<Weather> fetchWeather(String location) {
        return this.webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/current.json")
                        .queryParam("key", "790f798596a94c18a21162940231810")
                        .queryParam("q", location)
                        .build())
                .retrieve()
                .bodyToMono(Weather.class)
                .doOnNext(weather -> System.out.println("Weather: " + weather.getTempC()));
    }
}

