package com.example.moodtracker;

import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebServerConfig {

  // Neither Tomcat's nor Spring MVC's bundled MIME tables know the .webmanifest
  // extension, so without this, ResourceHttpRequestHandler serves manifest.webmanifest
  // as application/octet-stream instead of the content type browsers require to
  // recognize it as a web app manifest.
  @Bean
  public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> webmanifestMimeMapping() {
    MimeMappings mappings = new MimeMappings();
    mappings.add("webmanifest", "application/manifest+json");
    return factory -> factory.addMimeMappings(mappings);
  }
}
