package com.example.moodtracker.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Weather {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @JsonProperty("location.name")
    private String location;

    @JsonProperty("current.temp_c")
    private String tempC;

    @JsonProperty("forecast.daily.will_it_rain")
    private String dailyWillItRain;

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public String getLocation() {
        return location;
    }

    public String getTempC() {
        return tempC;
    }

    public String getDailyWillItRain() {
        return dailyWillItRain;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void getTempC(String tempC) {
        this.tempC = tempC;
    }

    public void setDailyWillItRain(String dailyWillItRain) {
        this.dailyWillItRain = dailyWillItRain;
    }
}
