package org.jsut.modules.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jsut.modules.user.entity.User;

@Mapper
public interface UserMapper {
    User findById(@Param("id") long id);
}
