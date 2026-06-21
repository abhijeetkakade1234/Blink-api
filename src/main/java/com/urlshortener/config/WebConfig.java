package com.urlshortener.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final RateLimitInterceptor rateLimitInterceptor;
  private final String[] allowedOrigins;

  public WebConfig(
      RateLimitInterceptor rateLimitInterceptor,
      @Value("${app.cors.allowed-origins}") String allowedOrigins) {
    this.rateLimitInterceptor = rateLimitInterceptor;
    this.allowedOrigins = allowedOrigins.split(",");
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(rateLimitInterceptor)
        .addPathPatterns("/api/urls")
        .excludePathPatterns("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**");
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOrigins(allowedOrigins)
        .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
        .allowedHeaders("*");
  }
}
