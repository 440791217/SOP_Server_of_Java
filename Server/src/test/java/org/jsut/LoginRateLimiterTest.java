package org.jsut;

import org.jsut.common.BusinessException;
import org.jsut.modules.user.service.LoginRateLimiter;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class LoginRateLimiterTest {
    @Test
    void twentyAttemptsPerIpThen429AndOtherIpUnaffected() {
        var limiter = new LoginRateLimiter(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        for (int i = 0; i < 20; i++) limiter.check("127.0.0.1");
        var failure = assertThrows(BusinessException.class, () -> limiter.check("127.0.0.1"));
        assertEquals(429, failure.getStatus().value());
        assertDoesNotThrow(() -> limiter.check("127.0.0.2"));
    }
}
