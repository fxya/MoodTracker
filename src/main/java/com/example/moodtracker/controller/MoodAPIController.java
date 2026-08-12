package com.example.moodtracker.controller;

import com.example.moodtracker.model.Mood;
import com.example.moodtracker.model.User;
import com.example.moodtracker.model.Weather;
import com.example.moodtracker.repository.MoodRepository;
import com.example.moodtracker.repository.UserRepository;
import com.example.moodtracker.service.WeatherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.List;

@RestController
public class MoodAPIController {

    private final MoodRepository moodRepository;
    private final UserRepository userRepository;
    private final WeatherService weatherService;

    public MoodAPIController(@Autowired MoodRepository moodRepository,
                             @Autowired UserRepository userRepository,
                             @Autowired WeatherService weatherService) {
        this.moodRepository = moodRepository;
        this.userRepository = userRepository;
        this.weatherService = weatherService;
    }

    @GetMapping("/api/moods")
    public List<Mood> getAllMoods() {
        return moodRepository.findByUserOrderByDateDesc(currentUser());
    }

    @PostMapping("/api/moods")
    public Mono<RedirectView> addMood(@RequestParam("mood") String moodEntry,
                                      @RequestParam("moodRating") Integer moodRating,
                                      @RequestParam("clientCurrentDateTime") String currentDateString,
                                      @RequestParam("location") String location) {

        // Resolved synchronously, before the reactive/async chain below - the
        // security context is thread-local and won't be available once execution
        // moves off this request thread.
        User user = currentUser();

        // Use java.time's DateTimeFormatter to parse the date string
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");
        Instant currentDate;

        try {
            TemporalAccessor accessor = formatter.parse(currentDateString);
            currentDate = Instant.from(accessor);
        } catch (Exception e) {
            // Handle date parsing error
            return Mono.just(new RedirectView("/error"));
        }
        return weatherService.fetchWeather(location)
                .flatMap(weatherData -> {
                    // Create a Mood object and save
                    Mood mood = new Mood();
                    mood.setMood(moodEntry);
                    mood.setDate(currentDate);
                    mood.setMoodRating(moodRating);
                    mood.setWeather(weatherData);
                    mood.setUser(user);
                    moodRepository.save(mood);

                    return Mono.just(new RedirectView("/moods"));
                });

    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

}
