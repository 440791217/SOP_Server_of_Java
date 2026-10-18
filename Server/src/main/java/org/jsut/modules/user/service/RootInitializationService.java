package org.jsut.modules.user.service;

import org.jsut.modules.user.mapper.AuthMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class RootInitializationService {
    private final AuthMapper mapper;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public RootInitializationService(AuthMapper mapper, PasswordEncoder encoder, Clock clock) {
        this.mapper = mapper;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Transactional
    public boolean initialize(String password) {
        var root = mapper.lockByUsername("root");
        if (root == null || !Boolean.TRUE.equals(root.isSuperuser()) || root.deletedAt() != null) {
            throw new IllegalStateException("root 种子用户不存在或身份不正确，请检查 seed.sql 初始化结果");
        }
        // No password overwrite or re-enabling an existing account on restart.
        if (root.passwordHash() != null) {
            return false;
        }
        if (root.status() != 0) {
            throw new IllegalStateException("未初始化的 root 必须处于禁用状态");
        }
        if (password == null || password.isBlank() || password.length() < 8 || password.length() > 128) {
            throw new IllegalStateException("root 初始密码需要8至128个字符，请设置 sop.bootstrap.root.password");
        }
        int updated = mapper.initializeRoot(root.id(), encoder.encode(password),
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        if (updated != 1) {
            throw new IllegalStateException("root 初始化失败，事务已回滚");
        }
        return true;
    }
}
