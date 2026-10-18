package org.jsut.modules.resource.mapper;
import org.apache.ibatis.annotations.*;
import java.util.*;
@Mapper public interface ResourceMapper {
 List<Map<String,Object>> list(Map<String,Object> q); long count(Map<String,Object> q);
 Map<String,Object> get(Map<String,Object> q); Map<String,Object> lock(Map<String,Object> q);
 int insert(Map<String,Object> q); int update(Map<String,Object> q); int delete(Map<String,Object> q);
 long references(Map<String,Object> q); Map<String,Object> connection(@Param("id") long id);
 int removeConnection(@Param("id") long id); int insertConnection(Map<String,Object> q);
 List<Map<String,Object>> requirements(@Param("id") long id); int clearRequirements(@Param("id") long id); int insertRequirement(Map<String,Object> q);
 int environment(@Param("id") long id,@Param("text") String text);
 List<Map<String,Object>> stationTasks(@Param("stationId") long stationId);
 List<Map<String,Object>> tasks(@Param("sid") long sid); Map<String,Object> task(@Param("id") long id);
 Map<String,Object> contentOwnerTask(@Param("id") long id);
 Map<String,Object> taskContent(@Param("id") long id);
 int insertTaskContent(Map<String,Object> q); int bumpTaskContent(Map<String,Object> q);
 long contentReferences(@Param("id") long id);
 int insertTask(Map<String,Object> q); int updateTask(Map<String,Object> q);
 Map<String,Object> standard(@Param("id") long id); int deactivate(@Param("id") long id); int insertStandard(Map<String,Object> q);
 long taskReferences(@Param("sid") long sid); long ownerTasks(@Param("pid") long pid,@Param("uid") long uid);
 long revisionTasks(@Param("rid") long rid);
 List<Map<String,Object>> devices(@Param("id") long id);
}
