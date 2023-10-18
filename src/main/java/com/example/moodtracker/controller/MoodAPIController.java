package com.example.moodtracker.controller;

import com.example.moodtracker.model.Mood;
import com.example.moodtracker.repository.MoodRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@RestController
public class MoodAPIController {

    private final MoodRepository moodRepository;

    public MoodAPIController(@Autowired MoodRepository moodRepository) {
        this.moodRepository = moodRepository;
    }

    @GetMapping("/api/moods")
    public List<Mood> getAllMoods() {
        return moodRepository.findAll();
    }

    @PostMapping("/api/moods")
    public RedirectView addMood(@RequestParam("moodEntry") String moodEntry,
                          @RequestParam("clientCurrentDateTime") String currentDateString) {

        // Convert the string date to a Date object. Adjust the format as needed.
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Date currentDate;

        try {
            currentDate = formatter.parse(currentDateString);
        } catch (Exception e) {
            // Handle date parsing error
            return new RedirectView("/error");
        }
        // Create a Mood object and save
        Mood mood = new Mood(moodEntry, currentDate);
        moodRepository.save(mood);
        return new RedirectView("/moods");
    }

}
