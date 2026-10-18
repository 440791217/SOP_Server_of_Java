package org.jsut.modules.user.service;

import org.jsut.common.BusinessException;
import org.jsut.modules.user.dto.UserSummary;
import org.jsut.modules.user.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 用户业务集中在一个具体类中，暂不增加 Service 接口和 Impl 层。 */
@Service
public class UserService {
    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** 内部查询基础能力。未来 Controller 调用前必须完成会话及权限检查。 */
    @Transactional(readOnly = true)
    public UserSummary findById(long id) {
        if (id <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "用户 ID 不正确");
        }
        var user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return new UserSummary(user.id().toString(), user.username(), user.realName(),
                user.cardNumber(), user.teamId() == null ? null : user.teamId().toString(),
                user.phone(), user.avatarUrl(), user.status(), user.isSuperuser(),
                utc(user.createdAt()), utc(user.updatedAt()), user.version());
    }

    private static String utc(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC).toString();
    }
}
