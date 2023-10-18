package com.example.moodtracker.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class MoodController {

    @GetMapping("/moods")
    public String getMoods() {
        return "moods";
    }

}