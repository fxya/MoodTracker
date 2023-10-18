package com.example.moodtracker.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.Instant;

@Entity
public class Mood {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String mood;
    private Instant date;

    public Mood() {}

    public Mood(String mood, Instant date) {
        this.mood = mood;
        this.date = date;
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

}
