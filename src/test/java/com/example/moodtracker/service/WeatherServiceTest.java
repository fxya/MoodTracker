package com.example.moodtracker.service;

import com.example.moodtracker.model.Weather;
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

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

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

    private WeatherService weatherService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // WeatherService builds two WebClient instances (geocoding + forecast) off
        // the same builder; both resolve to this one mocked client/response chain,
        // distinguished below only by which response type each call requests.
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenAnswer(invocation -> {
            Function<UriBuilder, URI> uriFunction = invocation.getArgument(0);
            uriFunction.apply(UriComponentsBuilder.newInstance());
            return requestHeadersSpec;
        });
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        weatherService = new WeatherService(webClientBuilder);
    }

    private void mockGeocodingResult(double latitude, double longitude) {
        WeatherService.GeocodingResponse response = new WeatherService.GeocodingResponse(
                List.of(new WeatherService.GeocodingResult(latitude, longitude)));
        when(responseSpec.bodyToMono(WeatherService.GeocodingResponse.class)).thenReturn(Mono.just(response));
    }

    @Test
    void fetchWeather_ShouldReturnWeatherObject_WhenBothApiCallsSucceed() {
        mockGeocodingResult(51.52, -0.11);
        WeatherService.ForecastResponse forecastResponse = new WeatherService.ForecastResponse(
                new WeatherService.CurrentConditions(13.0, 0.4));
        when(responseSpec.bodyToMono(WeatherService.ForecastResponse.class)).thenReturn(Mono.just(forecastResponse));

        Weather result = weatherService.fetchWeather("London").block();

        assertNotNull(result);
        assertEquals(13.0, result.getTemperatureC());
        assertEquals(0.4, result.getPrecipitationMm());
    }

    @Test
    void fetchWeather_ShouldError_WhenLocationHasNoGeocodingResults() {
        WeatherService.GeocodingResponse emptyResponse = new WeatherService.GeocodingResponse(List.of());
        when(responseSpec.bodyToMono(WeatherService.GeocodingResponse.class)).thenReturn(Mono.just(emptyResponse));

        assertThrows(IllegalStateException.class, () -> weatherService.fetchWeather("Nowhereville").block());
    }

    @Test
    void fetchWeather_ShouldPropagateError_WhenGeocodingCallFails() {
        when(responseSpec.bodyToMono(WeatherService.GeocodingResponse.class))
                .thenReturn(Mono.error(new WebClientResponseException(500, "Server Error", HttpHeaders.EMPTY, null, null)));

        assertThrows(WebClientResponseException.class, () -> weatherService.fetchWeather("London").block());
    }

    @Test
    void fetchWeather_ShouldPropagateError_WhenForecastCallFails() {
        mockGeocodingResult(51.52, -0.11);
        when(responseSpec.bodyToMono(WeatherService.ForecastResponse.class))
                .thenReturn(Mono.error(new TimeoutException("Request timed out")));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> weatherService.fetchWeather("London").block());
        assertTrue(exception.getCause() instanceof TimeoutException);
    }
}
