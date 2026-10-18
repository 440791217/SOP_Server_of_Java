package org.jsut.modules.sop.mapper;
import org.apache.ibatis.annotations.*;
import java.util.*;
@Mapper
public interface SopVisualMapper {
    Map<String,Object> get(@Param("attachmentId") long attachmentId);
    int save(Map<String,Object> row);
}
