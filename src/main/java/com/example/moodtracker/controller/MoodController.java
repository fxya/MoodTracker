package com.example.moodtracker.controller;

import com.example.moodtracker.model.Mood;
import com.example.moodtracker.model.User;
import com.example.moodtracker.model.Weather;
import com.example.moodtracker.model.WeeklySummary;
import com.example.moodtracker.repository.MoodRepository;
import com.example.moodtracker.repository.UserRepository; // To fetch the current user
import com.example.moodtracker.service.WeatherService;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping; // Added for base path
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import reactor.core.publisher.Mono;

@Controller
@RequestMapping("/moodtracker") // Base path for mood related operations
public class MoodController {

  private static final Logger log = LoggerFactory.getLogger(MoodController.class);
  private static final int PAGE_SIZE = 5;
  private static final DateTimeFormatter MOOD_DATE_FORMAT =
      DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

  private final MoodRepository moodRepository;
  private final UserRepository userRepository; // To fetch the User entity
  private final WeatherService weatherService;

  public MoodController(
      MoodRepository moodRepository, UserRepository userRepository, WeatherService weatherService) {
    this.moodRepository = moodRepository;
    this.userRepository = userRepository;
    this.weatherService = weatherService;
  }

  // Display moods for the current user (optionally searched/filtered/paginated),
  // the weekly summary stat card, and a form to add a new mood
  @GetMapping
  public String getMoodsPage(
      @RequestParam(value = "q", required = false) String q,
      @RequestParam(value = "minRating", required = false) Integer minRating,
      @RequestParam(value = "maxRating", required = false) Integer maxRating,
      @RequestParam(value = "page", defaultValue = "0") int page,
      Model model,
      Authentication authentication) {
    User user = currentUser(authentication);
    String normalizedQuery = (q == null || q.isBlank()) ? null : q.trim();
    Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE);
    Page<Mood> moodPage =
        moodRepository.search(user, normalizedQuery, minRating, maxRating, pageable);

    ZoneId zone = resolveZone(user);
    moodPage
        .getContent()
        .forEach(
            mood -> mood.setFormattedDate(MOOD_DATE_FORMAT.withZone(zone).format(mood.getDate())));

    model.addAttribute("moods", moodPage.getContent());
    model.addAttribute("moodPage", moodPage);
    model.addAttribute("q", q);
    model.addAttribute("minRating", minRating);
    model.addAttribute("maxRating", maxRating);
    model.addAttribute("newMood", new Mood()); // For the form
    // Potentially add other attributes like username to display on page
    model.addAttribute("username", user.getUsername());
    model.addAttribute("weeklySummary", computeWeeklySummary(user));
    return "moodtracker"; // Name of the Thymeleaf template
  }

  // Handle submission of a new mood
  @PostMapping("/add")
  public String addMood(
      @ModelAttribute("newMood") Mood mood,
      Authentication authentication,
      RedirectAttributes redirectAttributes) {
    User user = currentUser(authentication);

    mood.setUser(user);
    mood.setDate(Instant.now());
    // Assuming mood.mood (String) and mood.moodRating (Integer) are set from the form
    mood.setWeather(fetchWeatherForUser(user));
    moodRepository.save(mood);

    redirectAttributes.addFlashAttribute("moodSaved", true);
    return "redirect:/moodtracker"; // Redirect back to the mood listing page
  }

  @GetMapping("/{id}/edit")
  public String showEditForm(@PathVariable Long id, Model model, Authentication authentication) {
    Mood mood = ownedMoodOrNotFound(id, authentication);
    model.addAttribute("mood", mood);
    return "mood-edit";
  }

  // Editing only touches what a user would plausibly want to fix (mood text,
  // rating, notes) - date and weather stay as they were when the mood was
  // originally logged, since they describe that moment, not the edit.
  @PostMapping("/{id}/edit")
  public String updateMood(
      @PathVariable Long id,
      @RequestParam("mood") String moodText,
      @RequestParam("moodRating") Integer moodRating,
      @RequestParam(value = "notes", required = false) String notes,
      Authentication authentication,
      RedirectAttributes redirectAttributes) {
    Mood mood = ownedMoodOrNotFound(id, authentication);
    mood.setMood(moodText);
    mood.setMoodRating(moodRating);
    mood.setNotes(notes);
    moodRepository.save(mood);

    redirectAttributes.addFlashAttribute("moodUpdated", true);
    return "redirect:/moodtracker";
  }

  @PostMapping("/{id}/delete")
  public String deleteMood(
      @PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
    Mood mood = ownedMoodOrNotFound(id, authentication);
    moodRepository.delete(mood);

    redirectAttributes.addFlashAttribute("moodDeleted", true);
    return "redirect:/moodtracker";
  }

  private Mood ownedMoodOrNotFound(Long id, Authentication authentication) {
    User user = currentUser(authentication);
    return moodRepository
        .findByIdAndUser(id, user)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mood not found"));
  }

  private User currentUser(Authentication authentication) {
    String username = authentication.getName();
    return userRepository
        .findByUsername(username)
        .orElseThrow(() -> new RuntimeException("User not found: " + username));
  }

  // Falls back to the server's zone when the user hasn't set one - the only
  // behavior available before User.timeZone existed, so nothing regresses for
  // users who haven't opted in. SettingsController already validates the stored
  // value, but a defensive fallback here means a bad value can never break the
  // mood list rather than just failing more gracefully at save time.
  private ZoneId resolveZone(User user) {
    String timeZone = user.getTimeZone();
    if (timeZone == null || timeZone.isBlank()) {
      return ZoneId.systemDefault();
    }
    try {
      return ZoneId.of(timeZone);
    } catch (DateTimeException e) {
      log.warn(
          "Invalid time zone \"{}\" for user \"{}\"; falling back to server default",
          timeZone,
          user.getUsername());
      return ZoneId.systemDefault();
    }
  }

  // Compares this week's average mood rating to the prior week's, fetching only the
  // last 14 days rather than the user's entire history.
  private WeeklySummary computeWeeklySummary(User user) {
    Instant now = Instant.now();
    Instant oneWeekAgo = now.minus(7, ChronoUnit.DAYS);
    Instant twoWeeksAgo = now.minus(14, ChronoUnit.DAYS);

    List<Mood> recent = moodRepository.findByUserAndDateAfterOrderByDateDesc(user, twoWeeksAgo);
    List<Mood> thisWeek = recent.stream().filter(m -> m.getDate().isAfter(oneWeekAgo)).toList();
    List<Mood> lastWeek = recent.stream().filter(m -> !m.getDate().isAfter(oneWeekAgo)).toList();

    return new WeeklySummary(thisWeek.size(), averageRating(thisWeek), averageRating(lastWeek));
  }

  private Double averageRating(List<Mood> moods) {
    return moods.isEmpty()
        ? null
        : moods.stream().mapToInt(Mood::getMoodRating).average().orElseThrow();
  }

  // Weather is a nice-to-have on top of a logged mood, not a requirement for logging
  // one - if the user hasn't set a location, or the lookup fails, the mood still saves.
  private Weather fetchWeatherForUser(User user) {
    String location = user.getLocation();
    if (location == null || location.isBlank()) {
      return null;
    }
    return weatherService
        .fetchWeather(location)
        .onErrorResume(
            e -> {
              log.warn("Could not fetch weather for location \"{}\": {}", location, e.getMessage());
              return Mono.empty();
            })
        .block();
  }
}
