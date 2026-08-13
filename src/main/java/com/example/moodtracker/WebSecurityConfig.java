package com.example.moodtracker;

import com.example.moodtracker.security.AuthRateLimitFilter;
import com.example.moodtracker.service.DatabaseUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

  @Bean
  public static PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public AuthenticationProvider authenticationProvider(
      DatabaseUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
    authProvider.setPasswordEncoder(passwordEncoder);
    return authProvider;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, AuthRateLimitFilter authRateLimitFilter) throws Exception {
    http.addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
        .authorizeHttpRequests(
            authorizeRequests ->
                authorizeRequests
                    .requestMatchers(
                        "/",
                        "/home",
                        "/login",
                        "/register", // For potential registration page
                        "/forgot-password",
                        "/reset-password",
                        "/actuator/health",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/favicon.svg",
                        "/manifest.webmanifest",
                        "/service-worker.js",
                        "/apple-touch-icon.png",
                        "/icons/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(
            formLogin ->
                formLogin.loginPage("/login").defaultSuccessUrl("/moodtracker", true).permitAll())
        .logout(
            logout ->
                logout
                    .logoutUrl("/logout")
                    .logoutSuccessUrl("/login?logout")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID")
                    .permitAll())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

    return http.build();
  }
}
