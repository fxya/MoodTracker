package com.example.moodtracker.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;

import java.util.Set;
import java.util.HashSet;

@Entity
@Table(name = "users") // Explicitly name the table to avoid conflicts with SQL keywords like "user"
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    @JsonIgnore // Never serialize the password hash if a User is ever exposed via JSON
    private String password;

    // For now, we'll keep roles simple. A Set<String> could be used for more complex role management.
    // private String role; // Example: "ROLE_USER"

    // Free-text location (e.g. "London") used to look up weather for each mood.
    private String location;

    // IANA zone id (e.g. "America/New_York"), used to render mood dates/times in the
    // user's own time zone instead of the server's. Null means "use the server's
    // default zone", which is what every user got before this field existed.
    private String timeZone;

    // Excluded from equals/hashCode/toString: Mood has a back-reference to User, and
    // Lombok's @Data on both sides would otherwise recurse through user <-> moods,
    // which corrupts Hibernate's lazy-collection loading (ConcurrentModificationException
    // when a HashSet's hashCode is computed reentrantly while it's still being loaded).
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Set<Mood> moods = new HashSet<>();

    // Constructors, getters, setters, equals, hashCode are handled by Lombok @Data, @NoArgsConstructor, @AllArgsConstructor
    // If specific constructors are needed (e.g. for username, password), they can be added.
}
