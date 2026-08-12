package com.example.moodtracker.repository;

import com.example.moodtracker.model.Mood;
import com.example.moodtracker.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises MoodRepository against a real PostgreSQL instance (like the rest of the
 * suite - no embedded database is on the classpath) rather than Mockito stubs, so it
 * actually runs the JPQL in search() through Postgres. This is the test that would
 * have caught the "function lower(bytea) does not exist" regression before it shipped:
 * that bug only appeared once a non-null search term hit the real database, which no
 * Mockito-based controller test can reproduce.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MoodRepositoryTest {

    @Autowired
    private MoodRepository moodRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;
    private User otherUser;

    @BeforeEach
    void setUp() {
        user = userRepository.save(newUser());
        otherUser = userRepository.save(newUser());
    }

    private User newUser() {
        User u = new User();
        u.setUsername("repo_test_" + UUID.randomUUID());
        u.setPassword("irrelevant");
        return u;
    }

    private Mood saveMood(User owner, String moodText, Integer rating, String notes, Instant date) {
        Mood mood = new Mood();
        mood.setUser(owner);
        mood.setMood(moodText);
        mood.setMoodRating(rating);
        mood.setNotes(notes);
        mood.setDate(date);
        return moodRepository.save(mood);
    }

    @Test
    void search_withNoFilters_returnsAllOfTheUsersMoods() {
        saveMood(user, "Happy", 8, "First", Instant.now());
        saveMood(user, "Sad", 3, "Second", Instant.now());
        saveMood(otherUser, "Excited", 9, "Not this user's", Instant.now());

        Page<Mood> page = moodRepository.search(user, null, null, null, PageRequest.of(0, 10));

        assertEquals(2, page.getTotalElements());
        assertTrue(page.getContent().stream().allMatch(m -> m.getUser().getId().equals(user.getId())));
    }

    @Test
    void search_withTextQuery_matchesAgainstRealPostgres() {
        // Regression test for "function lower(bytea) does not exist" - this only
        // reproduced with a real, non-null search term against actual PostgreSQL,
        // not against a mock.
        saveMood(user, "Happy", 8, "Shipped a feature", Instant.now());
        saveMood(user, "Sad", 3, "Rainy commute", Instant.now());

        Page<Mood> page = moodRepository.search(user, "rain", null, null, PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("Sad", page.getContent().get(0).getMood());
    }

    @Test
    void search_textQuery_matchesMoodFieldCaseInsensitively() {
        saveMood(user, "HAPPY", 8, null, Instant.now());
        saveMood(user, "Sad", 3, null, Instant.now());

        Page<Mood> page = moodRepository.search(user, "happy", null, null, PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("HAPPY", page.getContent().get(0).getMood());
    }

    @Test
    void search_textQuery_withNoMatches_returnsEmptyPage() {
        saveMood(user, "Happy", 8, "Nothing relevant", Instant.now());

        Page<Mood> page = moodRepository.search(user, "zzznomatch", null, null, PageRequest.of(0, 10));

        assertTrue(page.getContent().isEmpty());
        assertEquals(0, page.getTotalElements());
    }

    @Test
    void search_withMinRating_excludesLowerRatedMoods() {
        saveMood(user, "Great", 9, null, Instant.now());
        saveMood(user, "Meh", 5, null, Instant.now());

        Page<Mood> page = moodRepository.search(user, null, 8, null, PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("Great", page.getContent().get(0).getMood());
    }

    @Test
    void search_withMaxRating_excludesHigherRatedMoods() {
        saveMood(user, "Great", 9, null, Instant.now());
        saveMood(user, "Meh", 5, null, Instant.now());

        Page<Mood> page = moodRepository.search(user, null, null, 6, PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("Meh", page.getContent().get(0).getMood());
    }

    @Test
    void search_combinesTextAndRatingFilters() {
        saveMood(user, "Happy", 9, "Great day", Instant.now());
        saveMood(user, "Happy", 3, "Not so great day", Instant.now());

        Page<Mood> page = moodRepository.search(user, "happy", 8, null, PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals(9, page.getContent().get(0).getMoodRating());
    }

    @Test
    void search_resultsAreOrderedNewestFirst() {
        Instant now = Instant.now();
        saveMood(user, "Oldest", 5, null, now.minus(2, ChronoUnit.DAYS));
        saveMood(user, "Newest", 5, null, now);
        saveMood(user, "Middle", 5, null, now.minus(1, ChronoUnit.DAYS));

        Page<Mood> page = moodRepository.search(user, null, null, null, PageRequest.of(0, 10));

        assertEquals(List.of("Newest", "Middle", "Oldest"),
                page.getContent().stream().map(Mood::getMood).toList());
    }

    @Test
    void search_paginatesResults() {
        Instant now = Instant.now();
        for (int i = 0; i < 7; i++) {
            saveMood(user, "Mood " + i, 5, null, now.minus(i, ChronoUnit.DAYS));
        }

        Page<Mood> firstPage = moodRepository.search(user, null, null, null, PageRequest.of(0, 5));
        Page<Mood> secondPage = moodRepository.search(user, null, null, null, PageRequest.of(1, 5));

        assertEquals(7, firstPage.getTotalElements());
        assertEquals(2, firstPage.getTotalPages());
        assertEquals(5, firstPage.getContent().size());
        assertEquals(2, secondPage.getContent().size());
    }

    @Test
    void findByIdAndUser_returnsMoodWhenOwnedByUser() {
        Mood mood = saveMood(user, "Happy", 8, null, Instant.now());

        Optional<Mood> found = moodRepository.findByIdAndUser(mood.getId(), user);

        assertTrue(found.isPresent());
        assertEquals(mood.getId(), found.get().getId());
    }

    @Test
    void findByIdAndUser_returnsEmptyWhenOwnedByAnotherUser() {
        Mood mood = saveMood(otherUser, "Happy", 8, null, Instant.now());

        Optional<Mood> found = moodRepository.findByIdAndUser(mood.getId(), user);

        assertFalse(found.isPresent());
    }

    @Test
    void findByUserOrderByDateDesc_returnsOnlyThatUsersMoodsNewestFirst() {
        Instant now = Instant.now();
        saveMood(user, "Older", 5, null, now.minus(1, ChronoUnit.DAYS));
        saveMood(user, "Newer", 5, null, now);
        saveMood(otherUser, "Not mine", 5, null, now);

        List<Mood> moods = moodRepository.findByUserOrderByDateDesc(user);

        assertEquals(List.of("Newer", "Older"), moods.stream().map(Mood::getMood).toList());
    }

    @Test
    void findByUserAndDateAfterOrderByDateDesc_excludesMoodsBeforeCutoff() {
        Instant now = Instant.now();
        saveMood(user, "Recent", 5, null, now.minus(1, ChronoUnit.DAYS));
        saveMood(user, "TooOld", 5, null, now.minus(20, ChronoUnit.DAYS));

        List<Mood> moods = moodRepository.findByUserAndDateAfterOrderByDateDesc(user, now.minus(7, ChronoUnit.DAYS));

        assertEquals(List.of("Recent"), moods.stream().map(Mood::getMood).toList());
    }
}
