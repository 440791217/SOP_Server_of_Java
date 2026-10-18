package org.jsut.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

@Configuration
public class PasswordConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        // Argon2id: 19 MiB memory, 2 iterations, parallelism 1.
        return new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
