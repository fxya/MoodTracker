package com.example.moodtracker.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.example.moodtracker.model.User;
import com.example.moodtracker.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

  @Mock private UserRepository userRepository;

  @Mock private PasswordEncoder passwordEncoder;

  @Mock private Model model;

  @Mock private RedirectAttributes redirectAttributes;

  @InjectMocks private UserController userController;

  private User newUser;
  private BindingResult bindingResult;

  @BeforeEach
  void setUp() {
    newUser = new User();
    newUser.setUsername("newuser");
    newUser.setPassword("plaintext-password");
    bindingResult = new BeanPropertyBindingResult(newUser, "user");
  }

  @Test
  void showRegistrationForm_addsEmptyUserWhenNoneOnModel() {
    when(model.containsAttribute("user")).thenReturn(false);

    String viewName = userController.showRegistrationForm(model);

    assertEquals("register", viewName);
    verify(model).addAttribute(eq("user"), any(User.class));
  }

  @Test
  void showRegistrationForm_keepsExistingUserOnModel() {
    when(model.containsAttribute("user")).thenReturn(true);

    String viewName = userController.showRegistrationForm(model);

    assertEquals("register", viewName);
    verify(model, never()).addAttribute(eq("user"), any(User.class));
  }

  @Test
  void registerUser_savesNewUserWithEncodedPasswordAndRedirectsToLogin() {
    when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("plaintext-password")).thenReturn("encoded-password");

    String viewName = userController.registerUser(newUser, bindingResult, redirectAttributes);

    assertEquals("redirect:/login", viewName);

    ArgumentCaptor<User> savedUserCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(savedUserCaptor.capture());
    assertEquals("newuser", savedUserCaptor.getValue().getUsername());
    assertEquals("encoded-password", savedUserCaptor.getValue().getPassword());
    verify(redirectAttributes).addFlashAttribute(eq("registrationSuccess"), anyString());
  }

  @Test
  void registerUser_rejectsDuplicateUsernameWithoutSaving() {
    User existingUser = new User();
    existingUser.setUsername("newuser");
    when(userRepository.findByUsername("newuser")).thenReturn(Optional.of(existingUser));

    String viewName = userController.registerUser(newUser, bindingResult, redirectAttributes);

    assertEquals("redirect:/register", viewName);
    verify(userRepository, never()).save(any(User.class));
    verify(passwordEncoder, never()).encode(anyString());
  }
}
