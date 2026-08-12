package com.example.moodtracker.controller;

import com.example.moodtracker.model.User;
import com.example.moodtracker.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping
    public String showSettings(Model model, Authentication authentication) {
        User user = currentUser(authentication);
        model.addAttribute("location", user.getLocation());
        model.addAttribute("username", user.getUsername());
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

    @PostMapping("/password")
    public String changePassword(@RequestParam("currentPassword") String currentPassword,
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
            redirectAttributes.addFlashAttribute("passwordError", "New password and confirmation do not match.");
            return "redirect:/settings";
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        redirectAttributes.addFlashAttribute("passwordChanged", true);
        return "redirect:/settings";
    }

    @PostMapping("/delete-account")
    public String deleteAccount(@RequestParam("password") String password,
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
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }
}
