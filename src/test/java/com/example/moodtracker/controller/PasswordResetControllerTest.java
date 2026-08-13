package com.example.moodtracker.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.example.moodtracker.model.User;
import com.example.moodtracker.repository.UserRepository;
import com.example.moodtracker.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ExtendWith(MockitoExtension.class)
class PasswordResetControllerTest {

  @Mock private UserRepository userRepository;

  @Mock private PasswordEncoder passwordEncoder;

  @Mock private PasswordResetService passwordResetService;

  @Mock private HttpServletRequest request;

  @Mock private RedirectAttributes redirectAttributes;

  @Mock private Model model;

  @InjectMocks private PasswordResetController controller;

  private User user;

  @BeforeEach
  void setUp() {
    user = new User();
    user.setId(1L);
    user.setUsername("testuser");
    user.setEmail("testuser@example.com");
    user.setPassword("encoded-old-password");
  }

  private void stubRequestUrlComponents() {
    when(request.getScheme()).thenReturn("http");
    when(request.getServerName()).thenReturn("localhost");
    when(request.getServerPort()).thenReturn(8080);
    when(request.getContextPath()).thenReturn("");
  }

  @Test
  void requestReset_userWithEmail_sendsEmailAndShowsGenericMessage() {
    when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
    stubRequestUrlComponents();

    String viewName = controller.requestReset("testuser", request, redirectAttributes);

    assertEquals("redirect:/forgot-password", viewName);
    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    assertNotNull(userCaptor.getValue().getResetToken());
    assertTrue(userCaptor.getValue().getResetTokenExpiry().isAfter(Instant.now()));
    verify(passwordResetService).sendResetEmail(eq("testuser@example.com"), anyString());
    verify(redirectAttributes).addFlashAttribute(eq("requestMessage"), anyString());
  }

  @Test
  void requestReset_unknownUsername_sendsNothingButShowsSameGenericMessage() {
    when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

    String viewName = controller.requestReset("ghost", request, redirectAttributes);

    assertEquals("redirect:/forgot-password", viewName);
    verify(userRepository, never()).save(any());
    verify(passwordResetService, never()).sendResetEmail(anyString(), anyString());
    verify(redirectAttributes).addFlashAttribute(eq("requestMessage"), anyString());
  }

  @Test
  void requestReset_userWithNoEmailOnFile_sendsNothingButShowsSameGenericMessage() {
    user.setEmail(null);
    when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

    String viewName = controller.requestReset("testuser", request, redirectAttributes);

    assertEquals("redirect:/forgot-password", viewName);
    verify(userRepository, never()).save(any());
    verify(passwordResetService, never()).sendResetEmail(anyString(), anyString());
    verify(redirectAttributes).addFlashAttribute(eq("requestMessage"), anyString());
  }

  @Test
  void requestReset_mailSendFailure_isSwallowedAndStillShowsGenericMessage() {
    when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
    stubRequestUrlComponents();
    doThrow(new MailSendException("SMTP not configured"))
        .when(passwordResetService)
        .sendResetEmail(anyString(), anyString());

    String viewName = controller.requestReset("testuser", request, redirectAttributes);

    assertEquals("redirect:/forgot-password", viewName);
    verify(redirectAttributes).addFlashAttribute(eq("requestMessage"), anyString());
  }

  @Test
  void showResetForm_validToken_setsTokenValidTrue() {
    user.setResetToken("valid-token");
    user.setResetTokenExpiry(Instant.now().plus(10, ChronoUnit.MINUTES));
    when(userRepository.findByResetToken("valid-token")).thenReturn(Optional.of(user));

    String viewName = controller.showResetForm("valid-token", model);

    assertEquals("reset-password", viewName);
    verify(model).addAttribute("tokenValid", true);
    verify(model).addAttribute("token", "valid-token");
  }

  @Test
  void showResetForm_expiredToken_setsTokenValidFalse() {
    user.setResetToken("expired-token");
    user.setResetTokenExpiry(Instant.now().minus(1, ChronoUnit.MINUTES));
    when(userRepository.findByResetToken("expired-token")).thenReturn(Optional.of(user));

    String viewName = controller.showResetForm("expired-token", model);

    assertEquals("reset-password", viewName);
    verify(model).addAttribute("tokenValid", false);
  }

  @Test
  void showResetForm_unknownToken_setsTokenValidFalse() {
    when(userRepository.findByResetToken("bogus")).thenReturn(Optional.empty());

    controller.showResetForm("bogus", model);

    verify(model).addAttribute("tokenValid", false);
  }

  @Test
  void resetPassword_validTokenAndMatchingPasswords_updatesAndClearsToken() {
    user.setResetToken("valid-token");
    user.setResetTokenExpiry(Instant.now().plus(10, ChronoUnit.MINUTES));
    when(userRepository.findByResetToken("valid-token")).thenReturn(Optional.of(user));
    when(passwordEncoder.encode("newpass")).thenReturn("encoded-new-password");

    String viewName =
        controller.resetPassword("valid-token", "newpass", "newpass", redirectAttributes);

    assertEquals("redirect:/login", viewName);
    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    assertEquals("encoded-new-password", userCaptor.getValue().getPassword());
    assertNull(userCaptor.getValue().getResetToken());
    assertNull(userCaptor.getValue().getResetTokenExpiry());
    verify(redirectAttributes).addFlashAttribute(eq("resetSuccess"), anyString());
  }

  @Test
  void resetPassword_expiredToken_redirectsToForgotPasswordWithoutSaving() {
    user.setResetToken("expired-token");
    user.setResetTokenExpiry(Instant.now().minus(1, ChronoUnit.MINUTES));
    when(userRepository.findByResetToken("expired-token")).thenReturn(Optional.of(user));

    String viewName =
        controller.resetPassword("expired-token", "newpass", "newpass", redirectAttributes);

    assertEquals("redirect:/forgot-password", viewName);
    verify(userRepository, never()).save(any());
    verify(redirectAttributes).addFlashAttribute(eq("requestError"), anyString());
  }

  @Test
  void resetPassword_mismatchedConfirmation_redirectsBackToResetFormWithoutSaving() {
    user.setResetToken("valid-token");
    user.setResetTokenExpiry(Instant.now().plus(10, ChronoUnit.MINUTES));
    when(userRepository.findByResetToken("valid-token")).thenReturn(Optional.of(user));

    String viewName =
        controller.resetPassword("valid-token", "newpass", "different", redirectAttributes);

    assertEquals("redirect:/reset-password?token=valid-token", viewName);
    verify(userRepository, never()).save(any());
    verify(redirectAttributes).addFlashAttribute(eq("resetError"), anyString());
  }

  @Test
  void resetPassword_blankNewPassword_redirectsBackToResetFormWithoutSaving() {
    user.setResetToken("valid-token");
    user.setResetTokenExpiry(Instant.now().plus(10, ChronoUnit.MINUTES));
    when(userRepository.findByResetToken("valid-token")).thenReturn(Optional.of(user));

    String viewName = controller.resetPassword("valid-token", "  ", "  ", redirectAttributes);

    assertEquals("redirect:/reset-password?token=valid-token", viewName);
    verify(userRepository, never()).save(any());
    verify(redirectAttributes).addFlashAttribute(eq("resetError"), anyString());
  }
}
