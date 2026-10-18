package org.jsut.modules.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jsut.modules.user.entity.ManagedUser;
import org.jsut.modules.user.dto.UserOption;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserManagementMapper {
    List<ManagedUser> list(Map<String, Object> query);
    long count(Map<String, Object> query);
    ManagedUser detail(@Param("id") long id);
    List<UserOption> userRoles(@Param("id") long id);
    List<UserOption> teamOptions();
    List<UserOption> roleOptions();
    UserOption team(@Param("id") long id);
    UserOption role(@Param("id") long id);
    Long memberRole();
    int insert(Map<String, Object> values);
    int update(Map<String, Object> values);
    int status(Map<String, Object> values);
    int softDelete(Map<String, Object> values);
    int resetPassword(Map<String, Object> values);
    int bumpVersion(Map<String, Object> values);
    int removeRoles(@Param("id") long id);
    int addRole(@Param("id") long id, @Param("roleId") long roleId);
}
