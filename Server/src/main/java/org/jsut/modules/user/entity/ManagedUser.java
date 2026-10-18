package org.jsut.modules.user.entity;

import java.time.LocalDateTime;
public record ManagedUser(Long id, String username, String realName, String cardNumber, Long teamId,
        String teamName, String phone, String avatarUrl, Integer status, Boolean isSuperuser,
        LocalDateTime createdAt, LocalDateTime updatedAt, Integer version) { }
