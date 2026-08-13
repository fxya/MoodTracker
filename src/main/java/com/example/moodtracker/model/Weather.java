package com.example.moodtracker.model;

import jakarta.persistence.*;
import lombok.Data;

/* Only the fields we actually use are persisted: current temperature and
precipitation at the time a mood was logged. */
@Entity
@Data
public class Weather {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private Long id;

  // Explicit column names: Hibernate's default naming strategy doesn't insert an
  // underscore before a trailing single capital letter (temperatureC -> temperaturec).
  @Column(name = "temperature_c")
  private Double temperatureC;

  @Column(name = "precipitation_mm")
  private Double precipitationMm;
}
