package org.jsut.modules.user.entity;

import java.time.LocalDateTime;

/** 数据库内部查询模型，不含密码字段，不直接作为接口返回值。 */
public record User(Long id, String username, String realName, String cardNumber, Long teamId,
                   String phone, String avatarUrl, Integer status, Boolean isSuperuser,
                   LocalDateTime createdAt, LocalDateTime updatedAt, Integer version) {
}
