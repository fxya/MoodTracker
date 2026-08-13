package com.example.moodtracker.controller;

import com.example.moodtracker.model.Mood;
import com.example.moodtracker.model.User;
import com.example.moodtracker.repository.MoodRepository;
import com.example.moodtracker.repository.UserRepository;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
public class MoodAPIController {

  private final MoodRepository moodRepository;
  private final UserRepository userRepository;

  public MoodAPIController(MoodRepository moodRepository, UserRepository userRepository) {
    this.moodRepository = moodRepository;
    this.userRepository = userRepository;
  }

  // Backs the mood-rating trend, weather trend, and correlation charts on /moods -
  // fetches the full history rather than a paginated slice, since the charts plot
  // the whole timeline.
  @GetMapping("/api/moods")
  public List<Mood> getAllMoods() {
    return moodRepository.findByUserOrderByDateDesc(currentUser());
  }

  private User currentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    String username = authentication.getName();
    return userRepository
        .findByUsername(username)
        .orElseThrow(() -> new RuntimeException("User not found: " + username));
  }
}
