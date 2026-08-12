package com.schwab.shortener.core;

import com.schwab.shortener.core.model.ClickEvent;
import java.util.List;

public interface ClickEventStore {
    void record(ClickEvent event);
    List<ClickEvent> findByCode(String code);
    long countByCode(String code);
}
