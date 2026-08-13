package com.example.moodtracker.controller;

import com.example.moodtracker.model.User;
import com.example.moodtracker.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
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

@Controller
@RequestMapping("/settings")
public class SettingsController {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public SettingsController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
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
