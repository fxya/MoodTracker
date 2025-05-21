package com.example.moodtracker.controller;

import com.example.moodtracker.model.User;
import com.example.moodtracker.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes; // For flash messages

import jakarta.validation.Valid; // For basic validation if you add annotations to User

@Controller
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        if (!model.containsAttribute("user")) { // Keep existing user object if redirected from POST
            model.addAttribute("user", new User());
        }
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@Valid @ModelAttribute("user") User user,
                               BindingResult bindingResult,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            // If validation errors (e.g. @NotNull on User fields), return to form
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.user", bindingResult);
            redirectAttributes.addFlashAttribute("user", user);
            return "redirect:/register";
        }

        // Check if username already exists
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            bindingResult.rejectValue("username", "user.username.exists", "Username already taken. Please choose another.");
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.user", bindingResult);
            redirectAttributes.addFlashAttribute("user", user);
            return "redirect:/register"; // Redirect back to show the error
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        // Set default roles if applicable, e.g., user.setRoles(Collections.singleton("ROLE_USER"));
        userRepository.save(user);

        redirectAttributes.addFlashAttribute("registrationSuccess", "Registration successful! Please login.");
        return "redirect:/login";
    }
}
