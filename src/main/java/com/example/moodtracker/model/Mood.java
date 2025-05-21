package com.example.moodtracker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Mood {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String mood;
    private Instant date;
    private Integer moodRating;
    @OneToOne(cascade = CascadeType.ALL)
    private Weather weather;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id") // This specifies the foreign key column in the 'mood' table
    private User user;
}
