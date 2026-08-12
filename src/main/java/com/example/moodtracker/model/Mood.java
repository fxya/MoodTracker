package com.example.moodtracker.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

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

    // Excluded from equals/hashCode/toString: see the matching note on User.moods -
    // both sides of this bidirectional relationship must break the cycle.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id") // This specifies the foreign key column in the 'mood' table
    @JsonIgnore // Avoid serializing the owning User (and its back-reference cycle) via /api/moods
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
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
