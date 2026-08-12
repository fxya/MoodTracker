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
import org.springframework.ui.Model;
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
    void testShowSettings_exposesCurrentLocationAndTimeZone() {
        testUser.setLocation("London");
        testUser.setTimeZone("Europe/London");
        when(authentication.getName()).thenReturn(testUsername);
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

        Model model = mock(Model.class);
        String viewName = settingsController.showSettings(model, authentication);

        assertEquals("settings", viewName);
        verify(model).addAttribute("location", "London");
        verify(model).addAttribute("timeZone", "Europe/London");
        verify(model).addAttribute(eq("availableTimeZones"), any());
        verify(model).addAttribute("username", testUsername);
    }

    @Test
    void testUpdateSettings_savesLocationAndTimeZone() {
        when(authentication.getName()).thenReturn(testUsername);
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

        String viewName = settingsController.updateSettings("London", "Europe/London", authentication, redirectAttributes);

        assertEquals("redirect:/settings", viewName);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("London", userCaptor.getValue().getLocation());
        assertEquals("Europe/London", userCaptor.getValue().getTimeZone());
        verify(redirectAttributes).addFlashAttribute("settingsSaved", true);
    }

    @Test
    void testUpdateSettings_blankTimeZoneClearsIt() {
        testUser.setTimeZone("Europe/London");
        when(authentication.getName()).thenReturn(testUsername);
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

        settingsController.updateSettings("London", "  ", authentication, redirectAttributes);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertNull(userCaptor.getValue().getTimeZone());
    }

    @Test
    void testUpdateSettings_invalidTimeZone_doesNotSave() {
        when(authentication.getName()).thenReturn(testUsername);
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

        String viewName = settingsController.updateSettings("London", "Not/AZone", authentication, redirectAttributes);

        assertEquals("redirect:/settings", viewName);
        verify(userRepository, never()).save(any());
        verify(redirectAttributes).addFlashAttribute(eq("settingsError"), anyString());
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
