package com.example.moodtracker.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import com.example.moodtracker.model.Mood;
import com.example.moodtracker.model.User;
import com.example.moodtracker.model.Weather;
import com.example.moodtracker.model.WeeklySummary;
import com.example.moodtracker.repository.MoodRepository;
import com.example.moodtracker.repository.UserRepository;
import com.example.moodtracker.service.WeatherService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
public class MoodControllerTest {

  @Mock private MoodRepository moodRepository;

  @Mock private UserRepository userRepository;

  @Mock private Authentication authentication;

  @Mock private SecurityContext securityContext;

  @Mock private Model model; // Added for getMoodsPage, though not the primary focus

  @Mock private WeatherService weatherService;

  @InjectMocks private MoodController moodController;

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
    RedirectAttributes redirectAttributes = mock(RedirectAttributes.class);

    // Act
    String viewName = moodController.addMood(moodFormData, authentication, redirectAttributes);

    // Assert
    assertEquals("redirect:/moodtracker", viewName);
    verify(userRepository, times(1)).findByUsername(testUsername);
    verify(moodRepository, times(1)).save(moodArgumentCaptor.capture());
    verify(redirectAttributes).addFlashAttribute("moodSaved", true);

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
    moodController.addMood(moodFormData, authentication, mock(RedirectAttributes.class));

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
    String viewName =
        moodController.addMood(moodFormData, authentication, mock(RedirectAttributes.class));

    assertEquals("redirect:/moodtracker", viewName);
    verify(moodRepository).save(moodArgumentCaptor.capture());
    assertNull(moodArgumentCaptor.getValue().getWeather());
  }

  @Test
  void testGetMoodsPage_defaultsToFirstPageWithNoFilters() {
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
    Page<Mood> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 5), 0);
    when(moodRepository.search(eq(testUser), isNull(), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(emptyPage);
    when(moodRepository.findByUserAndDateAfterOrderByDateDesc(eq(testUser), any(Instant.class)))
        .thenReturn(Collections.emptyList());

    String viewName = moodController.getMoodsPage(null, null, null, 0, model, authentication);

    assertEquals("moodtracker", viewName);
    verify(model).addAttribute(eq("moods"), eq(emptyPage.getContent()));
    verify(model).addAttribute(eq("moodPage"), eq(emptyPage));
    verify(model).addAttribute(eq("q"), isNull());
    verify(model).addAttribute(eq("minRating"), isNull());
    verify(model).addAttribute(eq("maxRating"), isNull());
    verify(model).addAttribute(eq("newMood"), any(Mood.class));
    verify(model).addAttribute(eq("username"), eq(testUsername));
    verify(model).addAttribute(eq("weeklySummary"), any(WeeklySummary.class));
  }

  @Test
  void testGetMoodsPage_formatsDatesInUsersTimeZone() {
    testUser.setTimeZone("America/New_York");
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

    Mood mood = moodWithRating(8, Instant.parse("2026-01-15T05:30:00Z")); // 00:30 EST
    when(moodRepository.search(eq(testUser), isNull(), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(mood)));
    when(moodRepository.findByUserAndDateAfterOrderByDateDesc(eq(testUser), any(Instant.class)))
        .thenReturn(Collections.emptyList());

    moodController.getMoodsPage(null, null, null, 0, model, authentication);

    assertEquals("15-Jan-2026 00:30", mood.getFormattedDate());
  }

  @Test
  void testGetMoodsPage_fallsBackToServerZoneWhenUserTimeZoneUnset() {
    testUser.setTimeZone(null);
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

    Instant date = Instant.parse("2026-01-15T05:30:00Z");
    Mood mood = moodWithRating(8, date);
    when(moodRepository.search(eq(testUser), isNull(), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(mood)));
    when(moodRepository.findByUserAndDateAfterOrderByDateDesc(eq(testUser), any(Instant.class)))
        .thenReturn(Collections.emptyList());

    moodController.getMoodsPage(null, null, null, 0, model, authentication);

    String expected =
        java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm")
            .withZone(java.time.ZoneId.systemDefault())
            .format(date);
    assertEquals(expected, mood.getFormattedDate());
  }

  @Test
  void testGetMoodsPage_fallsBackToServerZoneWhenUserTimeZoneInvalid() {
    testUser.setTimeZone("Not/AZone");
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

    Instant date = Instant.parse("2026-01-15T05:30:00Z");
    Mood mood = moodWithRating(8, date);
    when(moodRepository.search(eq(testUser), isNull(), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(mood)));
    when(moodRepository.findByUserAndDateAfterOrderByDateDesc(eq(testUser), any(Instant.class)))
        .thenReturn(Collections.emptyList());

    moodController.getMoodsPage(null, null, null, 0, model, authentication);

    String expected =
        java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm")
            .withZone(java.time.ZoneId.systemDefault())
            .format(date);
    assertEquals(expected, mood.getFormattedDate());
  }

  @Test
  void testGetMoodsPage_passesSearchFilterAndPageToRepository() {
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
    when(moodRepository.search(eq(testUser), eq("happy"), eq(4), eq(9), any(Pageable.class)))
        .thenReturn(new PageImpl<>(Collections.emptyList()));
    when(moodRepository.findByUserAndDateAfterOrderByDateDesc(eq(testUser), any(Instant.class)))
        .thenReturn(Collections.emptyList());

    moodController.getMoodsPage("happy", 4, 9, 2, model, authentication);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(moodRepository)
        .search(eq(testUser), eq("happy"), eq(4), eq(9), pageableCaptor.capture());
    assertEquals(2, pageableCaptor.getValue().getPageNumber());
    assertEquals(5, pageableCaptor.getValue().getPageSize());
  }

  @Test
  void testGetMoodsPage_trimsSearchQuery() {
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
    when(moodRepository.search(eq(testUser), eq("happy"), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(Collections.emptyList()));
    when(moodRepository.findByUserAndDateAfterOrderByDateDesc(eq(testUser), any(Instant.class)))
        .thenReturn(Collections.emptyList());

    moodController.getMoodsPage("  happy  ", null, null, 0, model, authentication);
    verify(moodRepository)
        .search(eq(testUser), eq("happy"), isNull(), isNull(), any(Pageable.class));
  }

  @Test
  void testGetMoodsPage_blankSearchQueryNormalizedToNull() {
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
    when(moodRepository.search(eq(testUser), isNull(), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(Collections.emptyList()));
    when(moodRepository.findByUserAndDateAfterOrderByDateDesc(eq(testUser), any(Instant.class)))
        .thenReturn(Collections.emptyList());

    moodController.getMoodsPage("   ", null, null, 0, model, authentication);
    verify(moodRepository).search(eq(testUser), isNull(), isNull(), isNull(), any(Pageable.class));
  }

  @Test
  void testGetMoodsPage_negativePageClampedToZero() {
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
    when(moodRepository.search(eq(testUser), isNull(), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(Collections.emptyList()));
    when(moodRepository.findByUserAndDateAfterOrderByDateDesc(eq(testUser), any(Instant.class)))
        .thenReturn(Collections.emptyList());

    moodController.getMoodsPage(null, null, null, -3, model, authentication);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(moodRepository)
        .search(eq(testUser), isNull(), isNull(), isNull(), pageableCaptor.capture());
    assertEquals(0, pageableCaptor.getValue().getPageNumber());
  }

  @Test
  void testGetMoodsPage_weeklySummaryComparesThisWeekToLastWeek() {
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
    when(moodRepository.search(eq(testUser), isNull(), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(Collections.emptyList()));

    Instant now = Instant.now();
    List<Mood> recent =
        List.of(
            moodWithRating(8, now.minus(1, ChronoUnit.DAYS)),
            moodWithRating(6, now.minus(2, ChronoUnit.DAYS)),
            moodWithRating(3, now.minus(9, ChronoUnit.DAYS)));
    when(moodRepository.findByUserAndDateAfterOrderByDateDesc(eq(testUser), any(Instant.class)))
        .thenReturn(recent);

    ArgumentCaptor<WeeklySummary> summaryCaptor = ArgumentCaptor.forClass(WeeklySummary.class);
    moodController.getMoodsPage(null, null, null, 0, model, authentication);
    verify(model).addAttribute(eq("weeklySummary"), summaryCaptor.capture());

    WeeklySummary summary = summaryCaptor.getValue();
    assertEquals(2, summary.entryCount());
    assertEquals(7.0, summary.averageThisWeek());
    assertEquals(3.0, summary.averageLastWeek());
    assertTrue(summary.hasComparison());
    assertEquals(4.0, summary.delta(), 0.0001);
  }

  @Test
  void testGetMoodsPage_weeklySummary_noEntriesAtAll() {
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
    when(moodRepository.search(eq(testUser), isNull(), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(Collections.emptyList()));
    when(moodRepository.findByUserAndDateAfterOrderByDateDesc(eq(testUser), any(Instant.class)))
        .thenReturn(Collections.emptyList());

    ArgumentCaptor<WeeklySummary> summaryCaptor = ArgumentCaptor.forClass(WeeklySummary.class);
    moodController.getMoodsPage(null, null, null, 0, model, authentication);
    verify(model).addAttribute(eq("weeklySummary"), summaryCaptor.capture());

    WeeklySummary summary = summaryCaptor.getValue();
    assertFalse(summary.hasData());
    assertFalse(summary.hasComparison());
  }

  private Mood moodWithRating(int rating, Instant date) {
    Mood mood = new Mood();
    mood.setMoodRating(rating);
    mood.setDate(date);
    return mood;
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

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
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

    String viewName =
        moodController.updateMood(
            5L, "Happy", 9, "Feeling better", authentication, redirectAttributes);

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

    assertThrows(
        ResponseStatusException.class,
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

    assertThrows(
        ResponseStatusException.class,
        () -> moodController.deleteMood(5L, authentication, redirectAttributes));
    verify(moodRepository, never()).delete(any());
  }

  @Test
  void testExportCsv_includesHeaderAndAllFields() {
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

    Weather weather = new Weather();
    weather.setTemperatureC(14.5);
    weather.setPrecipitationMm(0.2);
    Mood mood = moodWithRating(8, Instant.parse("2026-01-15T10:30:00Z"));
    mood.setMood("Happy");
    mood.setNotes("Great day");
    mood.setWeather(weather);
    when(moodRepository.findByUserOrderByDateDesc(testUser)).thenReturn(List.of(mood));

    var response = moodController.exportCsv(authentication);

    assertEquals(200, response.getStatusCode().value());
    assertEquals(
        "attachment; filename=\"moods.csv\"",
        response.getHeaders().getFirst("Content-Disposition"));
    String body = response.getBody();
    assertTrue(body.startsWith("Date,Mood,Rating,Notes,TemperatureC,PrecipitationMm\r\n"));
    assertTrue(body.contains("2026-01-15T10:30:00Z,Happy,8,Great day,14.5,0.2\r\n"));
  }

  @Test
  void testExportCsv_quotesFieldsContainingCommasOrQuotes() {
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

    Mood mood = moodWithRating(5, Instant.parse("2026-01-15T10:30:00Z"));
    mood.setMood("Okay");
    mood.setNotes("Busy day, said \"hi\" to a friend");
    when(moodRepository.findByUserOrderByDateDesc(testUser)).thenReturn(List.of(mood));

    var response = moodController.exportCsv(authentication);

    assertTrue(response.getBody().contains("\"Busy day, said \"\"hi\"\" to a friend\""));
  }

  @Test
  void testExportCsv_onlyExportsCurrentUsersMoods() {
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
    when(moodRepository.findByUserOrderByDateDesc(testUser)).thenReturn(Collections.emptyList());

    moodController.exportCsv(authentication);

    verify(moodRepository).findByUserOrderByDateDesc(testUser);
  }

  @Test
  void testExportJson_returnsMoodListWithDownloadHeader() {
    when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
    Mood mood = moodWithRating(7, Instant.parse("2026-01-15T10:30:00Z"));
    when(moodRepository.findByUserOrderByDateDesc(testUser)).thenReturn(List.of(mood));

    var response = moodController.exportJson(authentication);

    assertEquals(200, response.getStatusCode().value());
    assertEquals(
        "attachment; filename=\"moods.json\"",
        response.getHeaders().getFirst("Content-Disposition"));
    assertEquals(List.of(mood), response.getBody());
  }
}
