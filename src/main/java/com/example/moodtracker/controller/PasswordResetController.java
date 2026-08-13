package com.example.moodtracker.controller;

import com.example.moodtracker.model.User;
import com.example.moodtracker.repository.UserRepository;
import com.example.moodtracker.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// Unauthenticated by design (see WebSecurityConfig) - this is exactly how a locked-out
// user recovers access. The request endpoint deliberately never reveals whether a
// username exists or has an email on file: every outcome shows the same generic
// message, the same anti-enumeration posture the rest of the app already takes
// (rate limiting, no "username taken" hint anywhere but registration).
@Controller
public class PasswordResetController {

  private static final Logger log = LoggerFactory.getLogger(PasswordResetController.class);
  private static final Duration TOKEN_VALIDITY = Duration.ofMinutes(30);
  private static final String GENERIC_REQUEST_MESSAGE =
      "If that username exists and has an email on file, we've sent a password reset link.";

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordResetService passwordResetService;

  public PasswordResetController(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      PasswordResetService passwordResetService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.passwordResetService = passwordResetService;
  }

  @GetMapping("/forgot-password")
  public String showForgotPasswordForm() {
    return "forgot-password";
  }

  @PostMapping("/forgot-password")
  public String requestReset(
      @RequestParam("username") String username,
      HttpServletRequest request,
      RedirectAttributes redirectAttributes) {
    userRepository
        .findByUsername(username)
        .filter(user -> user.getEmail() != null && !user.getEmail().isBlank())
        .ifPresent(user -> issueResetToken(user, request));

    redirectAttributes.addFlashAttribute("requestMessage", GENERIC_REQUEST_MESSAGE);
    return "redirect:/forgot-password";
  }

  private void issueResetToken(User user, HttpServletRequest request) {
    String token = UUID.randomUUID().toString();
    user.setResetToken(token);
    user.setResetTokenExpiry(Instant.now().plus(TOKEN_VALIDITY));
    userRepository.save(user);

    String resetUrl =
        ServletUriComponentsBuilder.fromContextPath(request)
            .path("/reset-password")
            .queryParam("token", token)
            .toUriString();

    try {
      passwordResetService.sendResetEmail(user.getEmail(), resetUrl);
    } catch (MailException e) {
      // Best-effort, same as weather lookups elsewhere: a down/unconfigured SMTP
      // relay must never leak into the (always-generic) response.
      log.warn(
          "Failed to send password reset email to user \"{}\": {}",
          user.getUsername(),
          e.getMessage());
    }
  }

  @GetMapping("/reset-password")
  public String showResetForm(@RequestParam("token") String token, Model model) {
    model.addAttribute("tokenValid", validToken(token).isPresent());
    model.addAttribute("token", token);
    return "reset-password";
  }

  @PostMapping("/reset-password")
  public String resetPassword(
      @RequestParam("token") String token,
      @RequestParam("newPassword") String newPassword,
      @RequestParam("confirmPassword") String confirmPassword,
      RedirectAttributes redirectAttributes) {
    Optional<User> maybeUser = validToken(token);
    if (maybeUser.isEmpty()) {
      redirectAttributes.addFlashAttribute(
          "requestError", "That reset link is invalid or has expired. Please request a new one.");
      return "redirect:/forgot-password";
    }

    if (newPassword == null || newPassword.isBlank()) {
      redirectAttributes.addFlashAttribute("resetError", "New password cannot be blank.");
      return "redirect:/reset-password?token=" + token;
    }
    if (!newPassword.equals(confirmPassword)) {
      redirectAttributes.addFlashAttribute(
          "resetError", "New password and confirmation do not match.");
      return "redirect:/reset-password?token=" + token;
    }

    User user = maybeUser.get();
    user.setPassword(passwordEncoder.encode(newPassword));
    user.setResetToken(null);
    user.setResetTokenExpiry(null);
    userRepository.save(user);

    redirectAttributes.addFlashAttribute(
        "resetSuccess", "Your password has been reset. Please log in.");
    return "redirect:/login";
  }

  private Optional<User> validToken(String token) {
    return userRepository
        .findByResetToken(token)
        .filter(
            user ->
                user.getResetTokenExpiry() != null
                    && user.getResetTokenExpiry().isAfter(Instant.now()));
  }
}
