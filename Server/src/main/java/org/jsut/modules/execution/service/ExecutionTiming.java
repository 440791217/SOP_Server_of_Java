package org.jsut.modules.execution.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/** Reconstruct active step time from persisted confirmation and pause events. */
public final class ExecutionTiming {
 private ExecutionTiming() {}
 private static LocalDateTime time(Object value) {
  if(value==null)return null;
  if(value instanceof Timestamp stamp)return stamp.toLocalDateTime();
  if(value instanceof LocalDateTime date)return date;
  return LocalDateTime.parse(value.toString().replace(' ','T'));
 }
 private static long millis(LocalDateTime start,LocalDateTime end){return Math.max(0,Duration.between(start,end).toMillis());}
 public static void apply(Map<String,Object> execution,List<? extends Map<String,Object>> steps,List<Map<String,Object>> events,LocalDateTime now) {
  var started=time(execution.get("started_at"));if(started==null)return;
  int status=((Number)execution.get("status")).intValue();
  var ended=time(execution.get("ended_at"));var stop=ended==null?now:ended;
  var pauses=new ArrayList<LocalDateTime[]>();LocalDateTime pause=null;
  for(var event:events){
   var at=time(event.get("created_at"));if(at==null)continue;
   String action=String.valueOf(event.get("action"));
   if(action.equals("pause")&&pause==null)pause=at;
   else if((action.equals("resume")||action.equals("abort")||action.equals("complete"))&&pause!=null){pauses.add(new LocalDateTime[]{pause,at});pause=null;}
  }
  if(pause!=null)pauses.add(new LocalDateTime[]{pause,stop});
  LocalDateTime start=started;
  for(var step:steps){
   if(start==null)break;
   var confirmed=time(step.get("confirmed_at"));var end=confirmed==null?stop:confirmed;
   long elapsed=millis(start,end);
   for(var interval:pauses){var left=interval[0].isAfter(start)?interval[0]:start;var right=interval[1].isBefore(end)?interval[1]:end;if(right.isAfter(left))elapsed-=millis(left,right);}
   step.put("elapsed_ms",Math.max(0,elapsed));
   step.put("timing_running",confirmed==null&&status==0);
   start=confirmed;
  }
 }
}
