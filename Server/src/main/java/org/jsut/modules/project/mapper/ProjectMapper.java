package org.jsut.modules.project.mapper;

import org.apache.ibatis.annotations.*;
import java.util.*;

@Mapper
public interface ProjectMapper {
    List<Map<String,Object>> list(Map<String,Object> q);
    long count(Map<String,Object> q);
    Map<String,Object> project(@Param("id") long id);
    Map<String,Object> lockProject(@Param("id") long id);
    Map<String,Object> sub(@Param("projectId") long projectId, @Param("id") long id);
    List<Map<String,Object>> children(@Param("projectId") long projectId);
    Integer member(@Param("projectId") long projectId,@Param("userId") long userId);
    List<Map<String,Object>> users(Map<String,Object> q);
    int insertProject(Map<String,Object> data);
    int insertSub(Map<String,Object> data);
    int addMember(@Param("projectId") long projectId,@Param("userId") long userId,@Param("actor") long actor);
    Map<String,Object> memberRow(@Param("projectId") long projectId,@Param("userId") long userId);
    List<Map<String,Object>> members(Map<String,Object> q);
    long memberCount(Map<String,Object> q);
    long ownedChildren(@Param("projectId") long projectId,@Param("userId") long userId);
    List<Map<String,Object>> memberCandidates(Map<String,Object> q);
    int insertMember(Map<String,Object> data);
    int updateMember(Map<String,Object> data);
    int projectOwner(Map<String,Object> data);
    int subOwner(Map<String,Object> data);
    int insertActivity(Map<String,Object> data);
    List<Map<String,Object>> activities(Map<String,Object> q);
    long activityCount(@Param("projectId") long projectId);
    int updateProject(Map<String,Object> data);
    int updateSub(Map<String,Object> data);
    int projectStatus(Map<String,Object> data);
    int subStatus(Map<String,Object> data);
    int archiveChildren(Map<String,Object> data);
    int restoreProject(Map<String,Object> data);
    int restoreChildren(Map<String,Object> data);
    int restoreSub(Map<String,Object> data);
    int deleteProject(Map<String,Object> data);
    int deleteSub(Map<String,Object> data);
    int bump(Map<String,Object> data);
    int bumpSub(Map<String,Object> data);
    int sort(@Param("projectId") long projectId,@Param("id") long id,@Param("position") int position,@Param("actor") long actor);
}
