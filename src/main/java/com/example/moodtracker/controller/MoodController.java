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
        User user = currentUser(authentication);
        List<Mood> moods = moodRepository.findByUserOrderByDateDesc(user);

        model.addAttribute("moods", moods);
        model.addAttribute("newMood", new Mood()); // For the form
        // Potentially add other attributes like username to display on page
        model.addAttribute("username", user.getUsername());
        return "moodtracker"; // Name of the Thymeleaf template
    }

    // Handle submission of a new mood
    @PostMapping("/add")
    public String addMood(@ModelAttribute("newMood") Mood mood, Authentication authentication) {
        User user = currentUser(authentication);

        mood.setUser(user);
        mood.setDate(Instant.now());
        // Assuming mood.mood (String) and mood.moodRating (Integer) are set from the form
        mood.setWeather(fetchWeatherForUser(user));
        moodRepository.save(mood);

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
    public String updateMood(@PathVariable Long id,
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
    public String deleteMood(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        Mood mood = ownedMoodOrNotFound(id, authentication);
        moodRepository.delete(mood);

        redirectAttributes.addFlashAttribute("moodDeleted", true);
        return "redirect:/moodtracker";
    }

    private Mood ownedMoodOrNotFound(Long id, Authentication authentication) {
        User user = currentUser(authentication);
        return moodRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mood not found"));
    }

    private User currentUser(Authentication authentication) {
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
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
