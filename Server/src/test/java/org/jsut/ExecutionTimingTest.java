package org.jsut;
import org.jsut.modules.execution.service.ExecutionTiming;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionTimingTest {
 final LocalDateTime base=LocalDateTime.of(2026,10,24,10,0);
 Map<String,Object> event(String action,int second){return Map.of("action",action,"created_at",base.plusSeconds(second));}
 Map<String,Object> execution(int status){return new HashMap<>(Map.of("started_at",base,"status",status));}
 @Test void restoresCompletedAndCurrentStepTimesAndSubtractsOnlyOverlappingPauses(){
  var steps=List.of(new HashMap<String,Object>(Map.of("confirmed_at",base.plusSeconds(20))),new HashMap<String,Object>(),new HashMap<String,Object>());
  var events=List.of(event("start",0),event("pause",5),event("resume",10),event("confirm",20),event("pause",25),event("resume",30));
  ExecutionTiming.apply(execution(0),steps,events,base.plusSeconds(40));
  assertEquals(15000L,steps.get(0).get("elapsed_ms"));assertEquals(false,steps.get(0).get("timing_running"));
  assertEquals(15000L,steps.get(1).get("elapsed_ms"));assertEquals(true,steps.get(1).get("timing_running"));
  assertFalse(steps.get(2).containsKey("elapsed_ms"));
 }
 @Test void pausedTimeDoesNotIncreaseOnReloadAndResumePreservesAccumulatedTime(){
  var steps=List.of(new HashMap<String,Object>());var events=List.of(event("start",0),event("pause",8));
  ExecutionTiming.apply(execution(1),steps,events,base.plusSeconds(50));assertEquals(8000L,steps.get(0).get("elapsed_ms"));assertEquals(false,steps.get(0).get("timing_running"));
  ExecutionTiming.apply(execution(1),steps,events,base.plusSeconds(80));assertEquals(8000L,steps.get(0).get("elapsed_ms"));
  ExecutionTiming.apply(execution(0),steps,List.of(event("start",0),event("pause",8),event("resume",80)),base.plusSeconds(90));assertEquals(18000L,steps.get(0).get("elapsed_ms"));
 }
 @Test void abortFreezesTheCurrentStepAndDoesNotStartLaterSteps(){
  var e=execution(3);e.put("ended_at",base.plusSeconds(30));
  var steps=List.of(new HashMap<String,Object>(),new HashMap<String,Object>());
  ExecutionTiming.apply(e,steps,List.of(event("pause",10),event("abort",30)),base.plusSeconds(90));
  assertEquals(10000L,steps.get(0).get("elapsed_ms"));assertEquals(false,steps.get(0).get("timing_running"));assertFalse(steps.get(1).containsKey("elapsed_ms"));
 }
}
