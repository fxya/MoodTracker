package com.example.moodtracker.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.moodtracker.model.Weather;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
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

class WeatherServiceTest {

  @Mock private WebClient.Builder webClientBuilder;
  @Mock private WebClient webClient;
  @Mock private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
  @Mock private WebClient.RequestHeadersSpec requestHeadersSpec;
  @Mock private WebClient.ResponseSpec responseSpec;

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
    when(requestHeadersUriSpec.uri(any(Function.class)))
        .thenAnswer(
            invocation -> {
              Function<UriBuilder, URI> uriFunction = invocation.getArgument(0);
              uriFunction.apply(UriComponentsBuilder.newInstance());
              return requestHeadersSpec;
            });
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

    weatherService = new WeatherService(webClientBuilder);
  }

  private void mockGeocodingResult(double latitude, double longitude) {
    WeatherService.GeocodingResponse response =
        new WeatherService.GeocodingResponse(
            List.of(new WeatherService.GeocodingResult(latitude, longitude)));
    when(responseSpec.bodyToMono(WeatherService.GeocodingResponse.class))
        .thenReturn(Mono.just(response));
  }

  @Test
  void fetchWeather_ShouldReturnWeatherObject_WhenBothApiCallsSucceed() {
    mockGeocodingResult(51.52, -0.11);
    WeatherService.ForecastResponse forecastResponse =
        new WeatherService.ForecastResponse(new WeatherService.CurrentConditions(13.0, 0.4));
    when(responseSpec.bodyToMono(WeatherService.ForecastResponse.class))
        .thenReturn(Mono.just(forecastResponse));

    Weather result = weatherService.fetchWeather("London").block();

    assertNotNull(result);
    assertEquals(13.0, result.getTemperatureC());
    assertEquals(0.4, result.getPrecipitationMm());
  }

  @Test
  void fetchWeather_ShouldError_WhenLocationHasNoGeocodingResults() {
    WeatherService.GeocodingResponse emptyResponse =
        new WeatherService.GeocodingResponse(List.of());
    when(responseSpec.bodyToMono(WeatherService.GeocodingResponse.class))
        .thenReturn(Mono.just(emptyResponse));

    assertThrows(
        IllegalStateException.class, () -> weatherService.fetchWeather("Nowhereville").block());
  }

  @Test
  void fetchWeather_ShouldPropagateError_WhenGeocodingCallFails() {
    when(responseSpec.bodyToMono(WeatherService.GeocodingResponse.class))
        .thenReturn(
            Mono.error(
                new WebClientResponseException(
                    500, "Server Error", HttpHeaders.EMPTY, null, null)));

    assertThrows(
        WebClientResponseException.class, () -> weatherService.fetchWeather("London").block());
  }

  @Test
  void fetchWeather_ShouldPropagateError_WhenForecastCallFails() {
    mockGeocodingResult(51.52, -0.11);
    when(responseSpec.bodyToMono(WeatherService.ForecastResponse.class))
        .thenReturn(Mono.error(new TimeoutException("Request timed out")));

    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> weatherService.fetchWeather("London").block());
    assertTrue(exception.getCause() instanceof TimeoutException);
  }

  @Test
  void fetchWeather_cachesGeocodingResultForRepeatedLocation() {
    mockGeocodingResult(51.52, -0.11);
    WeatherService.ForecastResponse forecastResponse =
        new WeatherService.ForecastResponse(new WeatherService.CurrentConditions(13.0, 0.4));
    when(responseSpec.bodyToMono(WeatherService.ForecastResponse.class))
        .thenReturn(Mono.just(forecastResponse));

    weatherService.fetchWeather("London").block();
    weatherService.fetchWeather("London").block();

    // The geocoding lookup should only happen once - the second call is a cache hit -
    // but the forecast (current conditions) call still happens every time, since
    // weather actually does change.
    verify(responseSpec, times(1)).bodyToMono(WeatherService.GeocodingResponse.class);
    verify(responseSpec, times(2)).bodyToMono(WeatherService.ForecastResponse.class);
  }

  @Test
  void fetchWeather_geocodeCacheIsCaseAndWhitespaceInsensitive() {
    mockGeocodingResult(51.52, -0.11);
    when(responseSpec.bodyToMono(WeatherService.ForecastResponse.class))
        .thenReturn(
            Mono.just(
                new WeatherService.ForecastResponse(
                    new WeatherService.CurrentConditions(13.0, 0.4))));

    weatherService.fetchWeather("London").block();
    weatherService.fetchWeather("  LONDON  ").block();

    verify(responseSpec, times(1)).bodyToMono(WeatherService.GeocodingResponse.class);
  }

  @Test
  void fetchWeather_doesNotCacheAFailedGeocodingLookup() {
    WeatherService.GeocodingResponse emptyResponse =
        new WeatherService.GeocodingResponse(List.of());
    when(responseSpec.bodyToMono(WeatherService.GeocodingResponse.class))
        .thenReturn(Mono.just(emptyResponse));

    assertThrows(
        IllegalStateException.class, () -> weatherService.fetchWeather("Nowhereville").block());
    assertThrows(
        IllegalStateException.class, () -> weatherService.fetchWeather("Nowhereville").block());

    // A bad/misspelled location isn't memorized as a permanent failure - each
    // attempt re-queries in case it was transient or the user corrects the spelling.
    verify(responseSpec, times(2)).bodyToMono(WeatherService.GeocodingResponse.class);
  }

  @Test
  void fetchWeather_treatsDifferentLocationsAsSeparateCacheEntries() {
    mockGeocodingResult(51.52, -0.11);
    when(responseSpec.bodyToMono(WeatherService.ForecastResponse.class))
        .thenReturn(
            Mono.just(
                new WeatherService.ForecastResponse(
                    new WeatherService.CurrentConditions(13.0, 0.4))));

    weatherService.fetchWeather("London").block();
    weatherService.fetchWeather("Paris").block();

    verify(responseSpec, times(2)).bodyToMono(WeatherService.GeocodingResponse.class);
  }

  @Test
  void fetchHistoricalWeather_ShouldReturnDailyAggregate_WhenBothApiCallsSucceed() {
    mockGeocodingResult(51.52, -0.11);
    WeatherService.ArchiveResponse archiveResponse =
        new WeatherService.ArchiveResponse(
            new WeatherService.DailyConditions(List.of(9.5), List.of(3.2)));
    when(responseSpec.bodyToMono(WeatherService.ArchiveResponse.class))
        .thenReturn(Mono.just(archiveResponse));

    Weather result =
        weatherService.fetchHistoricalWeather("London", LocalDate.of(2026, 1, 15)).block();

    assertNotNull(result);
    assertEquals(9.5, result.getTemperatureC());
    assertEquals(3.2, result.getPrecipitationMm());
  }

  @Test
  void fetchHistoricalWeather_ShouldError_WhenNoDailyDataReturned() {
    mockGeocodingResult(51.52, -0.11);
    WeatherService.ArchiveResponse emptyResponse =
        new WeatherService.ArchiveResponse(
            new WeatherService.DailyConditions(List.of(), List.of()));
    when(responseSpec.bodyToMono(WeatherService.ArchiveResponse.class))
        .thenReturn(Mono.just(emptyResponse));

    assertThrows(
        IllegalStateException.class,
        () -> weatherService.fetchHistoricalWeather("London", LocalDate.of(2026, 1, 15)).block());
  }

  @Test
  void fetchHistoricalWeather_ShouldError_WhenLocationHasNoGeocodingResults() {
    WeatherService.GeocodingResponse emptyResponse =
        new WeatherService.GeocodingResponse(List.of());
    when(responseSpec.bodyToMono(WeatherService.GeocodingResponse.class))
        .thenReturn(Mono.just(emptyResponse));

    assertThrows(
        IllegalStateException.class,
        () ->
            weatherService
                .fetchHistoricalWeather("Nowhereville", LocalDate.of(2026, 1, 15))
                .block());
  }

  @Test
  void fetchHistoricalWeather_ReusesTheGeocodeCacheSharedWithFetchWeather() {
    mockGeocodingResult(51.52, -0.11);
    when(responseSpec.bodyToMono(WeatherService.ForecastResponse.class))
        .thenReturn(
            Mono.just(
                new WeatherService.ForecastResponse(
                    new WeatherService.CurrentConditions(13.0, 0.4))));
    when(responseSpec.bodyToMono(WeatherService.ArchiveResponse.class))
        .thenReturn(
            Mono.just(
                new WeatherService.ArchiveResponse(
                    new WeatherService.DailyConditions(List.of(9.5), List.of(3.2)))));

    weatherService.fetchWeather("London").block();
    weatherService.fetchHistoricalWeather("London", LocalDate.of(2026, 1, 15)).block();

    // The backfill flow geocodes once per user, then makes one historical-weather
    // call per mood - the whole point of sharing the cache is that only the first
    // of those calls (here, the earlier fetchWeather) should hit the geocoding API.
    verify(responseSpec, times(1)).bodyToMono(WeatherService.GeocodingResponse.class);
  }
}
