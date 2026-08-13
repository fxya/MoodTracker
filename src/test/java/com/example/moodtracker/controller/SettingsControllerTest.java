package com.example.moodtracker.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

import com.example.moodtracker.model.Mood;
import com.example.moodtracker.model.User;
import com.example.moodtracker.model.Weather;
import com.example.moodtracker.repository.MoodRepository;
import com.example.moodtracker.repository.UserRepository;
import com.example.moodtracker.service.WeatherService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
public class SettingsControllerTest {

  @Mock private UserRepository userRepository;

  @Mock private PasswordEncoder passwordEncoder;

  @Mock private MoodRepository moodRepository;

  @Mock private WeatherService weatherService;

  @Mock private Validator validator;

  @Mock private Authentication authentication;

  @Mock private RedirectAttributes redirectAttributes;

  @InjectMocks private SettingsController settingsController;

  private User testUser;
  private final String testUsername = "testuser";

  @BeforeEach
  void setUp() {
    testUser = new User();
    testUser.setId(1L);
    testUser.setUsername(testUsername);
    testUser.setPassword("encoded-old-password");
  }

  @Test
  void testShowSettings_exposesCurrentLocationAndTimeZone() {
    testUser.setLocation("London");
    testUser.setTimeZone("Europe/London");
    when(authentication.getName()).thenReturn(testUsername);
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

    Model model = mock(Model.class);
    String viewName = settingsController.showSettings(model, authentication);

    assertEquals("settings", viewName);
    verify(model).addAttribute("location", "London");
    verify(model).addAttribute("timeZone", "Europe/London");
    verify(model).addAttribute(eq("availableTimeZones"), any());
    verify(model).addAttribute("username", testUsername);
  }

  @Test
  void testUpdateSettings_savesLocationAndTimeZone() {
    when(authentication.getName()).thenReturn(testUsername);
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

    String viewName =
        settingsController.updateSettings(
            null, "London", "Europe/London", authentication, redirectAttributes);

    assertEquals("redirect:/settings", viewName);
    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    assertEquals("London", userCaptor.getValue().getLocation());
    assertEquals("Europe/London", userCaptor.getValue().getTimeZone());
    verify(redirectAttributes).addFlashAttribute("settingsSaved", true);
  }

  @Test
  void testUpdateSettings_blankTimeZoneClearsIt() {
    testUser.setTimeZone("Europe/London");
    when(authentication.getName()).thenReturn(testUsername);
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

    settingsController.updateSettings(null, "London", "  ", authentication, redirectAttributes);

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    assertNull(userCaptor.getValue().getTimeZone());
  }

  @Test
  void testUpdateSettings_invalidTimeZone_doesNotSave() {
    when(authentication.getName()).thenReturn(testUsername);
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

    String viewName =
        settingsController.updateSettings(
            null, "London", "Not/AZone", authentication, redirectAttributes);

    assertEquals("redirect:/settings", viewName);
    verify(userRepository, never()).save(any());
    verify(redirectAttributes).addFlashAttribute(eq("settingsError"), anyString());
  }

  @Test
  void testUpdateSettings_savesValidEmail() {
    when(authentication.getName()).thenReturn(testUsername);
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
    when(validator.validateValue(
            eq(User.class),
            eq("email"),
            eq("new@example.com"),
            eq(User.RegistrationValidation.class)))
        .thenReturn(Set.of());
    when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());

    String viewName =
        settingsController.updateSettings(
            "new@example.com", null, null, authentication, redirectAttributes);

    assertEquals("redirect:/settings", viewName);
    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    assertEquals("new@example.com", userCaptor.getValue().getEmail());
    verify(redirectAttributes).addFlashAttribute("settingsSaved", true);
  }

  @Test
  void testUpdateSettings_invalidEmailFormat_doesNotSave() {
    when(authentication.getName()).thenReturn(testUsername);
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
    ConstraintViolation<User> violation = mock(ConstraintViolation.class);
    when(violation.getMessage()).thenReturn("Enter a valid email address.");
    when(validator.validateValue(
            eq(User.class), eq("email"), eq("not-an-email"), eq(User.RegistrationValidation.class)))
        .thenReturn(Set.of(violation));

    String viewName =
        settingsController.updateSettings(
            "not-an-email", null, null, authentication, redirectAttributes);

    assertEquals("redirect:/settings", viewName);
    verify(userRepository, never()).save(any());
    verify(redirectAttributes).addFlashAttribute("settingsError", "Enter a valid email address.");
  }

  @Test
  void testUpdateSettings_emailAlreadyUsedByAnotherAccount_doesNotSave() {
    when(authentication.getName()).thenReturn(testUsername);
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
    when(validator.validateValue(
            eq(User.class),
            eq("email"),
            eq("taken@example.com"),
            eq(User.RegistrationValidation.class)))
        .thenReturn(Set.of());
    User otherUser = new User();
    otherUser.setId(2L);
    when(userRepository.findByEmail("taken@example.com")).thenReturn(Optional.of(otherUser));

    String viewName =
        settingsController.updateSettings(
            "taken@example.com", null, null, authentication, redirectAttributes);

    assertEquals("redirect:/settings", viewName);
    verify(userRepository, never()).save(any());
    verify(redirectAttributes).addFlashAttribute(eq("settingsError"), anyString());
  }

  @Test
  void testUpdateSettings_emailUnchangedForSameAccount_saves() {
    testUser.setEmail("mine@example.com");
    when(authentication.getName()).thenReturn(testUsername);
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
    when(validator.validateValue(
            eq(User.class),
            eq("email"),
            eq("mine@example.com"),
            eq(User.RegistrationValidation.class)))
        .thenReturn(Set.of());
    when(userRepository.findByEmail("mine@example.com")).thenReturn(Optional.of(testUser));

    String viewName =
        settingsController.updateSettings(
            "mine@example.com", null, null, authentication, redirectAttributes);

    assertEquals("redirect:/settings", viewName);
    verify(userRepository).save(testUser);
    verify(redirectAttributes).addFlashAttribute("settingsSaved", true);
  }

  @Test
  void testChangePassword_success() {
    when(authentication.getName()).thenReturn(testUsername);
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
    when(passwordEncoder.matches("oldpass", testUser.getPassword())).thenReturn(true);
    when(passwordEncoder.encode("newpass")).thenReturn("encoded-new-password");

    String viewName =
        settingsController.changePassword(
            "oldpass", "newpass", "newpass", authentication, redirectAttributes);

    assertEquals("redirect:/settings", viewName);
    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    assertEquals("encoded-new-password", userCaptor.getValue().getPassword());
    verify(redirectAttributes).addFlashAttribute("passwordChanged", true);
  }

  @Test
  void testChangePassword_wrongCurrentPassword_doesNotSave() {
    when(authentication.getName()).thenReturn(testUsername);
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
    when(passwordEncoder.matches("wrongpass", testUser.getPassword())).thenReturn(false);

    String viewName =
        settingsController.changePassword(
            "wrongpass", "newpass", "newpass", authentication, redirectAttributes);

    assertEquals("redirect:/settings", viewName);
    verify(userRepository, never()).save(any());
    verify(redirectAttributes).addFlashAttribute(eq("passwordError"), anyString());
  }

  @Test
  void testChangePassword_confirmationMismatch_doesNotSave() {
    when(authentication.getName()).thenReturn(testUsername);
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
    when(passwordEncoder.matches("oldpass", testUser.getPassword())).thenReturn(true);

    String viewName =
        settingsController.changePassword(
            "oldpass", "newpass", "different", authentication, redirectAttributes);

    assertEquals("redirect:/settings", viewName);
    verify(userRepository, never()).save(any());
    verify(redirectAttributes).addFlashAttribute(eq("passwordError"), anyString());
  }

  @Test
  void testChangePassword_blankNewPassword_doesNotSave() {
    when(authentication.getName()).thenReturn(testUsername);
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
    when(passwordEncoder.matches("oldpass", testUser.getPassword())).thenReturn(true);

    String viewName =
        settingsController.changePassword(
            "oldpass", "  ", "  ", authentication, redirectAttributes);

    assertEquals("redirect:/settings", viewName);
    verify(userRepository, never()).save(any());
    verify(redirectAttributes).addFlashAttribute(eq("passwordError"), anyString());
  }

  @Test
  void testDeleteAccount_success() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpSession session = mock(HttpSession.class);
    when(request.getSession()).thenReturn(session);

    when(authentication.getName()).thenReturn(testUsername);
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
    when(passwordEncoder.matches("correctpass", testUser.getPassword())).thenReturn(true);

    String viewName =
        settingsController.deleteAccount(
            "correctpass", authentication, request, redirectAttributes);

    assertEquals("redirect:/login?accountDeleted", viewName);
    verify(userRepository).delete(testUser);
    verify(session).invalidate();
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void testDeleteAccount_wrongPassword_doesNotDelete() {
    HttpServletRequest request = mock(HttpServletRequest.class);

    when(authentication.getName()).thenReturn(testUsername);
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
    when(passwordEncoder.matches("wrongpass", testUser.getPassword())).thenReturn(false);

    String viewName =
        settingsController.deleteAccount("wrongpass", authentication, request, redirectAttributes);

    assertEquals("redirect:/settings", viewName);
    verify(userRepository, never()).delete(any());
    verify(request, never()).getSession();
    verify(redirectAttributes).addFlashAttribute(eq("deleteError"), anyString());
  }

  @Test
  void testBackfillWeather_noLocationSet_returnsErrorWithoutQuerying() {
    when(authentication.getName()).thenReturn(testUsername);
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

    String viewName = settingsController.backfillWeather(authentication, redirectAttributes);

    assertEquals("redirect:/settings", viewName);
    verify(moodRepository, never()).findByUserAndWeatherIsNull(any());
    verify(redirectAttributes).addFlashAttribute(eq("backfillError"), anyString());
  }

  @Test
  void testBackfillWeather_noMoodsMissingWeather_returnsMessageWithoutValidating() {
    testUser.setLocation("London");
    when(authentication.getName()).thenReturn(testUsername);
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
    when(moodRepository.findByUserAndWeatherIsNull(testUser)).thenReturn(List.of());

    String viewName = settingsController.backfillWeather(authentication, redirectAttributes);

    assertEquals("redirect:/settings", viewName);
    verify(weatherService, never()).validateLocation(any());
    verify(redirectAttributes)
        .addFlashAttribute("backfillMessage", "No moods are missing weather.");
  }

  @Test
  void testBackfillWeather_invalidLocation_failsFastWithoutFetchingPerMood() {
    testUser.setLocation("Nowhereville");
    when(authentication.getName()).thenReturn(testUsername);
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
    Mood mood = new Mood();
    mood.setId(1L);
    mood.setDate(Instant.parse("2026-01-15T10:30:00Z"));
    when(moodRepository.findByUserAndWeatherIsNull(testUser)).thenReturn(List.of(mood));
    when(weatherService.validateLocation("Nowhereville"))
        .thenReturn(Mono.error(new IllegalStateException("No location found")));

    String viewName = settingsController.backfillWeather(authentication, redirectAttributes);

    assertEquals("redirect:/settings", viewName);
    verify(weatherService, never()).fetchHistoricalWeather(any(), any());
    verify(moodRepository, never()).save(any());
    verify(redirectAttributes).addFlashAttribute(eq("backfillError"), anyString());
  }

  @Test
  void testBackfillWeather_savesWeatherForEachMoodMissingIt() {
    testUser.setLocation("London");
    when(authentication.getName()).thenReturn(testUsername);
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

    Mood mood1 = new Mood();
    mood1.setId(1L);
    mood1.setDate(Instant.parse("2026-01-15T10:30:00Z"));
    Mood mood2 = new Mood();
    mood2.setId(2L);
    mood2.setDate(Instant.parse("2026-01-16T10:30:00Z"));
    when(moodRepository.findByUserAndWeatherIsNull(testUser)).thenReturn(List.of(mood1, mood2));
    when(weatherService.validateLocation("London")).thenReturn(Mono.empty());

    Weather weather = new Weather();
    weather.setTemperatureC(9.5);
    weather.setPrecipitationMm(1.0);
    when(weatherService.fetchHistoricalWeather(eq("London"), any())).thenReturn(Mono.just(weather));

    String viewName = settingsController.backfillWeather(authentication, redirectAttributes);

    assertEquals("redirect:/settings", viewName);
    assertEquals(weather, mood1.getWeather());
    assertEquals(weather, mood2.getWeather());
    verify(moodRepository, times(2)).save(any());
    verify(redirectAttributes)
        .addFlashAttribute("backfillMessage", "Backfilled weather for 2 of 2 mood(s).");
  }

  @Test
  void testBackfillWeather_perMoodFailureIsSkippedNotFatal() {
    testUser.setLocation("London");
    when(authentication.getName()).thenReturn(testUsername);
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

    Mood mood1 = new Mood();
    mood1.setId(1L);
    mood1.setDate(Instant.parse("2026-01-15T10:30:00Z"));
    Mood mood2 = new Mood();
    mood2.setId(2L);
    mood2.setDate(Instant.parse("2026-01-16T10:30:00Z"));
    when(moodRepository.findByUserAndWeatherIsNull(testUser)).thenReturn(List.of(mood1, mood2));
    when(weatherService.validateLocation("London")).thenReturn(Mono.empty());

    Weather weather = new Weather();
    weather.setTemperatureC(9.5);
    when(weatherService.fetchHistoricalWeather(eq("London"), any()))
        .thenReturn(Mono.error(new IllegalStateException("No historical weather available")))
        .thenReturn(Mono.just(weather));

    String viewName = settingsController.backfillWeather(authentication, redirectAttributes);

    assertEquals("redirect:/settings", viewName);
    assertNull(mood1.getWeather());
    assertEquals(weather, mood2.getWeather());
    verify(moodRepository, times(1)).save(any());
    verify(redirectAttributes)
        .addFlashAttribute("backfillMessage", "Backfilled weather for 1 of 2 mood(s).");
  }
}
