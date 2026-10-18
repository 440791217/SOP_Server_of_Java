package org.jsut.modules.user.entity;

import java.time.LocalDateTime;

/** 仅用于服务端认证，禁止返回接口或记录到日志。 */
public record UserCredentials(Long id, String username, String realName, String avatarUrl,
                              String passwordHash, Integer status, Boolean isSuperuser,
                              Boolean mustChangePassword, LocalDateTime deletedAt) {
    @Override
    public String toString() {
        return "UserCredentials[redacted]";
    }
}
