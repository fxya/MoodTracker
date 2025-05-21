package com.example.moodtracker.config;

import com.example.moodtracker.model.User;
import com.example.moodtracker.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Check if the user already exists to avoid duplicates on restart
        if (userRepository.findByUsername("testuser").isEmpty()) {
            User testUser = new User();
            testUser.setUsername("testuser");
            testUser.setPassword(passwordEncoder.encode("password"));
            // Set roles if you have a role field in User entity e.g. testUser.setRole("ROLE_USER");
            userRepository.save(testUser);
            System.out.println("Created test user: testuser/password");
        }
    }
}
