package org.jsut.modules.project.mapper;
import org.apache.ibatis.annotations.*;
import java.util.Map;
@Mapper public interface DeletionMapper {
 long active(Map<String,Object> q);
 int tasks(Map<String,Object> q);int children(Map<String,Object> q);int audit(Map<String,Object> q);
}
