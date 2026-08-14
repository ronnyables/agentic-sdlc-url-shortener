package com.schwab.shortener.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

/** Small bounded LRU cache used to avoid repository lookups on hot redirect paths. */
public final class LruCache<K, V> {

    private final Map<K, V> map;

    public LruCache(int maxSize) {
        this.map = Collections.synchronizedMap(new LinkedHashMap<K, V>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxSize;
            }
        });
    }

    public V get(K key) { return map.get(key); }
    public void put(K key, V value) { map.put(key, value); }
    public void invalidate(K key) { map.remove(key); }
    public int size() { return map.size(); }
}
