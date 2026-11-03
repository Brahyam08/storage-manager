package com.brahyam.storagemanager.config;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class AuthRateLimiterTest {
    @Test
    void shouldExpireWindowAndRoundRetryAfterUpWithoutSleeping() {
        AtomicLong now = new AtomicLong();
        var limiter = new AuthRateLimiter(2, 10, 1, 20, 10, now::get);
        assertEquals(0, limiter.tryAcquire("client", false));
        assertEquals(0, limiter.tryAcquire("client", false));
        assertEquals(10, limiter.tryAcquire("client", false));
        now.set(TimeUnit.SECONDS.toNanos(9) + 100_000_000);
        assertEquals(1, limiter.tryAcquire("client", false));
        now.set(TimeUnit.SECONDS.toNanos(10));
        assertEquals(0, limiter.tryAcquire("client", false));
    }

    @Test
    void shouldKeepAddressesAndActionsIndependent() {
        var limiter = new AuthRateLimiter(1, 10, 1, 20, 10, () -> 0);
        assertEquals(0, limiter.tryAcquire("first", true));
        assertEquals(20, limiter.tryAcquire("first", true));
        assertEquals(0, limiter.tryAcquire("first", false));
        assertEquals(10, limiter.tryAcquire("first", false));
        assertEquals(0, limiter.tryAcquire("second", false));
    }

    @Test
    void shouldBoundMemoryWithoutEvictingActiveQuotas() {
        AtomicLong now = new AtomicLong();
        var limiter = new AuthRateLimiter(1, 10, 1, 20, 2, now::get);
        assertEquals(0, limiter.tryAcquire("first", false));
        assertEquals(0, limiter.tryAcquire("second", true));
        assertEquals(10, limiter.tryAcquire("new", false));
        assertEquals(10, limiter.tryAcquire("first", false));
        now.set(TimeUnit.SECONDS.toNanos(10));
        assertEquals(0, limiter.tryAcquire("new", false));
        assertEquals(10, limiter.tryAcquire("second", true));
        assertEquals(10, limiter.tryAcquire("another", false));
    }

    @Test
    void shouldAdmitOnlyTheQuotaUnderConcurrentRequests() throws Exception {
        var limiter = new AuthRateLimiter(7, 60, 1, 60, 100, () -> 0);
        ExecutorService workers = Executors.newFixedThreadPool(8);
        try {
            var requests = new ArrayList<Callable<Long>>();
            for (int i = 0; i < 64; i++) requests.add(() -> limiter.tryAcquire("same", false));
            int admitted = 0;
            for (Future<Long> result : workers.invokeAll(requests)) {
                if (result.get() == 0) admitted++;
            }
            assertEquals(7, admitted);
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void shouldRejectInvalidConfigurationRatherThanDisableProtection() {
        assertThrows(IllegalArgumentException.class, () -> new AuthRateLimiter(0, 1, 1, 1, 1, () -> 0));
        assertThrows(IllegalArgumentException.class, () -> new AuthRateLimiter(1, 0, 1, 1, 1, () -> 0));
        assertThrows(IllegalArgumentException.class, () -> new AuthRateLimiter(1, 1, -1, 1, 1, () -> 0));
        assertThrows(IllegalArgumentException.class, () -> new AuthRateLimiter(1, 1, 1, 0, 1, () -> 0));
        assertThrows(IllegalArgumentException.class, () -> new AuthRateLimiter(1, 1, 1, 1, 0, () -> 0));
    }
}
