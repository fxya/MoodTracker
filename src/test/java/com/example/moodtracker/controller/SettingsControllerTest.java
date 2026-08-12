package com.example.moodtracker.controller;

import com.example.moodtracker.model.User;
import com.example.moodtracker.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SettingsControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private Authentication authentication;

    @Mock
    private RedirectAttributes redirectAttributes;

    @InjectMocks
    private SettingsController settingsController;

    private User testUser;
    private final String testUsername = "testuser";

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername(testUsername);
        testUser.setPassword("encoded-old-password");
    }

    @Test
    void testChangePassword_success() {
        when(authentication.getName()).thenReturn(testUsername);
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldpass", testUser.getPassword())).thenReturn(true);
        when(passwordEncoder.encode("newpass")).thenReturn("encoded-new-password");

        String viewName = settingsController.changePassword("oldpass", "newpass", "newpass",
                authentication, redirectAttributes);

        assertEquals("redirect:/settings", viewName);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("encoded-new-password", userCaptor.getValue().getPassword());
        verify(redirectAttributes).addFlashAttribute("passwordChanged", true);
    }

    @Test
    void testChangePassword_wrongCurrentPassword_doesNotSave() {
        when(authentication.getName()).thenReturn(testUsername);
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongpass", testUser.getPassword())).thenReturn(false);

        String viewName = settingsController.changePassword("wrongpass", "newpass", "newpass",
                authentication, redirectAttributes);

        assertEquals("redirect:/settings", viewName);
        verify(userRepository, never()).save(any());
        verify(redirectAttributes).addFlashAttribute(eq("passwordError"), anyString());
    }

    @Test
    void testChangePassword_confirmationMismatch_doesNotSave() {
        when(authentication.getName()).thenReturn(testUsername);
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldpass", testUser.getPassword())).thenReturn(true);

        String viewName = settingsController.changePassword("oldpass", "newpass", "different",
                authentication, redirectAttributes);

        assertEquals("redirect:/settings", viewName);
        verify(userRepository, never()).save(any());
        verify(redirectAttributes).addFlashAttribute(eq("passwordError"), anyString());
    }

    @Test
    void testChangePassword_blankNewPassword_doesNotSave() {
        when(authentication.getName()).thenReturn(testUsername);
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldpass", testUser.getPassword())).thenReturn(true);

        String viewName = settingsController.changePassword("oldpass", "  ", "  ",
                authentication, redirectAttributes);

        assertEquals("redirect:/settings", viewName);
        verify(userRepository, never()).save(any());
        verify(redirectAttributes).addFlashAttribute(eq("passwordError"), anyString());
    }

    @Test
    void testDeleteAccount_success() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession()).thenReturn(session);

        when(authentication.getName()).thenReturn(testUsername);
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("correctpass", testUser.getPassword())).thenReturn(true);

        String viewName = settingsController.deleteAccount("correctpass", authentication, request, redirectAttributes);

        assertEquals("redirect:/login?accountDeleted", viewName);
        verify(userRepository).delete(testUser);
        verify(session).invalidate();
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testDeleteAccount_wrongPassword_doesNotDelete() {
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(authentication.getName()).thenReturn(testUsername);
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongpass", testUser.getPassword())).thenReturn(false);

        String viewName = settingsController.deleteAccount("wrongpass", authentication, request, redirectAttributes);

        assertEquals("redirect:/settings", viewName);
        verify(userRepository, never()).delete(any());
        verify(request, never()).getSession();
        verify(redirectAttributes).addFlashAttribute(eq("deleteError"), anyString());
    }
}
