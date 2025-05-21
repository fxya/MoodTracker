package com.example.moodtracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

/* Nested classes used to represent structure of nested JSON object returned from
weather API for proper deserialisation */

import lombok.Data;

@Entity
@Data
public class Weather {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    @Embedded
    private Location location;
    @Embedded
    private Current current;

    @Embeddable
    @Data
    public static class Location {
        private String name;
        // Add other fields as needed
    }

    @Embeddable
    @Data
    public static class Current {
        @JsonProperty("temp_c")
        private Double tempC;
        // Add other fields as needed
    }
}
