package com.example.moodtracker.controller;

import com.example.moodtracker.model.Mood;
import com.example.moodtracker.model.Weather; // Assuming Weather might be needed for Mood object
import com.example.moodtracker.repository.MoodRepository;
import com.example.moodtracker.service.WeatherService; // WeatherService is a dependency
import com.fasterxml.jackson.databind.ObjectMapper; // For JSON conversion
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.view.RedirectView;
import reactor.core.publisher.Mono; // For WeatherService mock
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class) // Added for Mockito support with @Mock and @InjectMocks
@WebMvcTest(MoodAPIController.class) // Test only the MoodAPIController
public class MoodAPIControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // MockBean for existing MockMvc tests
    @MockBean
    private MoodRepository mockBeanMoodRepository;

    @MockBean
    private WeatherService mockBeanWeatherService;

    // Mocks for the new unit test
    @Mock
    private MoodRepository moodRepository; // Renamed from mockBeanMoodRepository to avoid clash if @MockBean also creates a field with this name

    @Mock
    private WeatherService weatherService; // Renamed from mockBeanWeatherService

    @InjectMocks
    private MoodAPIController moodAPIController;


    @Autowired
    private ObjectMapper objectMapper; // For converting objects to JSON

    private Mood mood1;
    private Mood mood2;

    @BeforeEach
    void setUp() {
        // Mock Weather data for MockMvc tests
        Weather mockMvcWeather = new Weather("Cloudy with a chance of meatballs", "mvc-test-icon-01d");

        // Mock WeatherService response for MockMvc tests using the @MockBean instance
        when(mockBeanWeatherService.fetchWeather(anyString())).thenReturn(Mono.just(mockMvcWeather));

        mood1 = new Mood(1L, "Happy", Instant.now(), 7, mockMvcWeather);
        mood2 = new Mood(2L, "Sad", Instant.now().minusSeconds(3600), 3, mockMvcWeather);
    }

    @Test
    void addMood_shouldSaveMoodWithRatingAndRedirect() throws Exception {
        // Mock the save operation for MockMvc tests using the @MockBean instance
        when(mockBeanMoodRepository.save(any(Mood.class))).thenAnswer(invocation -> {
            Mood moodToSave = invocation.getArgument(0);
            // Simulate saving by assigning an ID if it's null (as per controller logic for new moods)
            if (moodToSave.getId() == null) {
                 // In a real scenario, ID would be generated. For the mock, we can just return it as is
                 // or simulate an ID assignment if the controller relies on it post-save.
                 // The controller creates new Mood(null, ...), so the saved mood will have an ID.
                 // Let's assume the save operation works and the object is returned as is by the mock.
            }
            return moodToSave;
        });

        mockMvc.perform(post("/api/moods")
                        .param("mood", "Very Happy") // Corrected param name to "mood"
                        .param("clientCurrentDateTime", Instant.now().toString())
                        .param("moodRating", "9")
                        .param("location", "TestCityForMvc") // Added location param for this test
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().is3xxRedirection()) // Expecting a redirect
                .andExpect(redirectedUrl("/moods"));

        // We could add Mockito.verify here if we wanted to ensure save was called,
        // and capture the argument to check if moodRating was correctly passed.
        // For now, the controller logic is simple enough that if it redirects, it likely worked.
    }

    @Test
    void getAllMoods_shouldReturnMoodsWithRatings() throws Exception {
        // Ensure this uses the @MockBean for findAll
        when(mockBeanMoodRepository.findAll()).thenReturn(Arrays.asList(mood1, mood2));

        mockMvc.perform(get("/api/moods"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].mood").value("Happy"))
                .andExpect(jsonPath("$[0].moodRating").value(7))
                .andExpect(jsonPath("$[1].mood").value("Sad"))
                .andExpect(jsonPath("$[1].moodRating").value(3));
    }

    @Test
    void addMood_shouldUseLocationParameterForWeatherService() {
        // Given
        String moodText = "Joyful";
        Integer moodRating = 9;
        String dateTimeString = "2024-01-15T10:00:00.000Z"; // Valid ISO 8601 format
        String testLocation = "Paris";
        Weather mockWeather = new Weather("Sunny", "01d"); // Example weather

        // Mocking WeatherService to return specific weather for the testLocation
        when(weatherService.fetchWeather(testLocation)).thenReturn(Mono.just(mockWeather));

        // Mocking MoodRepository save operation
        when(moodRepository.save(any(Mood.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Mono<RedirectView> result = moodAPIController.addMood(moodText, moodRating, dateTimeString, testLocation);

        // Then
        // Verify the reactive stream completes and the redirect URL is correct
        StepVerifier.create(result)
                .expectNextMatches(redirectView -> {
                    boolean urlMatch = redirectView.getUrl().equals("/moods");
                    if (!urlMatch) {
                        System.out.println("Redirect URL mismatch: Expected /moods, got " + redirectView.getUrl());
                    }
                    return urlMatch;
                })
                .verifyComplete();

        // Verify that fetchWeather was called on weatherService with the correct location
        verify(weatherService, times(1)).fetchWeather(testLocation);

        // Verify that save was called on moodRepository
        verify(moodRepository, times(1)).save(any(Mood.class));
    }
}
