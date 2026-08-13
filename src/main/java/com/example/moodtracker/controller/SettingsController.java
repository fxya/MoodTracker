package com.example.moodtracker.controller;

import com.example.moodtracker.model.Mood;
import com.example.moodtracker.model.User;
import com.example.moodtracker.model.Weather;
import com.example.moodtracker.repository.MoodRepository;
import com.example.moodtracker.repository.UserRepository;
import com.example.moodtracker.service.WeatherService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import reactor.core.publisher.Mono;

@Controller
@RequestMapping("/settings")
public class SettingsController {

  private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final MoodRepository moodRepository;
  private final WeatherService weatherService;

  public SettingsController(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      MoodRepository moodRepository,
      WeatherService weatherService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.moodRepository = moodRepository;
    this.weatherService = weatherService;
  }

  @GetMapping
  public String showSettings(Model model, Authentication authentication) {
    User user = currentUser(authentication);
    model.addAttribute("location", user.getLocation());
    model.addAttribute("timeZone", user.getTimeZone());
    model.addAttribute("availableTimeZones", sortedTimeZoneIds());
    model.addAttribute("username", user.getUsername());
    return "settings";
  }

  @PostMapping
  public String updateSettings(
      @RequestParam(value = "location", required = false) String location,
      @RequestParam(value = "timeZone", required = false) String timeZone,
      Authentication authentication,
      RedirectAttributes redirectAttributes) {
    User user = currentUser(authentication);
    user.setLocation(location == null || location.isBlank() ? null : location.trim());

    // A blank selection means "use the server's default zone" (the previous,
    // only behavior) - anything else must be a real IANA zone id, since it
    // drives date/time formatting elsewhere and a garbage value would blow up
    // at render time instead of at save time.
    if (timeZone == null || timeZone.isBlank()) {
      user.setTimeZone(null);
    } else if (isValidTimeZone(timeZone)) {
      user.setTimeZone(timeZone);
    } else {
      redirectAttributes.addFlashAttribute("settingsError", "Unrecognized time zone.");
      return "redirect:/settings";
    }
    userRepository.save(user);

    redirectAttributes.addFlashAttribute("settingsSaved", true);
    return "redirect:/settings";
  }

  // Lets a user retroactively fill in weather for moods logged before they set a
  // location (or where the lookup failed at the time). The location is validated
  // once up front so a bad/misspelled location fails fast with one clear error,
  // rather than every mood in the loop separately re-attempting - and re-timing-out
  // on - the same doomed geocode.
  @PostMapping("/backfill-weather")
  public String backfillWeather(
      Authentication authentication, RedirectAttributes redirectAttributes) {
    User user = currentUser(authentication);
    String location = user.getLocation();
    if (location == null || location.isBlank()) {
      redirectAttributes.addFlashAttribute(
          "backfillError", "Set a location first to backfill weather.");
      return "redirect:/settings";
    }

    List<Mood> moodsMissingWeather = moodRepository.findByUserAndWeatherIsNull(user);
    if (moodsMissingWeather.isEmpty()) {
      redirectAttributes.addFlashAttribute("backfillMessage", "No moods are missing weather.");
      return "redirect:/settings";
    }

    try {
      weatherService.validateLocation(location).block();
    } catch (RuntimeException e) {
      log.warn(
          "Could not validate location \"{}\" for weather backfill: {}", location, e.getMessage());
      redirectAttributes.addFlashAttribute(
          "backfillError",
          "Could not look up weather for \"" + location + "\". Check your location setting.");
      return "redirect:/settings";
    }

    // Sequential, not parallel: these moods share a single Hibernate session via
    // moodRepository, and concurrent saves against it would not be safe.
    ZoneId zone = resolveZone(user);
    int backfilled = 0;
    for (Mood mood : moodsMissingWeather) {
      LocalDate date = LocalDate.ofInstant(mood.getDate(), zone);
      Weather weather =
          weatherService
              .fetchHistoricalWeather(location, date)
              .onErrorResume(
                  e -> {
                    log.warn(
                        "Could not backfill weather for mood {} on {}: {}",
                        mood.getId(),
                        date,
                        e.getMessage());
                    return Mono.empty();
                  })
              .block();
      if (weather != null) {
        mood.setWeather(weather);
        moodRepository.save(mood);
        backfilled++;
      }
    }

    redirectAttributes.addFlashAttribute(
        "backfillMessage",
        "Backfilled weather for " + backfilled + " of " + moodsMissingWeather.size() + " mood(s).");
    return "redirect:/settings";
  }

  // Mirrors MoodController's fallback: the server's zone when the user hasn't set
  // one, so a past mood's Instant still maps to some sensible calendar date.
  private ZoneId resolveZone(User user) {
    String timeZone = user.getTimeZone();
    if (timeZone == null || timeZone.isBlank()) {
      return ZoneId.systemDefault();
    }
    try {
      return ZoneId.of(timeZone);
    } catch (DateTimeException e) {
      return ZoneId.systemDefault();
    }
  }

  private boolean isValidTimeZone(String timeZone) {
    try {
      ZoneId.of(timeZone);
      return true;
    } catch (DateTimeException e) {
      return false;
    }
  }

  private List<String> sortedTimeZoneIds() {
    return ZoneId.getAvailableZoneIds().stream().sorted().toList();
  }

  @PostMapping("/password")
  public String changePassword(
      @RequestParam("currentPassword") String currentPassword,
      @RequestParam("newPassword") String newPassword,
      @RequestParam("confirmPassword") String confirmPassword,
      Authentication authentication,
      RedirectAttributes redirectAttributes) {
    User user = currentUser(authentication);

    if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
      redirectAttributes.addFlashAttribute("passwordError", "Current password is incorrect.");
      return "redirect:/settings";
    }
    if (newPassword == null || newPassword.isBlank()) {
      redirectAttributes.addFlashAttribute("passwordError", "New password cannot be blank.");
      return "redirect:/settings";
    }
    if (!newPassword.equals(confirmPassword)) {
      redirectAttributes.addFlashAttribute(
          "passwordError", "New password and confirmation do not match.");
      return "redirect:/settings";
    }

    user.setPassword(passwordEncoder.encode(newPassword));
    userRepository.save(user);

    redirectAttributes.addFlashAttribute("passwordChanged", true);
    return "redirect:/settings";
  }

  @PostMapping("/delete-account")
  public String deleteAccount(
      @RequestParam("password") String password,
      Authentication authentication,
      HttpServletRequest request,
      RedirectAttributes redirectAttributes) {
    User user = currentUser(authentication);

    if (!passwordEncoder.matches(password, user.getPassword())) {
      redirectAttributes.addFlashAttribute("deleteError", "Incorrect password.");
      return "redirect:/settings";
    }

    userRepository.delete(user); // Cascades to the user's moods via User.moods' CascadeType.ALL

    request.getSession().invalidate();
    SecurityContextHolder.clearContext();

    // A query param, not a flash attribute: the session backing flash attributes
    // is the one we just invalidated, so it can't reliably carry a message across
    // this particular redirect (the same reason logout's message uses ?logout).
    return "redirect:/login?accountDeleted";
  }

  private User currentUser(Authentication authentication) {
    String username = authentication.getName();
    return userRepository
        .findByUsername(username)
        .orElseThrow(() -> new RuntimeException("User not found: " + username));
  }
}
