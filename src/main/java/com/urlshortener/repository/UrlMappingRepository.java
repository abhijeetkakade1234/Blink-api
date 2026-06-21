package com.urlshortener.repository;

import com.urlshortener.entity.UrlMapping;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, UUID> {
  Optional<UrlMapping> findByShortCode(String shortCode);

  boolean existsByShortCode(String shortCode);

  Page<UrlMapping> findByOriginalUrlContainingIgnoreCaseOrShortCodeContainingIgnoreCase(
      String originalUrlQuery, String shortCodeQuery, Pageable pageable);
}
