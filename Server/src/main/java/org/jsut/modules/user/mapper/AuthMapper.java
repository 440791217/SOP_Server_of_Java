package org.jsut.modules.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jsut.modules.user.entity.UserCredentials;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AuthMapper {
    UserCredentials lockByUsername(@Param("username") String username);
    UserCredentials lockById(@Param("id") long id);
    UserCredentials findByToken(@Param("hash") byte[] hash, @Param("now") LocalDateTime now);
    Long lockSession(@Param("hash") byte[] hash, @Param("now") LocalDateTime now);
    List<String> roles(@Param("userId") long userId);
    List<String> permissions(@Param("userId") long userId);
    int insertSession(@Param("userId") long userId, @Param("hash") byte[] hash,
                      @Param("now") LocalDateTime now, @Param("expires") LocalDateTime expires);
    int recordLogin(@Param("id") long id, @Param("now") LocalDateTime now);
    int revokeSession(@Param("hash") byte[] hash, @Param("now") LocalDateTime now);
    int revokeAllSessions(@Param("id") long id, @Param("now") LocalDateTime now);
    int changePassword(@Param("id") long id, @Param("hash") String hash,
                       @Param("now") LocalDateTime now);
    int initializeRoot(@Param("id") long id, @Param("hash") String hash,
                       @Param("now") LocalDateTime now);
}
