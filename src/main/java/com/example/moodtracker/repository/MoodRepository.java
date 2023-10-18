package com.example.moodtracker.repository;

import com.example.moodtracker.model.Mood;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MoodRepository extends JpaRepository<Mood, Long> {
}
