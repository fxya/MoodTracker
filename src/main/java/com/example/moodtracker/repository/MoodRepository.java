package com.example.moodtracker.repository;

import com.example.moodtracker.model.Mood;
import com.example.moodtracker.model.User; // Import User
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List; // Import List
import java.util.Optional;

public interface MoodRepository extends JpaRepository<Mood, Long> {
    List<Mood> findByUserOrderByDateDesc(User user); // New method

    // Ownership-scoped lookup so a user can only ever edit/delete their own moods.
    Optional<Mood> findByIdAndUser(Long id, User user);
}
