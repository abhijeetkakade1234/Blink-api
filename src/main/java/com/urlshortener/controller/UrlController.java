package com.urlshortener.controller;

import com.urlshortener.dto.CreateShortUrlRequest;
import com.urlshortener.dto.CreateShortUrlResponse;
import com.urlshortener.dto.DeleteUrlResponse;
import com.urlshortener.dto.UrlStatsResponse;
import com.urlshortener.dto.UrlSummaryResponse;
import com.urlshortener.service.UrlService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/urls")
public class UrlController {

  private final UrlService urlService;

  public UrlController(UrlService urlService) {
    this.urlService = urlService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CreateShortUrlResponse create(@Valid @RequestBody CreateShortUrlRequest request) {
    return urlService.createShortUrl(request);
  }

  @GetMapping
  public List<UrlSummaryResponse> list() {
    return urlService.listUrls();
  }

  @GetMapping("/{code}/stats")
  public UrlStatsResponse stats(@PathVariable("code") String code) {
    return urlService.getStats(code);
  }

  @DeleteMapping("/{id}")
  public DeleteUrlResponse delete(@PathVariable("id") UUID id) {
    urlService.deleteUrl(id);
    return new DeleteUrlResponse("url deleted");
  }
}
