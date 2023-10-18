package com.example.moodtracker.controller;

import com.example.moodtracker.model.Mood;
import com.example.moodtracker.repository.MoodRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class MoodController {

    private final MoodRepository moodRepository;

    public MoodController(@Autowired MoodRepository moodRepository) {
        this.moodRepository = moodRepository;
    }

    @GetMapping("/moods")
    public List<Mood> getAllMoods() {
        return moodRepository.findAll();
    }

    @PostMapping("/moods")
    public Mood addMood(@RequestBody Mood mood) {
        return moodRepository.save(mood);
    }
}
