package com.urlshortener.repository;

import com.urlshortener.entity.ClickEvent;
import com.urlshortener.entity.UrlMapping;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClickEventRepository extends JpaRepository<ClickEvent, UUID> {
  List<ClickEvent> findByUrl(UrlMapping url);
}
