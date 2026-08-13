package com.example.moodtracker.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

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

  // Optional, quick-select category alongside the free-text `mood` field
  // above - lets moods be grouped/analyzed (e.g. "most common mood this
  // week") without parsing free text. Fixed vocabulary enforced client-side
  // via a <select>, not a separate entity/table - see moodtracker.html.
  private String moodTag;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @OneToOne(cascade = CascadeType.ALL)
  private Weather weather;

  // View-only: set by MoodController when rendering moodtracker.html, formatted in
  // the owning user's time zone rather than the server's. Not persisted, and left
  // out of the /api/moods JSON payload (the charts on /moods format dates
  // client-side in the browser's own zone, so they don't need this).
  @Transient @JsonIgnore private String formattedDate;

  // Excluded from equals/hashCode/toString: see the matching note on User.moods -
  // both sides of this bidirectional relationship must break the cycle.
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id") // This specifies the foreign key column in the 'mood' table
  @JsonIgnore // Avoid serializing the owning User (and its back-reference cycle) via /api/moods
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private User user;

  public Mood(
      String mood, Instant date, Integer moodRating, User user, Weather weather, String notes) {
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
