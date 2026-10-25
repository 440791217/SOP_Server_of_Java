package org.jsut.modules.execution.service;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import static org.jsut.modules.resource.service.ResourceSupport.*;

public final class ExecutionClock {
 private ExecutionClock(){}
 public static boolean active(Map<String,Object> row,LocalDateTime now){
  if(row==null||!Boolean.parseBoolean(String.valueOf(row.get("active")))&&!"1".equals(String.valueOf(row.get("active"))))return false;
  Object raw=row.get("heartbeat_at");var at=raw instanceof Timestamp t?t.toLocalDateTime():(LocalDateTime)raw;
  long age=Duration.between(at,now).toMillis();return age>=0&&age<15000;
 }
 public static Map<String,Object> viewClock(Map<String,Object> row,LocalDateTime now){
  var out=new HashMap<String,Object>();out.put("position",row.get("position"));out.put("elapsed_ms",row.get("elapsed_ms"));out.put("remaining_ms",row.get("remaining_ms"));out.put("timing_running",active(row,now));out.put("heartbeat_sequence",row.get("heartbeat_sequence"));return out;
 }
 public static void requireSession(Map<String,Object> row,String session,LocalDateTime now){if(row!=null&&(!Objects.equals(session,row.get("session_key"))||!active(row,now)))throw conflict("执行会话已中断，请继续作业后重新检测");}
 public static long budget(Map<String,Object> step){Object seconds=step.get("expected_duration_seconds");return seconds instanceof Number n?Math.max(0,Math.round(n.doubleValue()*1000)):0;}
}
