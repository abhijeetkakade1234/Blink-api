package com.urlshortener.service;

import com.urlshortener.dto.CreateShortUrlRequest;
import com.urlshortener.dto.CreateShortUrlResponse;
import com.urlshortener.dto.UrlListResponse;
import com.urlshortener.dto.UrlStatsResponse;
import com.urlshortener.dto.UrlSummaryResponse;
import com.urlshortener.entity.ClickEvent;
import com.urlshortener.entity.UrlMapping;
import com.urlshortener.exception.BadRequestException;
import com.urlshortener.exception.NotFoundException;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlMappingRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UrlService {

  private static final String ALPHABET =
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

  private final UrlMappingRepository urlMappingRepository;
  private final ClickEventRepository clickEventRepository;
  private final StringRedisTemplate redisTemplate;
  private final String baseUrl;

  public UrlService(
      UrlMappingRepository urlMappingRepository,
      ClickEventRepository clickEventRepository,
      StringRedisTemplate redisTemplate,
      @Value("${app.base-url}") String baseUrl) {
    this.urlMappingRepository = urlMappingRepository;
    this.clickEventRepository = clickEventRepository;
    this.redisTemplate = redisTemplate;
    this.baseUrl = baseUrl;
  }

  @Transactional
  public CreateShortUrlResponse createShortUrl(CreateShortUrlRequest request) {
    String normalizedUrl = normalizeUrl(request.url());
    String shortCode = resolveShortCode(request.customAlias());

    UrlMapping mapping = new UrlMapping();
    mapping.setOriginalUrl(normalizedUrl);
    mapping.setShortCode(shortCode);
    mapping.setClickCount(0L);
    mapping.setExpiresAt(request.expiresAt());

    UrlMapping saved = urlMappingRepository.save(mapping);
    cacheMapping(saved);
    return toCreateResponse(saved);
  }

  @Transactional
  public CreateShortUrlResponse updateUrl(UUID id, CreateShortUrlRequest request) {
    UrlMapping mapping =
        urlMappingRepository.findById(id).orElseThrow(() -> new NotFoundException("url not found"));
    String previousShortCode = mapping.getShortCode();

    mapping.setOriginalUrl(normalizeUrl(request.url()));
    mapping.setShortCode(resolveUpdatedShortCode(mapping, request.customAlias()));
    mapping.setExpiresAt(request.expiresAt());

    UrlMapping saved = urlMappingRepository.save(mapping);
    if (!previousShortCode.equals(saved.getShortCode())) {
      deleteCacheKey(previousShortCode);
    }
    cacheMapping(saved);
    return toCreateResponse(saved);
  }

  @Transactional
  public String resolveRedirect(String shortCode, HttpServletRequest request) {
    String cacheKey = cacheKey(shortCode);

    try {
      String cached = redisTemplate.opsForValue().get(cacheKey);
      if (cached != null && !cached.isBlank()) {
        UrlMapping mapping =
            urlMappingRepository
                .findByShortCode(shortCode)
                .orElseThrow(() -> new NotFoundException("short code not found"));
        ensureNotExpired(mapping);
        trackClick(mapping, request);
        return cached;
      }
    } catch (Exception ignored) {
      // ponytail: redirect still works from postgres if redis is unavailable.
    }

    UrlMapping mapping =
        urlMappingRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new NotFoundException("short code not found"));
    ensureNotExpired(mapping);
    cacheMapping(mapping);
    trackClick(mapping, request);
    return mapping.getOriginalUrl();
  }

  @Transactional(readOnly = true)
  public UrlStatsResponse getStats(String shortCode) {
    UrlMapping mapping =
        urlMappingRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new NotFoundException("short code not found"));

    List<ClickEvent> events = clickEventRepository.findByUrl(mapping);
    return new UrlStatsResponse(
        mapping.getId(),
        mapping.getShortCode(),
        buildShortUrl(mapping.getShortCode()),
        mapping.getOriginalUrl(),
        mapping.getClickCount(),
        mapping.getCreatedAt(),
        mapping.getExpiresAt(),
        summarize(events, ClickEvent::getCountry),
        summarize(events, ClickEvent::getDevice),
        summarize(events, ClickEvent::getBrowser));
  }

  @Transactional(readOnly = true)
  public UrlListResponse listUrls(int page, int size, String query) {
    int safePage = Math.max(page, 1);
    int safeSize = Math.min(Math.max(size, 1), 100);
    Pageable pageable =
        PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<UrlMapping> resultPage;

    if (query == null || query.isBlank()) {
      resultPage = urlMappingRepository.findAll(pageable);
    } else {
      String trimmedQuery = query.trim();
      resultPage =
          urlMappingRepository.findByOriginalUrlContainingIgnoreCaseOrShortCodeContainingIgnoreCase(
              trimmedQuery, trimmedQuery, pageable);
    }

    List<UrlSummaryResponse> items =
        resultPage.getContent().stream().map(this::toSummaryResponse).toList();

    return new UrlListResponse(
        items,
        safePage,
        safeSize,
        resultPage.getTotalElements(),
        Math.max(resultPage.getTotalPages(), 1));
  }

  @Transactional
  public void deleteUrl(UUID id) {
    UrlMapping mapping =
        urlMappingRepository.findById(id).orElseThrow(() -> new NotFoundException("url not found"));
    urlMappingRepository.delete(mapping);

    try {
      deleteCacheKey(mapping.getShortCode());
    } catch (Exception ignored) {
    }
  }

  private void deleteCacheKey(String shortCode) {
    try {
      redisTemplate.delete(cacheKey(shortCode));
    } catch (Exception ignored) {
    }
  }

  private void trackClick(UrlMapping mapping, HttpServletRequest request) {
    mapping.setClickCount(mapping.getClickCount() + 1);
    urlMappingRepository.save(mapping);

    ClickEvent event = new ClickEvent();
    event.setUrl(mapping);
    event.setCountry(resolveCountry(request));
    event.setDevice(resolveDevice(request));
    event.setBrowser(resolveBrowser(request));
    clickEventRepository.save(event);
  }

  private String resolveShortCode(String customAlias) {
    if (customAlias != null && !customAlias.isBlank()) {
      String trimmed = customAlias.trim();
      if (!trimmed.matches("[A-Za-z0-9_-]+")) {
        throw new BadRequestException("customAlias contains invalid characters");
      }
      if (urlMappingRepository.existsByShortCode(trimmed)) {
        throw new BadRequestException("customAlias already exists");
      }
      return trimmed;
    }

    String code;
    do {
      code = randomCode(6);
    } while (urlMappingRepository.existsByShortCode(code));
    return code;
  }

  private String resolveUpdatedShortCode(UrlMapping existingMapping, String customAlias) {
    if (customAlias == null || customAlias.isBlank()) {
      return existingMapping.getShortCode();
    }

    String trimmed = customAlias.trim();
    if (!trimmed.matches("[A-Za-z0-9_-]+")) {
      throw new BadRequestException("customAlias contains invalid characters");
    }
    if (!trimmed.equals(existingMapping.getShortCode())
        && urlMappingRepository.existsByShortCode(trimmed)) {
      throw new BadRequestException("customAlias already exists");
    }
    return trimmed;
  }

  private String normalizeUrl(String url) {
    try {
      URI uri = URI.create(url.trim());
      if (uri.getScheme() == null || uri.getHost() == null) {
        throw new BadRequestException("url must include a valid scheme and host");
      }
      if (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https")) {
        throw new BadRequestException("url must start with http or https");
      }
      return uri.toString();
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException("url is invalid");
    }
  }

  private void ensureNotExpired(UrlMapping mapping) {
    if (mapping.getExpiresAt() != null && mapping.getExpiresAt().isBefore(OffsetDateTime.now())) {
      throw new NotFoundException("short code expired");
    }
  }

  private void cacheMapping(UrlMapping mapping) {
    try {
      if (mapping.getExpiresAt() != null && mapping.getExpiresAt().isAfter(OffsetDateTime.now())) {
        Duration ttl = Duration.between(OffsetDateTime.now(), mapping.getExpiresAt());
        redisTemplate
            .opsForValue()
            .set(cacheKey(mapping.getShortCode()), mapping.getOriginalUrl(), ttl);
      } else {
        redisTemplate.opsForValue().set(cacheKey(mapping.getShortCode()), mapping.getOriginalUrl());
      }
    } catch (Exception ignored) {
    }
  }

  private String buildShortUrl(String shortCode) {
    return baseUrl.endsWith("/") ? baseUrl + shortCode : baseUrl + "/" + shortCode;
  }

  private CreateShortUrlResponse toCreateResponse(UrlMapping mapping) {
    return new CreateShortUrlResponse(
        mapping.getId(),
        mapping.getShortCode(),
        buildShortUrl(mapping.getShortCode()),
        mapping.getOriginalUrl(),
        mapping.getClickCount(),
        mapping.getCreatedAt(),
        mapping.getExpiresAt());
  }

  private UrlSummaryResponse toSummaryResponse(UrlMapping mapping) {
    return new UrlSummaryResponse(
        mapping.getId(),
        mapping.getShortCode(),
        buildShortUrl(mapping.getShortCode()),
        mapping.getOriginalUrl(),
        mapping.getClickCount(),
        mapping.getCreatedAt(),
        mapping.getExpiresAt());
  }

  private String cacheKey(String shortCode) {
    return "url:" + shortCode;
  }

  private String randomCode(int length) {
    StringBuilder builder = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      int index = ThreadLocalRandom.current().nextInt(ALPHABET.length());
      builder.append(ALPHABET.charAt(index));
    }
    return builder.toString();
  }

  private String resolveCountry(HttpServletRequest request) {
    String country = request.getHeader("CF-IPCountry");
    if (country == null || country.isBlank()) {
      country = request.getHeader("X-Country-Code");
    }
    return country == null || country.isBlank() ? "unknown" : country.toUpperCase(Locale.ROOT);
  }

  private String resolveDevice(HttpServletRequest request) {
    String agent = readAgent(request);
    if (agent.contains("mobile")) {
      return "mobile";
    }
    if (agent.contains("tablet") || agent.contains("ipad")) {
      return "tablet";
    }
    return "desktop";
  }

  private String resolveBrowser(HttpServletRequest request) {
    String agent = readAgent(request);
    if (agent.contains("edg/")) {
      return "edge";
    }
    if (agent.contains("chrome/")) {
      return "chrome";
    }
    if (agent.contains("safari/") && !agent.contains("chrome/")) {
      return "safari";
    }
    if (agent.contains("firefox/")) {
      return "firefox";
    }
    return "other";
  }

  private String readAgent(HttpServletRequest request) {
    String userAgent = request.getHeader("User-Agent");
    return userAgent == null ? "" : userAgent.toLowerCase(Locale.ROOT);
  }

  private Map<String, Long> summarize(
      List<ClickEvent> events, java.util.function.Function<ClickEvent, String> extractor) {
    Map<String, Long> summary = new LinkedHashMap<>();
    for (ClickEvent event : events) {
      summary.merge(extractor.apply(event), 1L, Long::sum);
    }
    return summary;
  }
}
