package com.example.moodtracker.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

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

  // Nullable at the DB level even though @NotBlank/@Email make it required on the
  // registration form: ddl-auto=update can't safely add a NOT NULL column to an
  // already-populated table, so existing users stay emailless until they set one in
  // Settings. Required to use the forgot-password flow, since that's how the reset
  // link is delivered.
  //
  // Scoped to the RegistrationValidation group rather than the default group:
  // Hibernate auto-validates entities against the *default* group on every
  // save (JPA/Bean Validation integration), and most saves of an existing User
  // (settings updates, password changes) have no email yet - validating those
  // against @NotBlank would break them. Only UserController's registration
  // endpoint opts into this group explicitly.
  @Column(unique = true)
  @NotBlank(message = "Email is required.", groups = User.RegistrationValidation.class)
  @Email(message = "Enter a valid email address.", groups = User.RegistrationValidation.class)
  private String email;

  // Single-use password-reset token + its expiry. Both null outside of an active
  // reset request; cleared again as soon as the reset succeeds.
  @JsonIgnore private String resetToken;

  private Instant resetTokenExpiry;

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

  // Constructors, getters, setters, equals, hashCode are handled by Lombok @Data,
  // @NoArgsConstructor, @AllArgsConstructor
  // If specific constructors are needed (e.g. for username, password), they can be added.

  // Marker group for validation that should only run at registration - see the email
  // field above.
  public interface RegistrationValidation {}
}
