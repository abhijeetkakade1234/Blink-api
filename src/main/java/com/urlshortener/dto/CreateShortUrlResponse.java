package com.urlshortener.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateShortUrlResponse(
    UUID id,
    String shortCode,
    String shortUrl,
    String originalUrl,
    Long clickCount,
    OffsetDateTime createdAt,
    OffsetDateTime expiresAt) {}
