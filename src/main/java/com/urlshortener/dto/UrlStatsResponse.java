package com.urlshortener.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record UrlStatsResponse(
    UUID id,
    String shortCode,
    String shortUrl,
    String originalUrl,
    Long totalClicks,
    OffsetDateTime createdAt,
    OffsetDateTime expiresAt,
    Map<String, Long> countries,
    Map<String, Long> devices,
    Map<String, Long> browsers) {}
