package com.hewei.hzyjy.xunzhi.career.memory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;

final class MemoryHotCache {

    private static final long MAXIMUM_SIZE = 5_000;
    private static final Duration EXPIRE_AFTER_ACCESS = Duration.ofMinutes(30);

    private MemoryHotCache() {
    }

    static <K, V> Cache<K, V> create() {
        return Caffeine.<K, V>newBuilder()
                .maximumSize(MAXIMUM_SIZE)
                .expireAfterAccess(EXPIRE_AFTER_ACCESS)
                .build();
    }
}
