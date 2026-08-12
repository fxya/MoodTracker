package com.example.moodtracker.controller;

import com.example.moodtracker.model.Mood;
import com.example.moodtracker.model.User;
import com.example.moodtracker.model.Weather;
import com.example.moodtracker.repository.MoodRepository;
import com.example.moodtracker.repository.UserRepository; // To fetch the current user
import com.example.moodtracker.service.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping; // Added for base path
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Controller
@RequestMapping("/moodtracker") // Base path for mood related operations
public class MoodController {

    private static final Logger log = LoggerFactory.getLogger(MoodController.class);

    @Autowired
    private MoodRepository moodRepository;

    @Autowired
    private UserRepository userRepository; // To fetch the User entity

    @Autowired
    private WeatherService weatherService;

    // Display moods for the current user and a form to add a new mood
    @GetMapping
    public String getMoodsPage(Model model, Authentication authentication) {
        String currentUsername = authentication.getName();

        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new RuntimeException("User not found: " + currentUsername));

        List<Mood> moods = moodRepository.findByUserOrderByDateDesc(user); // Assumes this method exists or will be added

        model.addAttribute("moods", moods);
        model.addAttribute("newMood", new Mood()); // For the form
        // Potentially add other attributes like username to display on page
        model.addAttribute("username", currentUsername);
        return "moodtracker"; // Name of the Thymeleaf template
    }

    // Handle submission of a new mood
    @PostMapping("/add")
    public String addMood(@ModelAttribute("newMood") Mood mood, Authentication authentication) {
        String currentUsername = authentication.getName();
        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new RuntimeException("User not found: " + currentUsername));

        mood.setUser(user);
        mood.setDate(Instant.now());
        // Assuming mood.mood (String) and mood.moodRating (Integer) are set from the form
        mood.setWeather(fetchWeatherForUser(user));
        moodRepository.save(mood);

        return "redirect:/moodtracker"; // Redirect back to the mood listing page
    }

    // Weather is a nice-to-have on top of a logged mood, not a requirement for logging
    // one - if the user hasn't set a location, or the lookup fails, the mood still saves.
    private Weather fetchWeatherForUser(User user) {
        String location = user.getLocation();
        if (location == null || location.isBlank()) {
            return null;
        }
        return weatherService.fetchWeather(location)
                .onErrorResume(e -> {
                    log.warn("Could not fetch weather for location \"{}\": {}", location, e.getMessage());
                    return Mono.empty();
                })
                .block();
    }
}