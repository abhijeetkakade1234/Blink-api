package com.urlshortener.dto;

import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record CreateShortUrlRequest(
    @jakarta.validation.constraints.NotBlank(message = "url is required") String url,
    @Size(min = 3, max = 32, message = "customAlias must be between 3 and 32 characters")
        String customAlias,
    OffsetDateTime expiresAt) {}
