package com.urlshortener.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.urlshortener.dto.CreateShortUrlRequest;
import com.urlshortener.dto.CreateShortUrlResponse;
import com.urlshortener.entity.UrlMapping;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlMappingRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class UrlServiceTest {

  @Test
  void createShortUrlUsesCustomAliasWhenProvided() {
    UrlMappingRepository urlMappingRepository = mock(UrlMappingRepository.class);
    ClickEventRepository clickEventRepository = mock(ClickEventRepository.class);
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(urlMappingRepository.existsByShortCode("blink123")).thenReturn(false);
    when(urlMappingRepository.save(any(UrlMapping.class)))
        .thenAnswer(
            invocation -> {
              UrlMapping mapping = invocation.getArgument(0);
              mapping.setId(UUID.randomUUID());
              mapping.setCreatedAt(OffsetDateTime.now());
              return mapping;
            });

    UrlService service =
        new UrlService(
            urlMappingRepository, clickEventRepository, redisTemplate, "https://blink.dev");

    CreateShortUrlResponse response =
        service.createShortUrl(
            new CreateShortUrlRequest("https://example.com/very-long-url", "blink123", null));

    assertEquals("blink123", response.shortCode());
    assertEquals("https://blink.dev/blink123", response.shortUrl());
    assertTrue(response.originalUrl().startsWith("https://example.com"));
  }
}
