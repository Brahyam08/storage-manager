package com.brahyam.storagemanager.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

@Component
public class AuthRateLimiter {
    private final int loginLimit;
    private final int registrationLimit;
    private final long loginWindow;
    private final long registrationWindow;
    private final int maxBuckets;
    private final LongSupplier nanoTime;
    private final Map<Key, Window> windows = new HashMap<>();

    @Autowired
    public AuthRateLimiter(
            @Value("${security.auth-rate-limit.login-limit:20}") int loginLimit,
            @Value("${security.auth-rate-limit.login-window-seconds:60}") int loginWindowSeconds,
            @Value("${security.auth-rate-limit.registration-limit:10}") int registrationLimit,
            @Value("${security.auth-rate-limit.registration-window-seconds:900}") int registrationWindowSeconds,
            @Value("${security.auth-rate-limit.max-buckets:10000}") int maxBuckets) {
        this(loginLimit, loginWindowSeconds, registrationLimit, registrationWindowSeconds, maxBuckets, System::nanoTime);
    }

    AuthRateLimiter(int loginLimit, int loginWindowSeconds, int registrationLimit,
                    int registrationWindowSeconds, int maxBuckets, LongSupplier nanoTime) {
        if (loginLimit < 1 || registrationLimit < 1 || loginWindowSeconds < 1
                || registrationWindowSeconds < 1 || maxBuckets < 1) {
            throw new IllegalArgumentException("Los límites y ventanas deben ser positivos");
        }
        this.loginLimit = loginLimit;
        this.registrationLimit = registrationLimit;
        this.loginWindow = TimeUnit.SECONDS.toNanos(loginWindowSeconds);
        this.registrationWindow = TimeUnit.SECONDS.toNanos(registrationWindowSeconds);
        this.maxBuckets = maxBuckets;
        this.nanoTime = nanoTime;
    }

    /** Returns zero when admitted, or the number of seconds to wait. */
    public synchronized long tryAcquire(String address, boolean registration) {
        long now = nanoTime.getAsLong();
        Key key = new Key(address, registration);
        Window window = windows.get(key);
        if (window != null && window.remaining(now) <= 0) {
            windows.remove(key);
            window = null;
        }
        if (window == null) {
            if (windows.size() >= maxBuckets) {
                windows.entrySet().removeIf(entry -> entry.getValue().remaining(now) <= 0);
            }
            if (windows.size() >= maxBuckets) {
                // Never evict active quotas: new clients wait for capacity to expire.
                long earliest = windows.values().stream().mapToLong(value -> value.remaining(now)).min().orElse(1);
                return secondsRoundedUp(earliest);
            }
            window = new Window(now, registration ? registrationWindow : loginWindow);
            windows.put(key, window);
        }
        int limit = registration ? registrationLimit : loginLimit;
        if (window.attempts >= limit) return secondsRoundedUp(window.remaining(now));
        window.attempts++;
        return 0;
    }

    private long secondsRoundedUp(long nanoseconds) {
        return Math.max(1, (nanoseconds + 999_999_999L) / 1_000_000_000L);
    }

    private record Key(String address, boolean registration) { }

    private static class Window {
        private final long started;
        private final long duration;
        private int attempts;

        private Window(long started, long duration) {
            this.started = started;
            this.duration = duration;
        }

        private long remaining(long now) {
            return duration - (now - started);
        }
    }
}
