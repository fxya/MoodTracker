package com.example.moodtracker.controller;

import com.example.moodtracker.model.Mood;
import com.example.moodtracker.model.User;
import com.example.moodtracker.model.Weather; // Assuming Weather might be needed for Mood object
import com.example.moodtracker.repository.MoodRepository;
import com.example.moodtracker.repository.UserRepository;
import com.example.moodtracker.service.WeatherService; // WeatherService is a dependency
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.view.RedirectView;
import reactor.core.publisher.Mono; // For WeatherService mock
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class) // Added for Mockito support with @Mock and @InjectMocks
@WebMvcTest(MoodAPIController.class) // Test only the MoodAPIController
public class MoodAPIControllerTest {

    // Matches the exact "yyyy-MM-dd'T'HH:mm:ss.SSSX" format MoodAPIController expects
    // for clientCurrentDateTime; Instant.now().toString() varies in fraction-digit
    // count and isn't reliably parseable by that pattern.
    private static final DateTimeFormatter CLIENT_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    // MockitoBean for existing MockMvc tests
    @MockitoBean
    private MoodRepository mockBeanMoodRepository;

    @MockitoBean
    private WeatherService mockBeanWeatherService;

    @MockitoBean
    private UserRepository mockBeanUserRepository;

    // Mocks for the new unit test
    @Mock
    private MoodRepository moodRepository; // Renamed from mockBeanMoodRepository to avoid clash if @MockBean also creates a field with this name

    @Mock
    private WeatherService weatherService; // Renamed from mockBeanWeatherService

    @Mock
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper; // For converting objects to JSON

    private Mood mood1;
    private Mood mood2;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Mock Weather data for MockMvc tests
        Weather mockMvcWeather = new Weather();

        // Mock WeatherService response for MockMvc tests using the @MockBean instance
        when(mockBeanWeatherService.fetchWeather(anyString())).thenReturn(Mono.just(mockMvcWeather));

        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        when(mockBeanUserRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        mood1 = new Mood();
        mood1.setId(1L);
        mood1.setMood("Happy");
        mood1.setDate(Instant.now());
        mood1.setMoodRating(7);
        mood1.setWeather(mockMvcWeather);

        mood2 = new Mood();
        mood2.setId(2L);
        mood2.setMood("Sad");
        mood2.setDate(Instant.now().minusSeconds(3600));
        mood2.setMoodRating(3);
        mood2.setWeather(mockMvcWeather);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @WithMockUser(username = "testuser")
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

        // The controller returns a Mono<RedirectView>, so Spring MVC processes it
        // asynchronously; the redirect only shows up after the async dispatch completes.
        var mvcResult = mockMvc.perform(post("/api/moods")
                        .with(csrf())
                        .param("mood", "Very Happy") // Corrected param name to "mood"
                        .param("clientCurrentDateTime", CLIENT_DATE_TIME_FORMATTER.format(Instant.now()))
                        .param("moodRating", "9")
                        .param("location", "TestCityForMvc") // Added location param for this test
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().is3xxRedirection()) // Expecting a redirect
                .andExpect(redirectedUrl("/moods"));

        // The mood should be attributed to the authenticated user - this endpoint used
        // to save moods with no user at all, so they'd never show up in anyone's list.
        ArgumentCaptor<Mood> moodCaptor = ArgumentCaptor.forClass(Mood.class);
        verify(mockBeanMoodRepository).save(moodCaptor.capture());
        assertEquals(testUser, moodCaptor.getValue().getUser());
    }

    @Test
    @WithMockUser(username = "testuser")
    void getAllMoods_shouldReturnOnlyTheAuthenticatedUsersMoods() throws Exception {
        when(mockBeanMoodRepository.findByUserOrderByDateDesc(testUser)).thenReturn(Arrays.asList(mood1, mood2));

        mockMvc.perform(get("/api/moods"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].mood").value("Happy"))
                .andExpect(jsonPath("$[0].moodRating").value(7))
                .andExpect(jsonPath("$[1].mood").value("Sad"))
                .andExpect(jsonPath("$[1].moodRating").value(3));

        // findAll() would leak every user's moods to whoever is logged in - make sure
        // the scoped lookup is what actually gets called.
        verify(mockBeanMoodRepository, never()).findAll();
    }

    @Test
    void addMood_shouldUseLocationParameterForWeatherService() {
        // Given
        String moodText = "Joyful";
        Integer moodRating = 9;
        String dateTimeString = "2024-01-15T10:00:00.000Z"; // Valid ISO 8601 format
        String testLocation = "Paris";
        Weather mockWeather = new Weather(); // Example weather

        Authentication authentication = new UsernamePasswordAuthenticationToken("testuser", "password");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Mocking WeatherService to return specific weather for the testLocation
        when(weatherService.fetchWeather(testLocation)).thenReturn(Mono.just(mockWeather));

        // Mocking MoodRepository save operation
        when(moodRepository.save(any(Mood.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // When
        MoodAPIController controller = new MoodAPIController(moodRepository, userRepository, weatherService);
        Mono<RedirectView> result = controller.addMood(moodText, moodRating, dateTimeString, testLocation);

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

        // Verify that save was called on moodRepository, with the mood attributed to the
        // authenticated user - moods created via this endpoint used to be saved with no
        // user at all, which meant they'd never show up in anyone's mood list.
        ArgumentCaptor<Mood> moodCaptor = ArgumentCaptor.forClass(Mood.class);
        verify(moodRepository, times(1)).save(moodCaptor.capture());
        assertEquals(testUser, moodCaptor.getValue().getUser());
    }
}
