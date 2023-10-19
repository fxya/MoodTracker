package com.example.moodtracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

/* Nested classes used to represent structure of nested JSON object returned from
weather API for proper deserialisation */

@Entity
public class Weather {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    @Embedded
    private Location location;
    @Embedded
    private Current current;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public Current getCurrent() {
        return current;
    }

    public void setCurrent(Current current) {
        this.current = current;
    }

    @Embeddable
    public static class Location {
        private String name;
        // Add other fields as needed

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Embeddable
    public static class Current {
        @JsonProperty("temp_c")
        private Double tempC;
        // Add other fields as needed

        // Getters and Setters
        public Double getTempC() {
            return tempC;
        }

        public void setTempC(Double tempC) {
            this.tempC = tempC;
        }
    }
}
