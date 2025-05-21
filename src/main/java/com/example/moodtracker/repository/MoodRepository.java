package com.example.moodtracker.repository;

import com.example.moodtracker.model.Mood;
import com.example.moodtracker.model.User; // Import User
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List; // Import List

public interface MoodRepository extends JpaRepository<Mood, Long> {
    List<Mood> findByUserOrderByDateDesc(User user); // New method
}
