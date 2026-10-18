package org.jsut.modules.sop.mapper;
import org.apache.ibatis.annotations.Mapper;
import java.util.*;
@Mapper
public interface SopBindingMapper {
    List<Map<String,Object>> list(Map<String,Object> q);
    Map<String,Object> get(Map<String,Object> q);
    Map<String,Object> bySop(Map<String,Object> q);
    int insert(Map<String,Object> data);
    int update(Map<String,Object> data);
    int unbind(Map<String,Object> data);
    int sort(Map<String,Object> data);
}
