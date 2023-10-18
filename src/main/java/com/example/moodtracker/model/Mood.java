package com.example.moodtracker.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.util.Date;


@Entity
public class Mood {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String mood;
    private Date date;

    public Mood() {}

    public Mood(String mood, Date date) {
        this.mood = mood;
        this.date = date;
    }

    public Long getId() {
        return id;
    }

    public String getMood() {
        return mood;
    }

    public Date getDate() {
        return date;
    }

    public void setMood(String mood) {
        this.mood = mood;
    }

    public void setDate(Date date) {
        this.date = date;
    }

}
