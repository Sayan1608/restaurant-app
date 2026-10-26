package com.restaurantapp.chat;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatRateLimiter {
    private static final int MAX_REQUESTS_PER_MINUTE = 20;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public boolean allow(String client) {
        long minute = Instant.now().getEpochSecond() / 60;
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> entry.getValue().minute < minute);
        }
        Window window = windows.compute(client, (key, current) ->
                current == null || current.minute != minute ? new Window(minute, 1) : current.increment());
        return window.count <= MAX_REQUESTS_PER_MINUTE;
    }

    private record Window(long minute, int count) {
        Window increment() { return new Window(minute, count + 1); }
    }
}
