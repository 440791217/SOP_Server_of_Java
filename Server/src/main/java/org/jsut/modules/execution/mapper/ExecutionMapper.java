package org.jsut.modules.execution.mapper;
import org.apache.ibatis.annotations.*;
import java.util.*;
@Mapper public interface ExecutionMapper {
 @Select("SELECT CURRENT_TIMESTAMP(3)") java.time.LocalDateTime now();
 @Select("SELECT id,owner_user_id FROM sop_project WHERE id=#{id}") Map<String,Object> project(@Param("id") long id);
 Map<String,Object> get(@Param("id") long id);
 Map<String,Object> lock(@Param("id") long id);
 Map<String,Object> request(@Param("uid") long uid,@Param("key") String key);
 List<Map<String,Object>> list(Map<String,Object> q); long count(Map<String,Object> q);
 int insert(Map<String,Object> q); int insertStep(Map<String,Object> q);
 List<Map<String,Object>> steps(@Param("id") long id); List<Map<String,Object>> events(@Param("id") long id);
 int confirm(Map<String,Object> q); int update(Map<String,Object> q); int event(Map<String,Object> q);
 List<Map<String,Object>> evidence(@Param("id") long id);
 Map<String,Object> stepEvidence(@Param("id") long id,@Param("position") int position);
 int insertEvidence(Map<String,Object> q);
 List<Map<String,Object>> clocks(@Param("id") long id);
 Map<String,Object> clock(@Param("id") long id,@Param("position") int position);
 int insertClock(Map<String,Object> q); int updateClock(Map<String,Object> q);
 int stopClocks(@Param("id") long id);
}
