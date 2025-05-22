package com.example.moodtracker.controller;

import com.example.moodtracker.model.Mood;
import com.example.moodtracker.model.User;
import com.example.moodtracker.repository.MoodRepository;
import com.example.moodtracker.repository.UserRepository;
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

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    }

    @Test
    void testGetMoodsPage() { // Basic test for the GET endpoint
        // Arrange
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
        // when(moodRepository.findByUserOrderByDateDesc(testUser)).thenReturn(Collections.emptyList()); // Example

        // Act
        String viewName = moodController.getMoodsPage(model);

        // Assert
        assertEquals("moodtracker", viewName);
        verify(model, times(1)).addAttribute(eq("moods"), anyList());
        verify(model, times(1)).addAttribute(eq("newMood"), any(Mood.class));
        verify(model, times(1)).addAttribute(eq("username"), eq(testUsername));
    }
}
