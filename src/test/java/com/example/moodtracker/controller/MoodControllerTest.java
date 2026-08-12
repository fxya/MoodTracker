package com.example.moodtracker.controller;

import com.example.moodtracker.model.Mood;
import com.example.moodtracker.model.User;
import com.example.moodtracker.model.Weather;
import com.example.moodtracker.repository.MoodRepository;
import com.example.moodtracker.repository.UserRepository;
import com.example.moodtracker.service.WeatherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MoodControllerTest {

    @Mock
    private MoodRepository moodRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Model model; // Added for getMoodsPage, though not the primary focus

    @Mock
    private WeatherService weatherService;

    @InjectMocks
    private MoodController moodController;

    private User testUser;
    private String testUsername = "testuser";

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername(testUsername);
        // Minimal setup for SecurityContext, can be expanded if needed
        when(authentication.getName()).thenReturn(testUsername);
        // It's generally better to mock SecurityContextHolder if used directly by the controller
        // but MoodController takes Authentication as a parameter, which is easier to mock.
    }

    @Test
    void testAddMoodWithNotes() {
        // Arrange
        Mood moodFormData = new Mood();
        moodFormData.setMood("Happy");
        moodFormData.setMoodRating(9);
        moodFormData.setNotes("Feeling great today, accomplished a lot!");

        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

        ArgumentCaptor<Mood> moodArgumentCaptor = ArgumentCaptor.forClass(Mood.class);

        // Act
        String viewName = moodController.addMood(moodFormData, authentication);

        // Assert
        assertEquals("redirect:/moodtracker", viewName);
        verify(userRepository, times(1)).findByUsername(testUsername);
        verify(moodRepository, times(1)).save(moodArgumentCaptor.capture());

        Mood savedMood = moodArgumentCaptor.getValue();
        assertNotNull(savedMood.getUser());
        assertEquals(testUsername, savedMood.getUser().getUsername());
        assertNotNull(savedMood.getDate());
        assertEquals("Happy", savedMood.getMood());
        assertEquals(9, savedMood.getMoodRating());
        assertEquals("Feeling great today, accomplished a lot!", savedMood.getNotes());

        // testUser has no location set, so weather should be skipped entirely -
        // logging a mood must never depend on the weather lookup succeeding.
        assertNull(savedMood.getWeather());
        verifyNoInteractions(weatherService);
    }

    @Test
    void testAddMood_fetchesWeatherWhenUserHasLocationSet() {
        testUser.setLocation("London");
        Weather london = new Weather();
        london.setTemperatureC(14.0);
        london.setPrecipitationMm(0.2);

        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
        when(weatherService.fetchWeather("London")).thenReturn(Mono.just(london));

        Mood moodFormData = new Mood();
        moodFormData.setMood("Content");
        moodFormData.setMoodRating(7);

        ArgumentCaptor<Mood> moodArgumentCaptor = ArgumentCaptor.forClass(Mood.class);
        moodController.addMood(moodFormData, authentication);

        verify(moodRepository).save(moodArgumentCaptor.capture());
        assertEquals(london, moodArgumentCaptor.getValue().getWeather());
    }

    @Test
    void testAddMood_stillSavesMoodWhenWeatherLookupFails() {
        testUser.setLocation("Nowhereville");
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
        when(weatherService.fetchWeather("Nowhereville"))
                .thenReturn(Mono.error(new IllegalStateException("No location found")));

        Mood moodFormData = new Mood();
        moodFormData.setMood("Anxious");
        moodFormData.setMoodRating(3);

        ArgumentCaptor<Mood> moodArgumentCaptor = ArgumentCaptor.forClass(Mood.class);
        String viewName = moodController.addMood(moodFormData, authentication);

        assertEquals("redirect:/moodtracker", viewName);
        verify(moodRepository).save(moodArgumentCaptor.capture());
        assertNull(moodArgumentCaptor.getValue().getWeather());
    }

    @Test
    void testGetMoodsPage() { // Basic test for the GET endpoint
        // Arrange
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
        // when(moodRepository.findByUserOrderByDateDesc(testUser)).thenReturn(Collections.emptyList()); // Example

        // Act
        String viewName = moodController.getMoodsPage(model, authentication);

        // Assert
        assertEquals("moodtracker", viewName);
        verify(model, times(1)).addAttribute(eq("moods"), anyList());
        verify(model, times(1)).addAttribute(eq("newMood"), any(Mood.class));
        verify(model, times(1)).addAttribute(eq("username"), eq(testUsername));
    }

    @Test
    void testShowEditForm() {
        Mood mood = new Mood();
        mood.setId(5L);
        mood.setMood("Happy");
        mood.setMoodRating(8);

        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
        when(moodRepository.findByIdAndUser(5L, testUser)).thenReturn(Optional.of(mood));

        String viewName = moodController.showEditForm(5L, model, authentication);

        assertEquals("mood-edit", viewName);
        verify(model).addAttribute("mood", mood);
    }

    @Test
    void testShowEditForm_notOwnedByCurrentUser_returnsNotFound() {
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
        when(moodRepository.findByIdAndUser(5L, testUser)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> moodController.showEditForm(5L, model, authentication));
        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void testUpdateMood() {
        Mood mood = new Mood();
        mood.setId(5L);
        mood.setMood("Sad");
        mood.setMoodRating(3);
        Instant originalDate = Instant.parse("2026-01-01T00:00:00Z");
        mood.setDate(originalDate);

        RedirectAttributes redirectAttributes = mock(RedirectAttributes.class);

        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
        when(moodRepository.findByIdAndUser(5L, testUser)).thenReturn(Optional.of(mood));

        String viewName = moodController.updateMood(5L, "Happy", 9, "Feeling better",
                authentication, redirectAttributes);

        assertEquals("redirect:/moodtracker", viewName);
        assertEquals("Happy", mood.getMood());
        assertEquals(9, mood.getMoodRating());
        assertEquals("Feeling better", mood.getNotes());
        // Editing only touches text/rating/notes - date must be untouched.
        assertEquals(originalDate, mood.getDate());
        verify(moodRepository).save(mood);
        verify(redirectAttributes).addFlashAttribute("moodUpdated", true);
    }

    @Test
    void testUpdateMood_notOwnedByCurrentUser_returnsNotFound() {
        RedirectAttributes redirectAttributes = mock(RedirectAttributes.class);
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
        when(moodRepository.findByIdAndUser(5L, testUser)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> moodController.updateMood(5L, "Happy", 9, null, authentication, redirectAttributes));
        verify(moodRepository, never()).save(any());
    }

    @Test
    void testDeleteMood() {
        Mood mood = new Mood();
        mood.setId(5L);

        RedirectAttributes redirectAttributes = mock(RedirectAttributes.class);

        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
        when(moodRepository.findByIdAndUser(5L, testUser)).thenReturn(Optional.of(mood));

        String viewName = moodController.deleteMood(5L, authentication, redirectAttributes);

        assertEquals("redirect:/moodtracker", viewName);
        verify(moodRepository).delete(mood);
        verify(redirectAttributes).addFlashAttribute("moodDeleted", true);
    }

    @Test
    void testDeleteMood_notOwnedByCurrentUser_returnsNotFound() {
        RedirectAttributes redirectAttributes = mock(RedirectAttributes.class);
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
        when(moodRepository.findByIdAndUser(5L, testUser)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> moodController.deleteMood(5L, authentication, redirectAttributes));
        verify(moodRepository, never()).delete(any());
    }
}
