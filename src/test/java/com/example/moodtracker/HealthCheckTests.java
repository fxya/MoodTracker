package com.example.moodtracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
class HealthCheckTests {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void healthEndpointIsReachableWithoutAuthenticationAndReportsUp() {
    ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

    assertEquals(200, response.getStatusCode().value());
    assertTrue(response.getBody().contains("\"status\":\"UP\""));
  }

  // Only "health" is in management.endpoints.web.exposure.include, and WebSecurityConfig
  // only permits /actuator/health, so /actuator/env is blocked twice over - either way,
  // real environment data (e.g. its "propertySources" key) must never come back. An
  // unauthenticated request bounces through Spring Security's login redirect rather than
  // a bare 404, so this checks the response body instead of the status code.
  @Test
  void otherActuatorEndpointsNeverExposeRealData() {
    ResponseEntity<String> response = restTemplate.getForEntity("/actuator/env", String.class);

    assertFalse(response.getBody() != null && response.getBody().contains("propertySources"));
  }
}
