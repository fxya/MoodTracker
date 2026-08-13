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
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

  // Exports the user's full mood history regardless of the current search/filter/
  // page - a partial export would be a surprising and unhelpful default for "give
  // me my data".
  @GetMapping(value = "/export/csv", produces = "text/csv")
  public ResponseEntity<String> exportCsv(Authentication authentication) {
    User user = currentUser(authentication);
    List<Mood> moods = moodRepository.findByUserOrderByDateDesc(user);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("moods.csv").build().toString())
        .contentType(MediaType.valueOf("text/csv;charset=UTF-8"))
        .body(toCsv(moods));
  }

  @GetMapping(value = "/export/json", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Mood>> exportJson(Authentication authentication) {
    User user = currentUser(authentication);
    List<Mood> moods = moodRepository.findByUserOrderByDateDesc(user);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("moods.json").build().toString())
        .body(moods);
  }

  private static String toCsv(List<Mood> moods) {
    StringBuilder csv =
        new StringBuilder("Date,Mood,Rating,Tag,Notes,TemperatureC,PrecipitationMm\r\n");
    for (Mood mood : moods) {
      Weather weather = mood.getWeather();
      csv.append(csvField(mood.getDate() == null ? null : mood.getDate().toString())).append(',');
      csv.append(csvField(mood.getMood())).append(',');
      csv.append(csvField(mood.getMoodRating() == null ? null : mood.getMoodRating().toString()))
          .append(',');
      csv.append(csvField(mood.getMoodTag())).append(',');
      csv.append(csvField(mood.getNotes())).append(',');
      csv.append(
              csvField(
                  weather == null || weather.getTemperatureC() == null
                      ? null
                      : weather.getTemperatureC().toString()))
          .append(',');
      csv.append(
          csvField(
              weather == null || weather.getPrecipitationMm() == null
                  ? null
                  : weather.getPrecipitationMm().toString()));
      csv.append("\r\n");
    }
    return csv.toString();
  }

  // RFC 4180: a field is quoted if it contains a comma, quote, or line break, with
  // any embedded quotes doubled up.
  private static String csvField(String value) {
    if (value == null) {
      return "";
    }
    boolean needsQuoting =
        value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
    String escaped = value.replace("\"", "\"\"");
    return needsQuoting ? "\"" + escaped + "\"" : escaped;
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
    // The tag <select>'s unselected option submits an empty string, not absent -
    // normalize that to null so "no tag" reads the same as never having set one.
    if (mood.getMoodTag() != null && mood.getMoodTag().isBlank()) {
      mood.setMoodTag(null);
    }
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
  // rating, notes, tag) - date stays as it was when the mood was originally
  // logged, since it describes that moment, not the edit. Weather is the one
  // exception: it's user-editable (see fields below) since the auto-fetched
  // value can simply be wrong (e.g. logged while traveling).
  @PostMapping("/{id}/edit")
  public String updateMood(
      @PathVariable Long id,
      @RequestParam("mood") String moodText,
      @RequestParam("moodRating") Integer moodRating,
      @RequestParam(value = "notes", required = false) String notes,
      @RequestParam(value = "moodTag", required = false) String moodTag,
      @RequestParam(value = "temperatureC", required = false) Double temperatureC,
      @RequestParam(value = "precipitationMm", required = false) Double precipitationMm,
      Authentication authentication,
      RedirectAttributes redirectAttributes) {
    Mood mood = ownedMoodOrNotFound(id, authentication);
    mood.setMood(moodText);
    mood.setMoodRating(moodRating);
    mood.setNotes(notes);
    mood.setMoodTag(moodTag == null || moodTag.isBlank() ? null : moodTag);
    applyWeatherEdit(mood, temperatureC, precipitationMm);
    moodRepository.save(mood);

    redirectAttributes.addFlashAttribute("moodUpdated", true);
    return "redirect:/moodtracker";
  }

  // A blank field means "leave unchanged", not "clear" - avoids ambiguity about
  // whether an empty input means "no weather" or "didn't touch this". Mutates the
  // existing Weather in place rather than replacing mood.weather with a new
  // instance: the @OneToOne has no orphanRemoval, so swapping the reference would
  // leave the old row orphaned. Only creates a Weather if the mood didn't have one
  // and the user actually entered a value.
  private void applyWeatherEdit(Mood mood, Double temperatureC, Double precipitationMm) {
    if (temperatureC == null && precipitationMm == null) {
      return;
    }
    Weather weather = mood.getWeather();
    if (weather == null) {
      weather = new Weather();
      mood.setWeather(weather);
    }
    if (temperatureC != null) {
      weather.setTemperatureC(temperatureC);
    }
    if (precipitationMm != null) {
      weather.setPrecipitationMm(precipitationMm);
    }
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

    return new WeeklySummary(
        thisWeek.size(), averageRating(thisWeek), averageRating(lastWeek), topTag(thisWeek));
  }

  private Double averageRating(List<Mood> moods) {
    return moods.isEmpty()
        ? null
        : moods.stream().mapToInt(Mood::getMoodRating).average().orElseThrow();
  }

  // Most frequent tag this week, ignoring untagged entries - null if no mood this
  // week had a tag set, or ties are broken arbitrarily (Map.Entry.comparingByValue
  // doesn't guarantee which of an equal-count pair wins, which is fine for a
  // lightweight stat-card callout).
  private String topTag(List<Mood> moods) {
    return moods.stream()
        .map(Mood::getMoodTag)
        .filter(tag -> tag != null && !tag.isBlank())
        .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()))
        .entrySet()
        .stream()
        .max(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey)
        .orElse(null);
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
