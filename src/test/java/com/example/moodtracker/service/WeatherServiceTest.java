package com.example.moodtracker.service;

import com.example.moodtracker.model.Weather;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.*;

class WeatherServiceTest {

    @Mock
    private WebClient.Builder webClientBuilder;
    @Mock
    private WebClient webClient;
    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock
    private WebClient.ResponseSpec responseSpec;
    @Mock
    private ObjectMapper objectMapper;

    private WeatherService weatherService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);

        when(requestHeadersUriSpec.uri(any(Function.class))).thenAnswer(invocation -> {
            Function<UriBuilder, URI> uriFunction = invocation.getArgument(0);
            uriFunction.apply(UriComponentsBuilder.newInstance());
            return requestHeadersSpec;
        });

        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        weatherService = new WeatherService(webClientBuilder, objectMapper);
    }


    @Test
    void fetchWeather_ShouldReturnWeatherObject_WhenApiCallIsSuccessful() {
        String sampleJsonResponse = "{\"location\":{\"name\":\"London\",\"region\":\"City of London, Greater London\"," +
                "\"country\":\"United Kingdom\",\"lat\":51.52,\"lon\":-0.11,\"tz_id\":\"Europe\\/London\",\"localtime_epoch" +
                "\":1621538910,\"localtime\":\"2021-05-20 16:48\"},\"current\":{\"last_updated_epoch\":1621538400,\"last_updated\"" +
                ":\"2021-05-20 16:40\",\"temp_c\":13.0,\"temp_f\":55.4,\"is_day\":1,\"condition\":{\"text\":\"Sunny\",\"icon\":\"" +
                "\\/\\/cdn.weatherapi.com\\/weather\\/64x64\\/day\\/113.png\",\"code\":1000},\"wind_mph\":8.1,\"wind_kph\":13.0,\"" +
                "wind_degree\":240,\"wind_dir\":\"WSW\",\"pressure_mb\":1016.0,\"pressure_in\":30.5,\"precip_mm\":0.0,\"precip_in\":0.0,\"" +
                "humidity\":63,\"cloud\":0,\"feelslike_c\":13.0,\"feelslike_f\":55.4,\"vis_km\":10.0,\"vis_miles\":6.0,\"uv\":5.0,\"gust_mph\"" +
                ":12.1,\"gust_kph\":19.4}}";
        Weather sampleWeatherObject = new Weather();

        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(sampleJsonResponse));
        try {
            when(objectMapper.readValue(anyString(), eq(Weather.class))).thenReturn(sampleWeatherObject);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Weather result = weatherService.fetchWeather("London").block();

        assertNotNull(result);
        assertEquals(sampleWeatherObject.getLocation(), result.getLocation());
    }

    @Test
    void fetchWeather_ShouldThrowException_WhenApiCallIsUnsuccessful() {
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new Exception()));

        assertThrows(Exception.class, () -> {
            weatherService.fetchWeather("London").block();
        });
    }

    @Test
    void fetchWeather_ShouldHandleHttpException_WhenApiReturns404() {
        // Mock a 404 HTTP error response
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new WebClientResponseException(404, "Not Found",
                HttpHeaders.EMPTY, null, null)));

        assertThrows(WebClientResponseException.class, () -> {
            weatherService.fetchWeather("InvalidLocation").block();
        });
    }

    @Test
    void fetchWeather_ShouldHandleException_WhenApiCallExceedsTimeout() {
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new TimeoutException("Request timed out")));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            weatherService.fetchWeather("London").block();
        });
        assertTrue(exception.getCause() instanceof TimeoutException);
    }

}
