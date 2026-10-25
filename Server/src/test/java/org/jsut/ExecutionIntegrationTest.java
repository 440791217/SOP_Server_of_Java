package org.jsut;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.jsut.modules.user.service.*;
import org.jsut.modules.user.dto.LoginRequest;
import tools.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.datasource.url=jdbc:h2:mem:execution_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:login-schema.sql,classpath:project-schema.sql,classpath:sop-schema.sql,classpath:resource-schema.sql,classpath:execution-schema.sql",
 "sop.bootstrap.root.enabled=false","server.port=0","sop.storage.directory=${java.io.tmpdir}/sop-execution-tests","sop.storage.data-root=${java.io.tmpdir}/sop-execution-tests/data"})
class ExecutionIntegrationTest {
 @Autowired JdbcTemplate jdbc;@Autowired AuthService auth;@Autowired RootInitializationService initializer;@Autowired PasswordEncoder encoder;@Autowired ObjectMapper json;@Value("${local.server.port}") int port;
 String root,owner,outsider;final HttpClient http=HttpClient.newHttpClient();
 @BeforeEach void seed(){
  jdbc.update("DELETE FROM sop_camera_config WHERE device_id IS NOT NULL");
  for(String t:List.of("sop_execution_step_clock","sop_execution_evidence","sop_step_detection","sop_execution_event","sop_execution_step","sop_execution","sop_task_content","sop_task_standard","sop_task","sop_step_resource_requirement","sop_device_connection","sop_resource_device","sop_resource_model","sop_project_station","sop_station","sop_location","sop_step_visual","sop_project_binding","sop_subproject_binding","sop_step_attachment","sop_file","sop_step","sop_revision","sop_document","sop_project_activity","sop_subproject","sop_project_member","sop_project","sys_user_session","sys_user_role","sys_role_permission","sys_user","sys_team","sys_role","sys_permission"))jdbc.execute("DELETE FROM "+t);
  jdbc.update("INSERT INTO sys_user(id,username,real_name,status,is_superuser) VALUES(1,'root','Root',0,1)");initializer.initialize("Root123!");String hash=encoder.encode("User123!");
  for(int i=2;i<=3;i++)jdbc.update("INSERT INTO sys_user(id,username,real_name,password_hash,status,must_change_password) VALUES(?,?,?,?,1,0)",i,"user"+i,"用户"+i,hash);
  jdbc.update("INSERT INTO sop_location(id,code,name,created_by,updated_by) VALUES(1,'LOC-BASE','测试区域',1,1)");
  jdbc.update("INSERT INTO sop_project(id,code,name,owner_user_id,status,created_by,updated_by) VALUES(10,'P10','项目A',2,1,1,1),(20,'P20','项目B',3,1,1,1)");
  jdbc.update("INSERT INTO sop_project_member(project_id,user_id,access_level,created_by,updated_by) VALUES(10,1,1,1,1),(10,2,1,1,1),(20,3,1,1,1)");
  jdbc.update("INSERT INTO sop_subproject(id,project_id,code,name,owner_user_id,sort_order,created_by,updated_by) VALUES(11,10,'S11','分部A',2,0,1,1),(21,20,'S21','分部B',3,0,1,1)");
  jdbc.update("UPDATE sop_subproject SET status=1");
  jdbc.update("INSERT INTO sop_document(id,code,name,managing_project_id,owner_user_id,created_by,updated_by) VALUES(30,'SOP30','检查标准',10,2,1,1)");
  jdbc.update("INSERT INTO sop_revision(id,sop_id,revision_no,title,status,published_by,published_at,created_by,updated_by) VALUES(31,30,1,'检查V1',1,1,CURRENT_TIMESTAMP,1,1),(32,30,2,'草稿V2',0,NULL,NULL,1,1)");
  jdbc.update("INSERT INTO sop_step(id,revision_id,sort_order,title,instruction) VALUES(41,31,1,'检查','检查内容'),(42,32,1,'新检查','操作说明')");
  root=auth.login(new LoginRequest("root","Root123!")).access_token();owner=auth.login(new LoginRequest("user2","User123!")).access_token();outsider=auth.login(new LoginRequest("user3","User123!")).access_token();
 }
 HttpResponse<String> send(String method,String path,Object body,String token)throws Exception{var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path));if(token!=null)b.header("Authorization","Bearer "+token);b.header("Content-Type","application/json");return http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());}
 String id(HttpResponse<String> r){assertEquals(200,r.statusCode(),r.body());return JsonPath.read(r.body(),"$.data.id");}
 Map<String,Object> item(String prefix,String name){var m=new HashMap<String,Object>(Map.of("code",prefix+"-202610162030-A7F29C","name",name,"status",0));if(prefix.equals("STA")){m.put("location_id",1);m.put("ip_address","198.51.100.1");}return m;}
 String station()throws Exception{String stationId=id(send("POST","/resource/station",item("STA","工位A"),root));jdbc.update("INSERT INTO sop_project_station(project_id,station_id,created_by) VALUES(10,?,1)",stationId);return stationId;}
 String model()throws Exception{var m=item("TMD","摄像头规格");m.put("type_code","camera");m.put("unit","台");m.put("specification","高清");return id(send("POST","/resource/model",m,root));}
 String device(String mid,String sid)throws Exception{var d=item("TOL","摄像头01");d.put("model_id",mid);d.put("station_id",sid);return id(send("POST","/resource/device",d,root));}
 Map<String,Object> task(String sid){var t=item("TSK","检查任务");t.put("owner_user_id",2);t.put("sort_order",0);if(sid!=null)t.put("station_id",sid);return t;}
 @Autowired org.jsut.modules.sop.service.SopImageStorage imageStorage;
 String tid,sid;
 Map<String,Object> startBody(String code,String key)throws Exception{
  if(tid==null){sid=station();var t=task(sid);t.put("status",1);t.put("sop_id",30);t.put("revision_id",31);tid=id(send("POST","/project/10/subproject/11/task",t,root));}
  var m=new HashMap<String,Object>();m.put("project_id",10);m.put("subproject_id",11);m.put("task_id",tid);m.put("station_id",sid);m.put("revision_id",31);m.put("task_version",0);m.put("product_code",code);m.put("request_key",key);return m;
 }
 @BeforeEach void clearIds(){tid=null;sid=null;}
 byte[] evidencePng()throws Exception{var image=new java.awt.image.BufferedImage(100,100,java.awt.image.BufferedImage.TYPE_INT_RGB);var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);return bytes.toByteArray();}
 String photoExecution(boolean automatic)throws Exception{
  String eid=id(send("POST","/execution",startBody("PHOTO-001","request_photo_start1"),root));
  @SuppressWarnings("unchecked") var snapshot=json.readValue(jdbc.queryForObject("SELECT content_snapshot FROM sop_execution WHERE id=?",String.class,eid),Map.class);
  @SuppressWarnings("unchecked") var step=(Map<String,Object>)((List<?>)snapshot.get("steps")).get(0);
  step.put("detection",Map.of("enabled",automatic,"confirmed",true,"camera_id","77","attachment_id","201"));
  var region=Map.of("id","r1","enabled",true,"detect_type","object","class_name","cup","confidence",75,"x",0,"y",0,"width",1,"height",1);
  step.put("image",Map.of("id","201","visual",Map.of("guide_items",List.of(),"detection_regions",List.of(region,new HashMap<>(Map.of("id","r2","enabled",true,"detect_type","count","class_name","cell phone","confidence",75,"expected_count",1,"x",0,"y",0,"width",1,"height",1))))));
  jdbc.update("UPDATE sop_execution SET content_snapshot=? WHERE id=?",json.writeValueAsString(snapshot),eid);return eid;
 }
 Map<String,Object> photoMetadata(boolean automatic){var m=new HashMap<String,Object>();m.put("version",0);m.put("request_key","evidence_request_0001");m.put("camera_id",77);m.put("image_width",100);m.put("image_height",100);m.put("captured_at",java.time.Instant.now().toString());m.put("automatic",automatic);m.put("model_id","yolov8m");m.put("frame_id","frame-1");m.put("detections",List.of());return m;}
 HttpResponse<String> uploadEvidence(String eid,Map<String,Object> metadata,byte[] bytes,String token)throws Exception{
  String boundary="testEvidenceBoundary";var body=new java.io.ByteArrayOutputStream();
  body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"metadata\"\r\nContent-Type: application/json\r\n\r\n"+json.writeValueAsString(metadata)+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"photo.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));body.write(bytes);body.write(("\r\n--"+boundary+"--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
  return http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/execution/"+eid+"/step/1/evidence")).header("Authorization","Bearer "+token).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
 }
 @AfterEach void clearEvidenceFiles(){for(var row:jdbc.queryForList("SELECT storage_key FROM sop_execution_evidence"))imageStorage.rollbackDelete((String)row.get("storage_key"));}
 @Test void evidenceIsSavedBeforeConfirmationAndReadThroughExecutionAuthorization()throws Exception{
  String eid=photoExecution(false);var metadata=photoMetadata(false);var png=evidencePng();
  assertEquals(400,send("PUT","/execution/"+eid+"/step/1/confirm",Map.of("version",0),root).statusCode());
  var result=uploadEvidence(eid,metadata,png,root);assertEquals(200,result.statusCode(),result.body());
  assertNotNull(JsonPath.read(result.body(),"$.data.steps[0].evidence"));assertEquals("77",JsonPath.read(result.body(),"$.data.steps[0].evidence.camera_id"));
  String key=jdbc.queryForObject("SELECT storage_key FROM sop_execution_evidence WHERE execution_id=?",String.class,eid);assertTrue(key.startsWith("projects/project-10/subproject-11/task-"+tid+"/executions/"));assertTrue(key.contains("PHOTO-001_exec-"+eid+"/step-001_"));assertFalse(key.contains(":"));
  var image=send("GET","/execution/"+eid+"/step/1/evidence",null,root);assertEquals(200,image.statusCode());assertEquals("image/png",image.headers().firstValue("Content-Type").orElse(""));assertEquals(403,send("GET","/execution/"+eid+"/step/1/evidence",null,outsider).statusCode());
  assertEquals(200,uploadEvidence(eid,metadata,png,root).statusCode());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sop_execution_evidence",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT version FROM sop_execution WHERE id=?",Integer.class,eid));
 }
 @Test void rejectsInvalidPhotosAndLeavesStepUnconfirmed()throws Exception{
  String eid=photoExecution(false);var metadata=photoMetadata(false);metadata.put("image_width",99);
  assertEquals(400,uploadEvidence(eid,metadata,evidencePng(),root).statusCode());
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sop_execution_evidence",Integer.class));assertNull(jdbc.queryForObject("SELECT confirmed_at FROM sop_execution_step WHERE execution_id=?",Object.class,eid));
  metadata.put("image_width",100);metadata.put("camera_id",78);assertEquals(400,uploadEvidence(eid,metadata,evidencePng(),root).statusCode());
  metadata.put("camera_id",77);assertEquals(400,uploadEvidence(eid,metadata,new byte[]{1,2,3},root).statusCode());
 }
 @Test void automaticEvidenceRequiresAllSnapshotRegionsInTheSameFrame()throws Exception{
  String eid=photoExecution(true);var metadata=photoMetadata(true);var cup=Map.of("class_name","cup","confidence",0.9,"bbox_xyxy",List.of(10,10,70,70));
  metadata.put("detections",List.of(cup));assertEquals(400,uploadEvidence(eid,metadata,evidencePng(),root).statusCode());assertNull(jdbc.queryForObject("SELECT confirmed_at FROM sop_execution_step WHERE execution_id=?",Object.class,eid));
  metadata.put("detections",List.of(cup,Map.of("class_name","cell phone","confidence",0.6,"bbox_xyxy",List.of(10,10,70,70))));assertEquals(400,uploadEvidence(eid,metadata,evidencePng(),root).statusCode());
  metadata.put("detections",List.of(cup,Map.of("class_name","cell phone","confidence",0.9,"bbox_xyxy",List.of(10,10,70,70))));var passed=uploadEvidence(eid,metadata,evidencePng(),root);assertEquals(200,passed.statusCode(),passed.body());
  assertEquals(2,((List<?>)JsonPath.read(passed.body(),"$.data.steps[0].evidence.detections")).size());
 }
 @Test void startIsIdempotentAndDoesNotDuplicateProductsOrOccupyStationTwice()throws Exception{
  var b=startBody("产品001","request_key_00001");String eid=id(send("POST","/execution",b,root));
  assertEquals(eid,id(send("POST","/execution",b,root)));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sop_execution",Integer.class));
  var other=new HashMap<>(b);other.put("request_key","request_key_00002");other.put("product_code","产品002");assertEquals(409,send("POST","/execution",other,root).statusCode());
  assertEquals(200,send("PUT","/execution/"+eid+"/abort",Map.of("version",0,"reason","测试中止"),root).statusCode());
  assertEquals(200,send("POST","/execution",other,root).statusCode());
  b.put("request_key","request_key_00003");assertEquals(409,send("POST","/execution",b,root).statusCode());
 }
 @Test void unstartedProjectBlocksOnsiteUseUntilProjectStarts()throws Exception{
  var body=startBody("P-start","request_project_start");
  jdbc.update("UPDATE sop_project SET status=0 WHERE id=10");
  var tasks=send("GET","/resource/station/"+sid+"/task",null,root);
  assertEquals(false,JsonPath.read(tasks.body(),"$.data.items[0].onsite_available"));
  assertTrue(((List<?>)JsonPath.read(tasks.body(),"$.data.items[0].onsite_unavailable_reasons")).contains("项目尚未开始，请先开始项目"));
  var rejected=send("POST","/execution",body,root);assertEquals(409,rejected.statusCode(),rejected.body());
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sop_execution",Integer.class));
  jdbc.update("UPDATE sop_project SET status=1 WHERE id=10");
  assertEquals(true,JsonPath.read(send("GET","/resource/station/"+sid+"/task",null,root).body(),"$.data.items[0].onsite_available"));
  assertEquals(200,send("POST","/execution",body,root).statusCode());
 }
 @Test void unstartedSubprojectAllowsPreviewButBlocksNewWorkAndKeepsExistingWork()throws Exception{
  var body=startBody("S-start","request_subproject_start");
  jdbc.update("UPDATE sop_subproject SET status=0 WHERE id=11");
  var tasks=send("GET","/resource/station/"+sid+"/task",null,root);
  assertEquals(false,JsonPath.read(tasks.body(),"$.data.items[0].onsite_available"));
  assertTrue(((List<?>)JsonPath.read(tasks.body(),"$.data.items[0].onsite_unavailable_reasons")).contains("子项目尚未开始，请先开始子项目"));
  assertEquals(200,send("GET","/project/10/subproject/11/task/"+tid+"/preview",null,root).statusCode());
  assertEquals(409,send("POST","/execution",body,root).statusCode());
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sop_execution",Integer.class));
  jdbc.update("UPDATE sop_subproject SET status=1 WHERE id=11");
  String eid=id(send("POST","/execution",body,root));
  jdbc.update("UPDATE sop_subproject SET status=2 WHERE id=11");
  assertEquals(false,JsonPath.read(send("GET","/resource/station/"+sid+"/task",null,root).body(),"$.data.items[0].onsite_available"));
  assertEquals(200,send("PUT","/execution/"+eid+"/step/1/confirm",Map.of("version",0),root).statusCode());
  assertEquals(200,send("PUT","/execution/"+eid+"/complete",Map.of("version",1),root).statusCode());
 }
 @Test void stepsMustBeConfirmedInOrderAndPauseNeedsReason()throws Exception{
  jdbc.update("INSERT INTO sop_step(id,revision_id,sort_order,title,instruction) VALUES(43,31,2,'复核','核对标签')");
  String eid=id(send("POST","/execution",startBody("P001","request_key_00001"),root));
  assertEquals(409,send("PUT","/execution/"+eid+"/step/2/confirm",Map.of("version",0),root).statusCode());
  assertEquals(409,send("PUT","/execution/"+eid+"/complete",Map.of("version",0),root).statusCode());
  assertEquals(400,send("PUT","/execution/"+eid+"/pause",Map.of("version",0),root).statusCode());
  assertEquals(200,send("PUT","/execution/"+eid+"/pause",Map.of("version",0,"reason","来料异常"),root).statusCode());
  assertEquals(409,send("PUT","/execution/"+eid+"/step/1/confirm",Map.of("version",1),root).statusCode());
  assertEquals(200,send("PUT","/execution/"+eid+"/resume",Map.of("version",1),root).statusCode());
  assertEquals(200,send("PUT","/execution/"+eid+"/step/1/confirm",Map.of("version",2),root).statusCode());
  assertEquals(409,send("PUT","/execution/"+eid+"/step/2/confirm",Map.of("version",2),root).statusCode());
  assertEquals(200,send("PUT","/execution/"+eid+"/step/2/confirm",Map.of("version",3),root).statusCode());
  var done=send("PUT","/execution/"+eid+"/complete",Map.of("version",4),root);assertEquals(200,done.statusCode(),done.body());assertEquals(2,((Number)JsonPath.read(done.body(),"$.data.status")).intValue());
  assertEquals(409,send("PUT","/execution/"+eid+"/abort",Map.of("version",5,"reason","结束后不能修改"),root).statusCode());assertNull(jdbc.queryForObject("SELECT active_station_id FROM sop_execution WHERE id=?",Long.class,eid));
 }
 Map<String,Object> heartbeat(String action,String session,long sequence,long elapsed){return Map.of("action",action,"position",1,"session_id",session,"sequence",sequence,"elapsed_ms",elapsed);}
 @Test void heartbeatPersistsRemainingTimeDeduplicatesAndRestoresWithoutCountingDisconnection()throws Exception{
  jdbc.update("INSERT INTO sop_step(id,revision_id,sort_order,title,instruction) VALUES(43,31,2,'复核','核对标签')");
  jdbc.update("UPDATE sop_step SET expected_duration_seconds=30 WHERE id=41");
  String eid=id(send("POST","/execution",startBody("CLOCK-1","request_clock_00001"),root));String key="clock_session_00001";
  assertEquals(200,send("POST","/execution/"+eid+"/clock",heartbeat("claim",key,0,0),root).statusCode());
  jdbc.update("UPDATE sop_execution_step_clock SET heartbeat_at=? WHERE execution_id=?",java.time.LocalDateTime.now().minusSeconds(5),eid);
  var beat=send("POST","/execution/"+eid+"/clock",heartbeat("heartbeat",key,1,5000),root);assertEquals(200,beat.statusCode(),beat.body());
  assertEquals(25000L,((Number)JsonPath.read(beat.body(),"$.data.remaining_ms")).longValue());
  assertEquals(200,send("POST","/execution/"+eid+"/clock",heartbeat("heartbeat",key,1,5000),root).statusCode());
  assertEquals(5000L,jdbc.queryForObject("SELECT elapsed_ms FROM sop_execution_step_clock WHERE execution_id=?",Long.class,eid));
  assertEquals(409,send("POST","/execution/"+eid+"/clock",heartbeat("claim","clock_session_00002",0,0),root).statusCode());
  jdbc.update("UPDATE sop_execution_step_clock SET heartbeat_at=? WHERE execution_id=?",java.time.LocalDateTime.now().minusSeconds(16),eid);
  assertEquals(409,send("POST","/execution/"+eid+"/clock",heartbeat("heartbeat",key,2,5000),root).statusCode());
  var view=send("GET","/execution/"+eid,null,root);assertEquals(false,JsonPath.read(view.body(),"$.data.steps[0].timing_running"));assertEquals(5000L,((Number)JsonPath.read(view.body(),"$.data.steps[0].elapsed_ms")).longValue());
  var restored=send("POST","/execution/"+eid+"/clock",heartbeat("claim","clock_session_00002",0,0),root);assertEquals(200,restored.statusCode(),restored.body());assertEquals(25000L,((Number)JsonPath.read(restored.body(),"$.data.remaining_ms")).longValue());
  assertEquals(409,send("POST","/execution/"+eid+"/clock",heartbeat("heartbeat",key,2,1000),root).statusCode());
  assertEquals(409,send("PUT","/execution/"+eid+"/step/1/confirm",Map.of("version",0,"clock_session_id",key),root).statusCode());
  assertEquals(200,send("PUT","/execution/"+eid+"/step/1/confirm",Map.of("version",0,"clock_session_id","clock_session_00002"),root).statusCode());
  assertEquals(409,send("PUT","/execution/"+eid+"/step/2/confirm",Map.of("version",1,"clock_session_id","clock_session_00002"),root).statusCode());
  assertEquals(200,send("POST","/execution/"+eid+"/clock",Map.of("action","claim","position",2,"session_id","clock_session_00003","sequence",0,"elapsed_ms",0),root).statusCode());
 }
 @Test void releaseStopsTimeImmediatelyAndAllowsANewSessionForUnlimitedSteps()throws Exception{
  String eid=id(send("POST","/execution",startBody("CLOCK-2","request_clock_00002"),root));String key="clock_session_00001";
  assertEquals(200,send("POST","/execution/"+eid+"/clock",heartbeat("claim",key,0,0),root).statusCode());
  assertNull(jdbc.queryForObject("SELECT remaining_ms FROM sop_execution_step_clock WHERE execution_id=?",Long.class,eid));
  assertEquals(200,send("POST","/execution/"+eid+"/clock",heartbeat("release",key,1,100),root).statusCode());
  assertEquals(409,send("POST","/execution/"+eid+"/clock",heartbeat("heartbeat",key,2,100),root).statusCode());
  assertEquals(200,send("POST","/execution/"+eid+"/clock",heartbeat("claim","clock_session_00002",0,0),root).statusCode());
  assertEquals(100L,jdbc.queryForObject("SELECT elapsed_ms FROM sop_execution_step_clock WHERE execution_id=?",Long.class,eid));
  assertEquals(200,send("PUT","/execution/"+eid+"/pause",Map.of("version",0,"reason","暂停"),root).statusCode());
  assertEquals(409,send("POST","/execution/"+eid+"/clock",heartbeat("heartbeat","clock_session_00002",1,100),root).statusCode());
 }
 @Test void executionKeepsOriginalVersionAndContentWhenTaskChanges()throws Exception{
  String eid=id(send("POST","/execution",startBody("P001","request_key_00001"),root));
  jdbc.update("UPDATE sop_task_standard SET revision_id=32 WHERE task_id=?",tid);jdbc.update("UPDATE sop_revision SET status=1,published_by=1,published_at=CURRENT_TIMESTAMP WHERE id=32");jdbc.update("UPDATE sop_task SET name='新任务名称',station_id=NULL WHERE id=?",tid);
  var detail=send("GET","/execution/"+eid,null,root);assertEquals(200,detail.statusCode());assertEquals("31",JsonPath.read(detail.body(),"$.data.revision_id"));assertEquals("检查内容",JsonPath.read(detail.body(),"$.data.content.steps[0].instruction"));assertEquals("检查任务",JsonPath.read(detail.body(),"$.data.task_name"));
  assertEquals(200,send("PUT","/execution/"+eid+"/step/1/confirm",Map.of("version",0),root).statusCode());
 }
 @Test void permissionsRestrictRecordsAndOnlyOriginalOperatorCanMutate()throws Exception{
  String eid=id(send("POST","/execution",startBody("P001","request_key_00001"),root));
  assertEquals(403,send("GET","/execution/"+eid,null,outsider).statusCode());
  jdbc.update("INSERT INTO sys_role(id,code,status,name) VALUES(18,'resource_reader',1,'资源查询')");jdbc.update("INSERT INTO sys_permission(id,code) VALUES(18,'resource:read')");jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(18,18)");jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(2,18),(3,18)");owner=auth.login(new LoginRequest("user2","User123!")).access_token();outsider=auth.login(new LoginRequest("user3","User123!")).access_token();
  assertEquals(200,send("GET","/execution/"+eid,null,owner).statusCode());assertEquals(403,send("PUT","/execution/"+eid+"/abort",Map.of("version",0,"reason","越权"),owner).statusCode());
  assertEquals(403,send("GET","/execution/"+eid,null,outsider).statusCode());assertEquals(0,((Number)JsonPath.read(send("GET","/execution",null,outsider).body(),"$.data.total")).intValue());
  assertEquals(404,send("GET","/execution/"+eid+"/image/999",null,root).statusCode());
 }
 @Test void rejectsStaleVersionAndUnpublishedTasksAndFiltersRecords()throws Exception{
  var b=startBody("P001","request_key_00001");b.put("task_version",3);assertEquals(409,send("POST","/execution",b,root).statusCode());b.put("task_version",0);
  jdbc.update("UPDATE sop_task_standard SET active=0 WHERE task_id=?",tid);assertEquals(409,send("POST","/execution",b,root).statusCode());jdbc.update("UPDATE sop_task_standard SET active=1 WHERE task_id=?",tid);jdbc.update("UPDATE sop_task SET status=0 WHERE id=?",tid);
  id(send("POST","/execution",b,root));var found=send("GET","/execution?product_code=P001&station_id="+sid+"&status=0",null,root);assertEquals(1,((Number)JsonPath.read(found.body(),"$.data.total")).intValue());assertEquals(0,((Number)JsonPath.read(send("GET","/execution?product_code=UNKNOWN",null,root).body(),"$.data.total")).intValue());
 }

 @Test void executionImagesRemainAuthorizedForOriginalRevision()throws Exception{
  var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
  var stored=imageStorage.save(new org.springframework.mock.web.MockMultipartFile("file","step.png","image/png",bytes.toByteArray()));
  try{
   jdbc.update("INSERT INTO sop_file(id,storage_key,original_name,mime_type,file_kind,byte_size,sha256,uploaded_by) VALUES(101,?,?,?,0,?,?,1)",stored.key(),stored.name(),stored.mime(),stored.bytes().length,stored.sha());
   jdbc.update("INSERT INTO sop_step_attachment(step_id,file_id,sort_order) VALUES(41,101,1)");
   String eid=id(send("POST","/execution",startBody("P001","request_key_image1"),root));
   jdbc.update("UPDATE sop_revision SET status=1,published_by=1,published_at=CURRENT_TIMESTAMP WHERE id=32");jdbc.update("UPDATE sop_task_standard SET revision_id=32 WHERE task_id=?",tid);
   var picture=send("GET","/execution/"+eid+"/image/101",null,root);assertEquals(200,picture.statusCode());assertEquals("image/png",picture.headers().firstValue("Content-Type").orElse(""));
   assertEquals(404,send("GET","/project/10/subproject/11/task/"+tid+"/image/101",null,root).statusCode());assertEquals(403,send("GET","/execution/"+eid+"/image/101",null,outsider).statusCode());
  }finally{imageStorage.rollbackDelete(stored.key());}
 }
 @Test void concurrentStartRequestsCreateOnlyOneExecution()throws Exception{
  var b=startBody("P001","request_concurrent1");
  var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
  try{
   var first=pool.submit(()->send("POST","/execution",b,root));var other=new HashMap<>(b);other.put("product_code","P002");other.put("request_key","request_concurrent2");var second=pool.submit(()->send("POST","/execution",other,root));
   var codes=new ArrayList<>(List.of(first.get().statusCode(),second.get().statusCode()));Collections.sort(codes);assertEquals(List.of(200,409),codes);assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sop_execution",Integer.class));
  }finally{pool.shutdownNow();}
 }
 @Test void rootMayAbortButNotConfirmAnotherOperatorsExecution()throws Exception{
  var b=startBody("P001","request_operator1");
  jdbc.update("INSERT INTO sys_role(id,code,status,name) VALUES(18,'operator',1,'操作员')");jdbc.update("INSERT INTO sys_permission(id,code) VALUES(18,'resource:read'),(19,'resource:use')");jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(18,18),(18,19)");jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(2,18)");owner=auth.login(new LoginRequest("user2","User123!")).access_token();
  String eid=id(send("POST","/execution",b,owner));assertEquals(403,send("PUT","/execution/"+eid+"/step/1/confirm",Map.of("version",0),root).statusCode());
  assertEquals(200,send("PUT","/execution/"+eid+"/abort",Map.of("version",0,"reason","操作员离岗，管理员中止释放工位"),root).statusCode());assertEquals(1,jdbc.queryForObject("SELECT actor_id FROM sop_execution_event WHERE execution_id=? AND action='abort'",Integer.class,eid));
 }

 @Test void rootTaskDeleteBlocksActiveWorkAndKeepsExecutionHistory()throws Exception{
  String eid=id(send("POST","/execution",startBody("DELETE-TASK","request_delete_task1"),root));String path="/project/10/subproject/11/task/"+tid;
  assertEquals(403,send("DELETE",path,Map.of("version",0),owner).statusCode());assertEquals(409,send("DELETE",path,Map.of("version",0),root).statusCode());
  assertEquals(200,send("PUT","/execution/"+eid+"/abort",Map.of("version",0,"reason","删除前中止"),root).statusCode());
  assertEquals(409,send("DELETE",path,Map.of("version",9),root).statusCode());assertEquals(200,send("DELETE",path,Map.of("version",0),root).statusCode());assertEquals("TASK_DELETED",jdbc.queryForObject("SELECT action FROM sop_project_activity WHERE project_id=10 ORDER BY id DESC LIMIT 1",String.class));
  assertEquals(0,((List<?>)JsonPath.read(send("GET","/project/10/subproject/11/task",null,root).body(),"$.data.items")).size());assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM sop_task WHERE id=?",java.sql.Timestamp.class,tid));
  assertEquals(200,send("GET","/execution/"+eid,null,root).statusCode());assertEquals(1,((Number)JsonPath.read(send("GET","/execution?product_code=DELETE-TASK",null,root).body(),"$.data.total")).intValue());
 }
 @Test void rootSubprojectDeleteRemovesTasksButNotSiblings()throws Exception{
  startBody("P001","request_delete_sub1");jdbc.update("UPDATE sop_subproject SET status=3 WHERE id=11");
  assertEquals(403,send("DELETE","/project/10/subproject/11",Map.of("version",0),owner).statusCode());
  assertEquals(true,JsonPath.read(send("GET","/project/10/subproject/11",null,root).body(),"$.data.can_delete"));
  assertEquals(200,send("DELETE","/project/10/subproject/11",Map.of("version",0),root).statusCode());assertEquals("SUBPROJECT_DELETED",jdbc.queryForObject("SELECT action FROM sop_project_activity WHERE project_id=10 ORDER BY id DESC LIMIT 1",String.class));assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM sop_task WHERE id=?",java.sql.Timestamp.class,tid));
  assertEquals(404,send("GET","/project/10/subproject/11",null,root).statusCode());assertNull(jdbc.queryForObject("SELECT deleted_at FROM sop_subproject WHERE id=21",java.sql.Timestamp.class));
 }
 @Test void rootProjectDeleteCascadesAfterActiveExecutionEndsAndRetainsSnapshot()throws Exception{
  String eid=id(send("POST","/execution",startBody("DELETE-PROJECT","request_delete_proj1"),root));
  assertEquals(409,send("DELETE","/project/10",Map.of("version",0),root).statusCode());assertEquals(200,send("GET","/project/10/subproject/11",null,root).statusCode());
  assertEquals(200,send("PUT","/execution/"+eid+"/abort",Map.of("version",0,"reason","准备删除项目"),root).statusCode());jdbc.update("UPDATE sop_project SET status=3 WHERE id=10");
  assertEquals(false,JsonPath.read(send("GET","/project/10",null,owner).body(),"$.data.can_delete"));assertEquals(200,send("DELETE","/project/10",Map.of("version",0),root).statusCode());assertEquals("PROJECT_DELETED",jdbc.queryForObject("SELECT action FROM sop_project_activity WHERE project_id=10 ORDER BY id DESC LIMIT 1",String.class));
  assertEquals(404,send("GET","/project/10",null,root).statusCode());assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM sop_subproject WHERE id=11",java.sql.Timestamp.class));assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM sop_task WHERE id=?",java.sql.Timestamp.class,tid));
  assertEquals(200,send("GET","/execution/"+eid,null,root).statusCode());assertEquals(1,((Number)JsonPath.read(send("GET","/execution?product_code=DELETE-PROJECT",null,root).body(),"$.data.total")).intValue());
  assertEquals(0,((List<?>)JsonPath.read(send("GET","/resource/station/"+sid+"/task",null,root).body(),"$.data.items")).size());
 }
 @Test void deleteAndStartCannotLeaveActiveWorkUnderDeletedProject()throws Exception{
  var b=startBody("P001","request_delete_race1");var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
  try{var start=pool.submit(()->send("POST","/execution",b,root));var remove=pool.submit(()->send("DELETE","/project/10",Map.of("version",0),root));int sc=start.get().statusCode(),dc=remove.get().statusCode();assertTrue(sc==200&&dc==409||sc==404&&dc==200,"start="+sc+",delete="+dc);
   assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sop_execution e JOIN sop_project p ON p.id=e.project_id WHERE e.status IN(0,1) AND p.deleted_at IS NOT NULL",Integer.class));
  }finally{pool.shutdownNow();}
 }
}
