package com.example.moodtracker.repository;

import com.example.moodtracker.model.Mood;
import com.example.moodtracker.model.User; // Import User
import java.time.Instant;
import java.util.List; // Import List
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MoodRepository extends JpaRepository<Mood, Long> {
  List<Mood> findByUserOrderByDateDesc(User user); // New method

  // Ownership-scoped lookup so a user can only ever edit/delete their own moods.
  Optional<Mood> findByIdAndUser(Long id, User user);

  // Bounded lookback for the weekly summary - avoids pulling a user's entire history
  // just to compare the last two weeks.
  List<Mood> findByUserAndDateAfterOrderByDateDesc(User user, Instant since);

  // Moods logged before the user set a location (or where weather lookup failed at
  // the time) - the backfill-weather feature's candidate set.
  List<Mood> findByUserAndWeatherIsNull(User user);

  // Each filter is optional (a null parameter matches everything) so the mood
  // history page can search/filter/paginate with a single query.
  // :q is explicitly CAST to string - without it, PostgreSQL's JDBC driver can't
  // infer a type for the parameter inside LOWER(CONCAT(...)) when it's null, and
  // fails with "function lower(bytea) does not exist".
  @Query(
      """
      SELECT m FROM Mood m WHERE m.user = :user
      AND (:q IS NULL OR LOWER(m.mood) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR LOWER(m.notes) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
      AND (:minRating IS NULL OR m.moodRating >= :minRating)
      AND (:maxRating IS NULL OR m.moodRating <= :maxRating)
      ORDER BY m.date DESC
      """)
  Page<Mood> search(
      @Param("user") User user,
      @Param("q") String q,
      @Param("minRating") Integer minRating,
      @Param("maxRating") Integer maxRating,
      Pageable pageable);
}
