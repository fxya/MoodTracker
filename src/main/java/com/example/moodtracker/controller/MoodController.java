package com.example.moodtracker.controller;

import com.example.moodtracker.model.Mood;
import com.example.moodtracker.model.User;
import com.example.moodtracker.repository.MoodRepository;
import com.example.moodtracker.repository.UserRepository; // To fetch the current user
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping; // Added for base path

import java.time.Instant;
import java.util.List;

@Controller
@RequestMapping("/moodtracker") // Base path for mood related operations
public class MoodController {

    @Autowired
    private MoodRepository moodRepository;

    @Autowired
    private UserRepository userRepository; // To fetch the User entity

    // Display moods for the current user and a form to add a new mood
    @GetMapping
    public String getMoodsPage(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
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
        moodRepository.save(mood);

        return "redirect:/moodtracker"; // Redirect back to the mood listing page
    }
}