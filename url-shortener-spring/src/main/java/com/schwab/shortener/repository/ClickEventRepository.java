package com.schwab.shortener.repository;

import com.schwab.shortener.model.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    List<ClickEvent> findByShortCodeOrderByTimestampAsc(String shortCode);

    long countByShortCode(String shortCode);
}
