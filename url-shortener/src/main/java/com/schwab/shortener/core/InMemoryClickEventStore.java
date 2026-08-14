package com.schwab.shortener.core;

import com.schwab.shortener.core.model.ClickEvent;

import java.util.List;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryClickEventStore implements ClickEventStore {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<ClickEvent>> events = new ConcurrentHashMap<>();

    @Override
    public void record(ClickEvent event) {
        events.computeIfAbsent(event.getShortCode(), k -> new CopyOnWriteArrayList<>()).add(event);
    }

    @Override
    public List<ClickEvent> findByCode(String code) {
        CopyOnWriteArrayList<ClickEvent> list = events.get(code);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    @Override
    public long countByCode(String code) {
        CopyOnWriteArrayList<ClickEvent> list = events.get(code);
        return list == null ? 0 : list.size();
    }
}
