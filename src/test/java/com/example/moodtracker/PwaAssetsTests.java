package com.example.moodtracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class PwaAssetsTests {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void manifestIsReachableWithoutAuthenticationAndHasTheExpectedContentType() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/manifest.webmanifest", String.class);

    assertEquals(200, response.getStatusCode().value());
    assertTrue(response.getHeaders().getContentType().toString().contains("manifest+json"));
  }

  // Regression guard for the WebSecurityConfig permit list - these all 302'd to /login
  // before /favicon.svg, /service-worker.js, /apple-touch-icon.png, and /icons/** were
  // added to permitAll(), which would silently break "Add to Home Screen" for anyone
  // not already logged in.
  @Test
  void pwaAssetsAreReachableWithoutAuthentication() {
    for (String path :
        new String[] {
          "/favicon.svg",
          "/service-worker.js",
          "/apple-touch-icon.png",
          "/icons/icon-192.png",
          "/icons/icon-512.png"
        }) {
      ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);
      assertEquals(200, response.getStatusCode().value(), path + " should be reachable");
    }
  }

  @Test
  void loginPageReferencesTheManifest() {
    ResponseEntity<String> response = restTemplate.getForEntity("/login", String.class);

    assertTrue(response.getBody().contains("rel=\"manifest\""));
  }
}
