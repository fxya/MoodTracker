package com.example.moodtracker.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

// Deliberately thin: builds and sends the reset email, nothing else. Send
// failures (e.g. no SMTP configured) propagate as MailException - the caller
// (PasswordResetController) decides how to handle that, the same division of
// responsibility as WeatherService/SettingsController for weather lookups.
@Service
public class PasswordResetService {

  private final JavaMailSender mailSender;
  private final String fromAddress;

  public PasswordResetService(
      JavaMailSender mailSender, @Value("${app.mail.from}") String fromAddress) {
    this.mailSender = mailSender;
    this.fromAddress = fromAddress;
  }

  public void sendResetEmail(String toEmail, String resetUrl) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(fromAddress);
    message.setTo(toEmail);
    message.setSubject("MoodTracker password reset");
    message.setText(
        "We received a request to reset your MoodTracker password.\n\n"
            + "Reset it here: "
            + resetUrl
            + "\n\n"
            + "This link expires in 30 minutes. If you didn't request this, you can "
            + "safely ignore this email.");
    mailSender.send(message);
  }
}
