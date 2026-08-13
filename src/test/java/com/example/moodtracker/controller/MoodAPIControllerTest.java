package com.example.moodtracker.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.moodtracker.model.Mood;
import com.example.moodtracker.model.User;
import com.example.moodtracker.model.Weather;
import com.example.moodtracker.repository.MoodRepository;
import com.example.moodtracker.repository.UserRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MoodAPIController.class) // Test only the MoodAPIController
public class MoodAPIControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MoodRepository moodRepository;

  @MockitoBean private UserRepository userRepository;

  private Mood mood1;
  private Mood mood2;
  private User testUser;

  @BeforeEach
  void setUp() {
    Weather weather = new Weather();

    testUser = new User();
    testUser.setId(1L);
    testUser.setUsername("testuser");
    when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

    mood1 = new Mood();
    mood1.setId(1L);
    mood1.setMood("Happy");
    mood1.setDate(Instant.now());
    mood1.setMoodRating(7);
    mood1.setWeather(weather);

    mood2 = new Mood();
    mood2.setId(2L);
    mood2.setMood("Sad");
    mood2.setDate(Instant.now().minusSeconds(3600));
    mood2.setMoodRating(3);
    mood2.setWeather(weather);
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @WithMockUser(username = "testuser")
  void getAllMoods_shouldReturnOnlyTheAuthenticatedUsersMoods() throws Exception {
    when(moodRepository.findByUserOrderByDateDesc(testUser))
        .thenReturn(Arrays.asList(mood1, mood2));

    mockMvc
        .perform(get("/api/moods"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[0].mood").value("Happy"))
        .andExpect(jsonPath("$[0].moodRating").value(7))
        .andExpect(jsonPath("$[1].mood").value("Sad"))
        .andExpect(jsonPath("$[1].moodRating").value(3));

    // findAll() would leak every user's moods to whoever is logged in - make sure
    // the scoped lookup is what actually gets called.
    verify(moodRepository, never()).findAll();
  }
}
