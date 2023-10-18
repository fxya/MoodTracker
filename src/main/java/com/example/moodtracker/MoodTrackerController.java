package com.example.moodtracker;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/* This controller serves the HTML page that displays the index page. There is
a single endpoint, /moodtracker, that returns the index.html template. */

@Controller
class MoodTrackerController {

    @GetMapping("/moodtracker")
    public String getIndexPage() {
        return "index";
    }

}
