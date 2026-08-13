package com.example.moodtracker;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

  public void addViewControllers(ViewControllerRegistry registry) {
    registry.addViewController("/home").setViewName("home");
    registry.addViewController("/").setViewName("home");
    // /moodtracker is deliberately NOT registered here - MoodController owns that
    // path and needs its @GetMapping to populate the model (moods, weeklySummary,
    // etc.); a bare view-controller registration would render the template with
    // none of that and NPE.
    registry.addViewController("/moods").setViewName("moods");
    registry.addViewController("/login").setViewName("login");
  }
}
