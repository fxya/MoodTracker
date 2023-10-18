package com.example.moodtracker.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
public class Mood {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String mood;
    private Instant date;
    @OneToOne(cascade = CascadeType.ALL)
    private Weather weather;

    public Mood() {}

    public Mood(String mood, Instant date, Weather weather) {
        this.mood = mood;
        this.date = date;
        this.weather = weather;
    }

    public Long getId() {
        return id;
    }

    public String getMood() {
        return mood;
    }

    public Instant getDate() {
        return date;
    }

    public void setMood(String mood) {
        this.mood = mood;
    }

    public void setDate(Instant date) {
        this.date = date;
    }

    public Weather getWeather() {
        return weather;
    }

    public void setWeather(Weather weather) {
        this.weather = weather;
    }

}
