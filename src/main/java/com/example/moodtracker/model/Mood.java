package com.example.moodtracker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Data
@NoArgsConstructor
// @AllArgsConstructor // Removed to define a custom constructor as per requirements
public class Mood {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String mood;
    private Instant date;
    private Integer moodRating;
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    @OneToOne(cascade = CascadeType.ALL)
    private Weather weather;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id") // This specifies the foreign key column in the 'mood' table
    private User user;

    public Mood(String mood, Instant date, Integer moodRating, User user, Weather weather, String notes) {
        this.mood = mood;
        this.date = date;
        this.moodRating = moodRating;
        this.user = user;
        this.weather = weather;
        this.notes = notes;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
