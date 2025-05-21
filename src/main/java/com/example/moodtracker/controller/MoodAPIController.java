package com.example.moodtracker.controller;

import com.example.moodtracker.model.Mood;
import com.example.moodtracker.model.Weather;
import com.example.moodtracker.repository.MoodRepository;
import com.example.moodtracker.service.WeatherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
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
    private final WeatherService weatherService;

    public MoodAPIController(@Autowired MoodRepository moodRepository,
                             @Autowired WeatherService weatherService) {
        this.moodRepository = moodRepository;
        this.weatherService = weatherService;
    }

    @GetMapping("/api/moods")
    public List<Mood> getAllMoods() {
        return moodRepository.findAll();
    }

    @PostMapping("/api/moods")
    public Mono<RedirectView> addMood(@RequestParam("moodEntry") String moodEntry,
                                      @RequestParam("clientCurrentDateTime") String currentDateString) {

        // Use java.time's DateTimeFormatter to parse the date string
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");
        Instant currentDate;
        String location = "London";

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
                    Mood mood = new Mood(null, moodEntry, currentDate, weatherData);
                    moodRepository.save(mood);

                    return Mono.just(new RedirectView("/moods"));
                });

    }

}
