package org.jsut.modules.user.service;

import org.jsut.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** 单实例、按直连 IP 限制每分钟20次尝试；不信任客户端 X-Forwarded-For。 */
@Component
public class LoginRateLimiter {
    private record Window(long until, int attempts) { }
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;

    public LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized void check(String address) {
        long now = clock.millis();
        windows.entrySet().removeIf(entry -> entry.getValue().until() <= now);
        Window window = windows.get(address);
        if ((window == null && windows.size() >= 4096) || (window != null && window.attempts() >= 20)) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "登录尝试过于频繁，请稍后再试");
        }
        windows.put(address, window == null ? new Window(now + 60_000, 1)
                : new Window(window.until(), window.attempts() + 1));
    }
}
