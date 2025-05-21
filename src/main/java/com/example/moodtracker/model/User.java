package com.example.moodtracker.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

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
    private String password;

    // For now, we'll keep roles simple. A Set<String> could be used for more complex role management.
    // private String role; // Example: "ROLE_USER"

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Mood> moods = new HashSet<>();

    // Constructors, getters, setters, equals, hashCode are handled by Lombok @Data, @NoArgsConstructor, @AllArgsConstructor
    // If specific constructors are needed (e.g. for username, password), they can be added.
}
