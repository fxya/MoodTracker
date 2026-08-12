package com.example.moodtracker.controller;

import com.example.moodtracker.model.User;
import com.example.moodtracker.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
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

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public String showSettings(Model model, Authentication authentication) {
        User user = currentUser(authentication);
        model.addAttribute("location", user.getLocation());
        return "settings";
    }

    @PostMapping
    public String updateSettings(@RequestParam(value = "location", required = false) String location,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        user.setLocation(location == null || location.isBlank() ? null : location.trim());
        userRepository.save(user);

        redirectAttributes.addFlashAttribute("settingsSaved", true);
        return "redirect:/settings";
    }

    private User currentUser(Authentication authentication) {
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }
}
