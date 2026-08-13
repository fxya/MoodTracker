package com.example.moodtracker.service;

import com.example.moodtracker.model.User;
import com.example.moodtracker.repository.UserRepository;
import java.util.Collections; // For creating a singleton set of authorities
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;

  public DatabaseUserDetailsService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    User user =
        userRepository
            .findByUsername(username)
            .orElseThrow(
                () -> new UsernameNotFoundException("User not found with username: " + username));

    // For simplicity, assigning a default "ROLE_USER" to every user.
    // This can be expanded if you add a 'roles' field to your User entity.
    return new org.springframework.security.core.userdetails.User(
        user.getUsername(),
        user.getPassword(),
        Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")) // Authorities
        );
  }
}
