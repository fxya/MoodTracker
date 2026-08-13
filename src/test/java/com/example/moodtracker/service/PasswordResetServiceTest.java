package com.example.moodtracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

  @Mock private JavaMailSender mailSender;

  @Test
  void sendResetEmail_buildsMessageWithToSubjectAndLink() {
    PasswordResetService service =
        new PasswordResetService(mailSender, "no-reply@moodtracker.local");

    service.sendResetEmail("user@example.com", "http://localhost:8080/reset-password?token=abc123");

    ArgumentCaptor<SimpleMailMessage> messageCaptor =
        ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(messageCaptor.capture());

    SimpleMailMessage sent = messageCaptor.getValue();
    assertEquals("no-reply@moodtracker.local", sent.getFrom());
    assertEquals(1, sent.getTo().length);
    assertEquals("user@example.com", sent.getTo()[0]);
    assertEquals("MoodTracker password reset", sent.getSubject());
    assertTrue(sent.getText().contains("http://localhost:8080/reset-password?token=abc123"));
  }
}
