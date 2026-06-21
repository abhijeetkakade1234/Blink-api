package com.urlshortener.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.urlshortener.dto.CreateShortUrlRequest;
import com.urlshortener.dto.CreateShortUrlResponse;
import com.urlshortener.dto.UrlListResponse;
import com.urlshortener.entity.UrlMapping;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlMappingRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

  @Test
  void updateUrlKeepsExistingShortCodeWhenAliasBlank() {
    UrlMappingRepository urlMappingRepository = mock(UrlMappingRepository.class);
    ClickEventRepository clickEventRepository = mock(ClickEventRepository.class);
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    UUID id = UUID.randomUUID();

    UrlMapping mapping = new UrlMapping();
    mapping.setId(id);
    mapping.setShortCode("blink123");
    mapping.setOriginalUrl("https://example.com/old");
    mapping.setClickCount(5L);
    mapping.setCreatedAt(OffsetDateTime.now().minusDays(1));

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(urlMappingRepository.findById(id)).thenReturn(Optional.of(mapping));
    when(urlMappingRepository.save(any(UrlMapping.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    UrlService service =
        new UrlService(
            urlMappingRepository, clickEventRepository, redisTemplate, "https://blink.dev");

    CreateShortUrlResponse response =
        service.updateUrl(id, new CreateShortUrlRequest("https://example.com/new", " ", null));

    assertEquals("blink123", response.shortCode());
    assertEquals("https://example.com/new", response.originalUrl());
  }

  @Test
  void listUrlsReturnsPagedSearchResults() {
    UrlMappingRepository urlMappingRepository = mock(UrlMappingRepository.class);
    ClickEventRepository clickEventRepository = mock(ClickEventRepository.class);
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    UrlMapping mapping = new UrlMapping();
    mapping.setId(UUID.randomUUID());
    mapping.setShortCode("blink123");
    mapping.setOriginalUrl("https://example.com/new");
    mapping.setClickCount(5L);
    mapping.setCreatedAt(OffsetDateTime.now());

    PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

    when(urlMappingRepository.findByOriginalUrlContainingIgnoreCaseOrShortCodeContainingIgnoreCase(
            eq("blink"), eq("blink"), eq(pageable)))
        .thenReturn(new PageImpl<>(List.of(mapping), pageable, 1));

    UrlService service =
        new UrlService(
            urlMappingRepository, clickEventRepository, redisTemplate, "https://blink.dev");

    UrlListResponse response = service.listUrls(1, 10, "blink");

    assertEquals(1, response.items().size());
    assertEquals(1, response.page());
    assertEquals(1, response.totalPages());
    assertEquals("blink123", response.items().get(0).shortCode());
  }
}
