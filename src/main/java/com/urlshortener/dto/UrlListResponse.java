package com.urlshortener.dto;

import java.util.List;

public record UrlListResponse(
    List<UrlSummaryResponse> items, int page, int size, long totalItems, int totalPages) {}
