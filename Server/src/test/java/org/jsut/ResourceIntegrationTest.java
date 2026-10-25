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
 "spring.datasource.url=jdbc:h2:mem:resource_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:login-schema.sql,classpath:project-schema.sql,classpath:sop-schema.sql,classpath:resource-schema.sql",
 "sop.bootstrap.root.enabled=false","server.port=0"})
class ResourceIntegrationTest {
 @org.junit.jupiter.api.io.TempDir static java.nio.file.Path cameraFiles;
 @org.springframework.test.context.DynamicPropertySource static void cameraStorage(org.springframework.test.context.DynamicPropertyRegistry r){r.add("sop.storage.directory",()->cameraFiles.toString());r.add("sop.storage.data-root",()->cameraFiles.resolve("data").toString());}
 @org.springframework.test.context.bean.override.mockito.MockitoBean org.jsut.modules.resource.service.CameraMediaService camera;
 @Autowired JdbcTemplate jdbc;@Autowired AuthService auth;@Autowired RootInitializationService initializer;@Autowired PasswordEncoder encoder;@Autowired ObjectMapper json;@Value("${local.server.port}") int port;
 String root,owner,outsider;final HttpClient http=HttpClient.newHttpClient();
 @Test void authenticatedRtspIsSavedWithDeviceAndOnlyManagersCanReadCredentials()throws Exception{
  String mid=model();var device=item("TOL","认证摄像头");device.put("model_id",mid);
  String address="rtsp://operator:p%40ss@192.0.2.10:554/live?channel=1";
  device.put("connection",Map.of("transport","network","protocol","rtsp","rtsp_url",address,"enabled",true));
  String did=id(send("POST","/resource/device",device,root));
  assertEquals(address,JsonPath.read(send("GET","/resource/device/"+did,null,root).body(),"$.data.connection.rtsp_url"));
  assertEquals(200,send("POST","/resource/device/"+did+"/snapshot",null,root).statusCode());
  org.mockito.Mockito.verify(camera).snapshot(address);
  var list=send("GET","/resource/device",null,root);assertFalse(list.body().contains("p%40ss"));assertFalse(list.body().contains("operator@"));
  jdbc.update("INSERT INTO sys_role(id,code,status,name) VALUES(18,'camera_reader',1,'资源查询')");jdbc.update("INSERT INTO sys_permission(id,code) VALUES(18,'resource:read')");jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(18,18)");jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(3,18)");outsider=auth.login(new LoginRequest("user3","User123!")).access_token();
  var detail=send("GET","/resource/device/"+did,null,outsider);assertEquals(200,detail.statusCode());assertFalse(detail.body().contains("p%40ss"));assertFalse(detail.body().contains("operator"));
  device.put("version",0);device.put("name","更名后摄像头");assertEquals(200,send("PUT","/resource/device/"+did,device,root).statusCode());
  assertEquals(address,jdbc.queryForObject("SELECT rtsp_url FROM sop_device_connection WHERE device_id=?",String.class,did));
 }
 @Test void rtspConfigurationCaptureUploadAndAnnotationRoundTrip()throws Exception{
  String sid=station(),mid=model();var d=item("TOL","RTSP摄像头");d.put("model_id",mid);d.put("station_id",sid);
  var c=new HashMap<String,Object>(Map.of("transport","network","protocol","rtsp","rtsp_url","rtsp://192.0.2.10/live?channel=1&subtype=0","enabled",true));d.put("connection",c);
  String did=id(send("POST","/resource/device",d,root));var detail=send("GET","/resource/device/"+did,null,root);
  assertEquals(c.get("rtsp_url"),JsonPath.read(detail.body(),"$.data.connection.rtsp_url"));
  assertEquals("192.0.2.10",JsonPath.read(detail.body(),"$.data.connection.host"));assertEquals(554,((Number)JsonPath.read(detail.body(),"$.data.connection.port")).intValue());assertEquals("/live?channel=1&subtype=0",JsonPath.read(detail.body(),"$.data.connection.path"));
  var pixels=new java.awt.image.BufferedImage(3,3,java.awt.image.BufferedImage.TYPE_INT_RGB);var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(pixels,"jpeg",bytes);
  org.mockito.Mockito.when(camera.snapshot(org.mockito.ArgumentMatchers.anyString())).thenReturn(bytes.toByteArray());
  assertEquals(200,send("POST","/resource/device/connection/test",c,root).statusCode());
  assertEquals(403,send("POST","/resource/device/connection/test",c,outsider).statusCode());
  assertEquals(403,send("POST","/resource/device/"+did+"/snapshot",null,outsider).statusCode());
  var snapshot=http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/resource/device/"+did+"/snapshot")).header("Authorization","Bearer "+root).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofByteArray());
  assertEquals(200,snapshot.statusCode());assertArrayEquals(bytes.toByteArray(),snapshot.body());
  org.mockito.Mockito.verify(camera,org.mockito.Mockito.atLeastOnce()).snapshot("rtsp://192.0.2.10:554/live?channel=1&subtype=0");
  var feed=org.mockito.Mockito.mock(org.jsut.modules.resource.service.CameraMediaService.Feed.class);
  org.mockito.Mockito.doAnswer(invocation->{var output=(java.io.OutputStream)invocation.getArgument(0);output.write("--camera\r\nContent-Type: image/jpeg\r\nContent-Length: 4\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));output.write(new byte[]{(byte)255,(byte)216,(byte)255,(byte)217});output.flush();return null;}).when(feed).writeTo(org.mockito.ArgumentMatchers.any());
  org.mockito.Mockito.when(camera.preview(org.mockito.ArgumentMatchers.anyString())).thenReturn(feed);
  var stream=send("GET","/resource/device/"+did+"/stream",null,root);assertEquals(200,stream.statusCode());assertTrue(stream.headers().firstValue("Content-Type").orElse("").startsWith("multipart/x-mixed-replace"));assertTrue(stream.body().contains("Content-Length: 4"));assertEquals("no-store",stream.headers().firstValue("Cache-Control").orElse(""));
  assertEquals(403,send("GET","/resource/device/"+did+"/stream",null,outsider).statusCode());
  String boundary="camera-test";var multipart=new java.io.ByteArrayOutputStream();
  multipart.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"version\"\r\n\r\n0\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"camera.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));multipart.write(snapshot.body());multipart.write(("\r\n--"+boundary+"--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
  var uploaded=http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/sop/30/version/32/step/42/image")).header("Authorization","Bearer "+root).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(multipart.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
  assertEquals(200,uploaded.statusCode(),uploaded.body());String aid=JsonPath.read(uploaded.body(),"$.data.steps[0].attachments[0].id");
  var visual=Map.of("attachment_id",aid,"schema_version",1,"image_width",3,"image_height",3,"guide_items",List.of(Map.of("id","camera-mark","kind","rect","name","摄像头标注","color","#409EFF","x",0.1,"y",0.1,"width",0.5,"height",0.5)),"detection_regions",List.of());
  var saved=send("PUT","/sop/30/version/32/step/42/authoring",Map.of("step",Map.of("title","新检查","instruction","操作说明","version",0,"revision_version",1),"visuals",List.of(visual)),root);
  assertEquals(200,saved.statusCode(),saved.body());var loaded=send("GET","/sop/30/version/32",null,root);assertEquals("摄像头标注",JsonPath.read(loaded.body(),"$.data.steps[0].attachments[0].visual.guide_items[0].name"));
  c.put("rtsp_url","http://192.0.2.10/live");c.put("device_version",0);assertEquals(400,send("PUT","/resource/device/"+did+"/connection",c,root).statusCode());
  c.put("rtsp_url","rtsp://192.0.2.10/live");c.put("enabled",false);assertEquals(200,send("PUT","/resource/device/"+did+"/connection",c,root).statusCode());assertEquals(400,send("POST","/resource/device/"+did+"/snapshot",null,root).statusCode());
 }
 @BeforeEach void seed(){
  jdbc.update("DELETE FROM sop_camera_config WHERE device_id IS NOT NULL");
  jdbc.update("UPDATE sop_camera_config SET refresh_fps=0,detect_fps=2,version=0,video_window_seconds=2,detection_window_seconds=5,update_interval_seconds=1 WHERE device_id IS NULL");
  org.mockito.Mockito.when(camera.snapshot(org.mockito.ArgumentMatchers.anyString())).thenReturn(new byte[]{(byte)255,(byte)216,(byte)255,(byte)217});
  for(String t:List.of("sop_task_content","sop_task_standard","sop_task","sop_step_resource_requirement","sop_device_connection","sop_resource_device","sop_resource_model","sop_project_station","sop_station","sop_location","sop_step_visual","sop_project_binding","sop_subproject_binding","sop_step_attachment","sop_file","sop_step","sop_revision","sop_document","sop_project_activity","sop_subproject","sop_project_member","sop_project","sys_user_session","sys_user_role","sys_role_permission","sys_user","sys_team","sys_role","sys_permission"))jdbc.execute("DELETE FROM "+t);
  jdbc.update("INSERT INTO sys_user(id,username,real_name,status,is_superuser) VALUES(1,'root','Root',0,1)");initializer.initialize("Root123!");String hash=encoder.encode("User123!");
  for(int i=2;i<=3;i++)jdbc.update("INSERT INTO sys_user(id,username,real_name,password_hash,status,must_change_password) VALUES(?,?,?,?,1,0)",i,"user"+i,"用户"+i,hash);
  jdbc.update("INSERT INTO sop_location(id,code,name,created_by,updated_by) VALUES(1,'LOC-BASE','测试区域',1,1)");
  jdbc.update("INSERT INTO sop_project(id,code,name,owner_user_id,created_by,updated_by) VALUES(10,'P10','项目A',2,1,1),(20,'P20','项目B',3,1,1)");
  jdbc.update("INSERT INTO sop_project_member(project_id,user_id,access_level,created_by,updated_by) VALUES(10,1,1,1,1),(10,2,1,1,1),(20,3,1,1,1)");
  jdbc.update("INSERT INTO sop_subproject(id,project_id,code,name,owner_user_id,sort_order,created_by,updated_by) VALUES(11,10,'S11','分部A',2,0,1,1),(21,20,'S21','分部B',3,0,1,1)");
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
 @Test void systemAppearanceSettingsAreSeparateValidatedAndAtomic() throws Exception {
  jdbc.update("UPDATE sop_system_setting SET version=0");
  assertEquals(401, send("GET", "/system/settings/appearance", null, null).statusCode());
  var values = Map.of("theme", "#13ce66", "tagsView", false, "fixedHeader", true, "sidebarLogo", true);
  var versions = Map.of("theme", 0, "tagsView", 0, "fixedHeader", 0, "sidebarLogo", 0);
  var update = Map.of("values", values, "versions", versions);
  assertEquals(403, send("PUT", "/system/settings/appearance", update, outsider).statusCode());
  var saved = send("PUT", "/system/settings/appearance", update, root);
  assertEquals(200, saved.statusCode(), saved.body());
  assertEquals("#13ce66", JsonPath.<Object>read(saved.body(), "$.data.values.theme"));
  assertEquals(1L, jdbc.queryForObject("SELECT updated_by FROM sop_system_setting WHERE config_key='appearance.theme'", Long.class));
  assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sop_system_setting WHERE config_key LIKE 'camera.%'", Integer.class));
  var stale = Map.of("theme", 0, "tagsView", 1, "fixedHeader", 1, "sidebarLogo", 1);
  var changedValues = Map.of("theme", "#409eff", "tagsView", true, "fixedHeader", false, "sidebarLogo", false);
  assertEquals(409, send("PUT", "/system/settings/appearance", Map.of("values", changedValues, "versions", stale), root).statusCode());
  assertEquals("true", jdbc.queryForObject("SELECT config_value FROM sop_system_setting WHERE config_key='appearance.fixedHeader'", String.class));
  assertEquals(1, jdbc.queryForObject("SELECT version FROM sop_system_setting WHERE config_key='appearance.fixedHeader'", Integer.class));
  var invalid = new HashMap<String, Object>(values);
  invalid.put("theme", "javascript:bad");
  assertEquals(400, send("PUT", "/system/settings/appearance", Map.of("values", invalid, "versions", stale), root).statusCode());
  invalid.put("camera.refresh_fps", 30);
  assertEquals(400, send("PUT", "/system/settings/appearance", Map.of("values", invalid, "versions", stale), root).statusCode());
 }

 @Test void cameraConfigHasOneDefaultAndRetainsOverrideAudit() throws Exception {
  assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sop_camera_config WHERE device_id IS NULL", Integer.class));
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("""
      INSERT INTO sop_camera_config(device_id,refresh_fps,detect_fps,video_window_seconds,detection_window_seconds,update_interval_seconds)
      VALUES(NULL,0,2,2,5,1)
      """));
  String sid = station();
  String mid = model();
  String did = device(mid, sid);
  var edit = item("TOL", "摄像头01");
  edit.put("model_id", mid);
  edit.put("station_id", sid);
  edit.put("version", 0);
  edit.put("frame_rates", Map.of("refresh_fps", 0, "detect_fps", 5));
  assertEquals(200, send("PUT", "/resource/device/" + did, edit, root).statusCode());
  assertEquals(1L, jdbc.queryForObject("SELECT updated_by FROM sop_camera_config WHERE device_id=?", Long.class, did));
  assertEquals(5, jdbc.queryForObject("SELECT detect_fps FROM sop_camera_config WHERE device_id=?", Integer.class, did));
 }

 @Test void statisticsSettingsAreValidatedVersionedAndPreservedForOlderClients() throws Exception {
  var initial = send("GET", "/resource/frame-rates", null, root);
  assertEquals(2, JsonPath.<Object>read(initial.body(), "$.data.video_window_seconds"));
  assertEquals(5, JsonPath.<Object>read(initial.body(), "$.data.detection_window_seconds"));
  var update = Map.of("refresh_fps", 0, "detect_fps", 2, "version", 0,
      "video_window_seconds", 3, "detection_window_seconds", 8, "update_interval_seconds", 2);
  assertEquals(403, send("PUT", "/resource/frame-rates", update, outsider).statusCode());
  var saved = send("PUT", "/resource/frame-rates", update, root);
  assertEquals(200, saved.statusCode(), saved.body());
  assertEquals(8, JsonPath.<Object>read(saved.body(), "$.data.detection_window_seconds"));
  assertEquals(409, send("PUT", "/resource/frame-rates", update, root).statusCode());
  var invalid = new HashMap<String, Object>(update);
  invalid.put("version", 1);
  invalid.put("video_window_seconds", 0);
  assertEquals(400, send("PUT", "/resource/frame-rates", invalid, root).statusCode());
  invalid.put("video_window_seconds", 31);
  assertEquals(400, send("PUT", "/resource/frame-rates", invalid, root).statusCode());
  var legacy = send("PUT", "/resource/frame-rates", Map.of("refresh_fps", 10, "detect_fps", 4, "version", 1), root);
  assertEquals(200, legacy.statusCode(), legacy.body());
  assertEquals(3, JsonPath.<Object>read(legacy.body(), "$.data.video_window_seconds"));
  assertEquals(8, JsonPath.<Object>read(legacy.body(), "$.data.detection_window_seconds"));
  assertEquals(2, JsonPath.<Object>read(legacy.body(), "$.data.update_interval_seconds"));
 }

 @Test void captureConfigurationDoesNotStartJavaMedia()throws Exception{
  String sid=station(),mid=model(),did=device(mid,sid);
  var connection=Map.of("transport","network","protocol","rtsp","rtsp_url","rtsp://operator:camera-password@192.0.2.10:554/live","enabled",true,"device_version",0);
  assertEquals(200,send("PUT","/resource/device/"+did+"/connection",connection,root).statusCode());
  org.mockito.Mockito.clearInvocations(camera);
  var config=send("GET","/resource/device/"+did+"/capture-config",null,root);
  assertEquals(200,config.statusCode(),config.body());assertEquals("no-store",config.headers().firstValue("Cache-Control").orElse(""));
  assertEquals(did,JsonPath.read(config.body(),"$.data.device_id"));assertEquals(1,((Number)JsonPath.read(config.body(),"$.data.config_version")).intValue());
  assertEquals("rtsp://operator:camera-password@192.0.2.10:554/live",JsonPath.read(config.body(),"$.data.rtsp_url"));
  assertEquals(0,JsonPath.<Object>read(config.body(),"$.data.frame_rates.refresh_fps"));assertEquals(2,JsonPath.<Object>read(config.body(),"$.data.frame_rates.detect_fps"));
  var globals=Map.of("refresh_fps",30,"detect_fps",4,"version",0);
  assertEquals(403,send("PUT","/resource/frame-rates",globals,outsider).statusCode());
  assertEquals(200,send("PUT","/resource/frame-rates",globals,root).statusCode());
  assertEquals(409,send("PUT","/resource/frame-rates",globals,root).statusCode());
  assertEquals(400,send("PUT","/resource/frame-rates",Map.of("refresh_fps",-1,"detect_fps",0,"version",1),root).statusCode());
  var inherited=send("GET","/resource/device/"+did+"/capture-config",null,root);assertEquals(30,JsonPath.<Object>read(inherited.body(),"$.data.frame_rates.refresh_fps"));assertEquals(4,JsonPath.<Object>read(inherited.body(),"$.data.frame_rates.detect_fps"));
  var edit=item("TOL","摄像头01");edit.put("model_id",mid);edit.put("station_id",sid);edit.put("version",1);edit.put("frame_rates",Map.of("refresh_fps",10,"detect_fps",5));
  assertEquals(200,send("PUT","/resource/device/"+did,edit,root).statusCode());
  var overridden=send("GET","/resource/device/"+did+"/capture-config",null,root);assertEquals(10,JsonPath.<Object>read(overridden.body(),"$.data.frame_rates.refresh_fps"));assertEquals(5,JsonPath.<Object>read(overridden.body(),"$.data.frame_rates.detect_fps"));
  var defaults=new HashMap<String,Object>();defaults.put("refresh_fps",null);defaults.put("detect_fps",null);edit.put("frame_rates",defaults);edit.put("version",2);
  assertEquals(200,send("PUT","/resource/device/"+did,edit,root).statusCode());
  assertEquals(4,JsonPath.<Object>read(send("GET","/resource/device/"+did+"/capture-config",null,root).body(),"$.data.frame_rates.detect_fps"));
  assertEquals(403,send("GET","/resource/device/"+did+"/capture-config",null,outsider).statusCode());
  org.mockito.Mockito.verifyNoInteractions(camera);
 }
 @Test void projectStationsRestrictTasksAndProtectLinks()throws Exception{
  String sid=station();jdbc.update("DELETE FROM sop_project_station WHERE project_id=10");
  assertEquals(400,send("POST","/project/10/subproject/11/task",task(sid),root).statusCode());
  assertEquals(403,send("GET","/project/10/station/list",null,outsider).statusCode());
  assertEquals(403,send("POST","/project/10/station",Map.of("station_id",sid,"version",0),outsider).statusCode());
  assertEquals(403,send("POST","/project/10/station",Map.of("station_id",sid,"version",0),owner).statusCode());
  assertEquals(200,send("POST","/project/10/station",Map.of("station_id",sid,"version",0),root).statusCode());
  assertEquals(409,send("POST","/project/10/station",Map.of("station_id",sid,"version",0),root).statusCode());
  var linked=send("GET","/project/10/station/list?available=true&keyword=工位",null,root);
  assertEquals(200,linked.statusCode(),linked.body());assertEquals(sid,JsonPath.read(linked.body(),"$.data.items[0].id"));
  var otherTask=task(sid);otherTask.put("owner_user_id",3);
  assertEquals(400,send("POST","/project/20/subproject/21/task",otherTask,root).statusCode());
  String tid=id(send("POST","/project/10/subproject/11/task",task(sid),root));
  assertEquals(409,send("DELETE","/project/10/station/"+sid,Map.of("version",1),root).statusCode());
  var cancelled=task(sid);cancelled.put("version",0);cancelled.put("status",2);
  assertEquals(200,send("PUT","/project/10/subproject/11/task/"+tid,cancelled,root).statusCode());
  assertEquals(200,send("DELETE","/project/10/station/"+sid,Map.of("version",1),root).statusCode());
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sop_project_station WHERE project_id=10",Integer.class));
  assertEquals(400,send("POST","/project/10/subproject/11/task",task(sid),root).statusCode());
 }
 @Test void inactiveStationsAreNotTaskOptionsAndCannotBeNewlyLinked()throws Exception{
  String sid=station();jdbc.update("UPDATE sop_station SET status=1 WHERE id=?",sid);
  var options=send("GET","/project/10/station/list?available=true",null,root);
  assertEquals(0,((List<?>)JsonPath.read(options.body(),"$.data.items")).size());
  assertEquals(1,((List<?>)JsonPath.read(send("GET","/project/10/station/list",null,root).body(),"$.data.items")).size());
  assertEquals(400,send("POST","/project/20/station",Map.of("station_id",sid,"version",0),root).statusCode());
  assertEquals(400,send("POST","/project/10/subproject/11/task",task(sid),root).statusCode());
 }
 @Test void stationTaskListFiltersProjectsAndRequiresStationForDrafts()throws Exception{
  String sid=station();assertEquals(400,send("POST","/project/10/subproject/11/task",task(null),root).statusCode());
  String tid=id(send("POST","/project/10/subproject/11/task",task(sid),root));
  var response=send("GET","/resource/station/"+sid+"/task",null,root);assertEquals(200,response.statusCode());assertEquals(tid,JsonPath.read(response.body(),"$.data.items[0].id"));assertEquals("10",JsonPath.read(response.body(),"$.data.items[0].project_id"));assertEquals(false,JsonPath.read(response.body(),"$.data.items[0].onsite_available"));assertTrue(((List<?>)JsonPath.read(response.body(),"$.data.items[0].onsite_unavailable_reasons")).contains("任务内容尚未发布"));
  assertEquals(403,send("GET","/resource/station/"+sid+"/task",null,outsider).statusCode());
  jdbc.update("INSERT INTO sys_role(id,code,status,name) VALUES(18,'resource_reader',1,'资源查询')");jdbc.update("INSERT INTO sys_permission(id,code) VALUES(18,'resource:read')");jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(18,18)");jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(3,18)");outsider=auth.login(new LoginRequest("user3","User123!")).access_token();
  var hidden=send("GET","/resource/station/"+sid+"/task",null,outsider);assertEquals(200,hidden.statusCode());assertEquals(0,((List<?>)JsonPath.read(hidden.body(),"$.data.items")).size());
  jdbc.update("UPDATE sop_task SET station_id=NULL WHERE id=?",tid);var edit=task(null);edit.put("version",0);assertEquals(400,send("PUT","/project/10/subproject/11/task/"+tid,edit,root).statusCode());edit.put("status",2);assertEquals(200,send("PUT","/project/10/subproject/11/task/"+tid,edit,root).statusCode());
 }
 @Test void onsiteReadinessRequiresPublishedConfiguredActiveTask()throws Exception{
  jdbc.update("UPDATE sop_subproject SET status=1 WHERE id=11");
  jdbc.update("UPDATE sop_project SET status=1 WHERE id=10");
  String sid=station();var t=task(sid);t.put("status",1);t.put("sop_id",30);t.put("revision_id",31);String tid=id(send("POST","/project/10/subproject/11/task",t,root));
  assertEquals(true,JsonPath.read(send("GET","/resource/station/"+sid+"/task",null,root).body(),"$.data.items[0].onsite_available"));
  jdbc.update("UPDATE sop_task SET status=0 WHERE id=?",tid);
  assertEquals(true,JsonPath.read(send("GET","/resource/station/"+sid+"/task",null,root).body(),"$.data.items[0].onsite_available"));
  jdbc.update("UPDATE sop_project SET status=3 WHERE id=10");
  assertEquals(false,JsonPath.read(send("GET","/resource/station/"+sid+"/task",null,root).body(),"$.data.items[0].onsite_available"));
  jdbc.update("UPDATE sop_project SET status=0 WHERE id=10");jdbc.update("UPDATE sop_task SET status=2 WHERE id=?",tid);
  assertEquals(false,JsonPath.read(send("GET","/resource/station/"+sid+"/task",null,root).body(),"$.data.items[0].onsite_available"));
 }
 @Test void switchingStationRechecksHardwareAndRetainsStandard()throws Exception{
  String first=station(),mid=model();String deviceId=device(mid,first);var second=item("STA","备用工位");second.put("code","STA-202610162030-B7F29C");second.put("ip_address","198.51.100.2");String next=id(send("POST","/resource/station",second,root));
  jdbc.update("INSERT INTO sop_project_station(project_id,station_id,created_by) VALUES(10,?,1)",next);
  jdbc.update("INSERT INTO sop_step_resource_requirement(step_id,model_id,name,type_code,unit,quantity,sort_order) VALUES(41,?,'摄像头','camera','台',1,1)",mid);
  var t=task(first);t.put("status",1);t.put("sop_id",30);t.put("revision_id",31);String tid=id(send("POST","/project/10/subproject/11/task",t,root));t.put("station_id",next);t.put("version",0);
  assertEquals(409,send("PUT","/project/10/subproject/11/task/"+tid,t,root).statusCode());assertEquals(Long.valueOf(first),jdbc.queryForObject("SELECT station_id FROM sop_task WHERE id=?",Long.class,tid));
  jdbc.update("UPDATE sop_resource_device SET station_id=? WHERE id=?",next,deviceId);assertEquals(200,send("PUT","/project/10/subproject/11/task/"+tid,t,root).statusCode());assertEquals(Long.valueOf(next),jdbc.queryForObject("SELECT station_id FROM sop_task WHERE id=?",Long.class,tid));assertEquals(31,jdbc.queryForObject("SELECT revision_id FROM sop_task_standard WHERE task_id=? AND active=1",Integer.class,tid));assertEquals(409,send("PUT","/project/10/subproject/11/task/"+tid,t,root).statusCode());
 }
 @Test void publishAndUseCommitsRevisionAndTaskTogether()throws Exception{
  String tid=id(send("POST","/project/10/subproject/11/task",task(station()),root));jdbc.update("UPDATE sop_revision SET revision_note='修订' WHERE id=32");
  var body=Map.of("sop_id",30,"revision_id",32,"revision_version",0,"task_version",0);
  assertEquals(403,send("PUT","/project/10/subproject/11/task/"+tid+"/publish",body,outsider).statusCode());assertEquals(0,jdbc.queryForObject("SELECT status FROM sop_revision WHERE id=32",Integer.class));
  var result=send("PUT","/project/10/subproject/11/task/"+tid+"/publish",body,root);assertEquals(200,result.statusCode(),result.body());assertEquals(1,jdbc.queryForObject("SELECT status FROM sop_revision WHERE id=32",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT status FROM sop_task WHERE id=?",Integer.class,tid));assertEquals(32,jdbc.queryForObject("SELECT revision_id FROM sop_task_standard WHERE task_id=? AND active=1",Integer.class,tid));
  assertEquals(0,jdbc.queryForObject("SELECT status FROM sop_subproject WHERE id=11",Integer.class));
  assertEquals(200,send("GET","/project/10/subproject/11/task/"+tid+"/preview",null,root).statusCode());
  assertEquals(409,send("PUT","/project/10/subproject/11/task/"+tid+"/publish",body,root).statusCode());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sop_task_standard WHERE task_id=?",Integer.class,tid));
 }
 @Test void publishAndUseRollsBackPublicationWhenStationHardwareDoesNotMatch()throws Exception{
  String tid=id(send("POST","/project/10/subproject/11/task",task(station()),root));String mid=model();jdbc.update("UPDATE sop_revision SET revision_note='修订' WHERE id=32");jdbc.update("INSERT INTO sop_step_resource_requirement(step_id,model_id,name,type_code,unit,quantity,sort_order) VALUES(42,?,'摄像头','camera','台',1,1)",mid);
  var body=Map.of("sop_id",30,"revision_id",32,"revision_version",0,"task_version",0);int docVersion=jdbc.queryForObject("SELECT version FROM sop_document WHERE id=30",Integer.class);
  var result=send("PUT","/project/10/subproject/11/task/"+tid+"/publish",body,root);assertEquals(409,result.statusCode(),result.body());assertEquals(0,jdbc.queryForObject("SELECT status FROM sop_revision WHERE id=32",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT version FROM sop_revision WHERE id=32",Integer.class));assertEquals(docVersion,jdbc.queryForObject("SELECT version FROM sop_document WHERE id=30",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sop_task_standard WHERE task_id=?",Integer.class,tid));assertEquals(0,jdbc.queryForObject("SELECT version FROM sop_task WHERE id=?",Integer.class,tid));
  jdbc.update("UPDATE sop_task SET version=version+1 WHERE id=?",tid);assertEquals(409,send("PUT","/project/10/subproject/11/task/"+tid+"/publish",body,root).statusCode());assertEquals(0,jdbc.queryForObject("SELECT status FROM sop_revision WHERE id=32",Integer.class));
 }
 @Test void taskContentDraftIsOwnedPersistentAndIdempotent()throws Exception{
  String tid=id(send("POST","/project/10/subproject/11/task",task(station()),root));var body=Map.of("code","SOP-202610171030-A1B2C3","version",0);
  assertEquals(403,send("POST","/project/10/subproject/11/task/"+tid+"/content",body,outsider).statusCode());
  var first=send("POST","/project/10/subproject/11/task/"+tid+"/content",body,root);assertEquals(200,first.statusCode(),first.body());String doc=JsonPath.read(first.body(),"$.data.sop_id"),rid=JsonPath.read(first.body(),"$.data.revision_id");
  assertEquals(1,jdbc.queryForObject("SELECT version FROM sop_task WHERE id=?",Integer.class,tid));assertEquals(Long.valueOf(doc),jdbc.queryForObject("SELECT sop_id FROM sop_task_content WHERE task_id=?",Long.class,tid));
  assertEquals(409,send("POST","/project/10/subproject/11/task/"+tid+"/content",body,root).statusCode());
  var again=send("POST","/project/10/subproject/11/task/"+tid+"/content",Map.of("version",1),root);assertEquals(200,again.statusCode(),again.body());assertEquals(rid,JsonPath.read(again.body(),"$.data.revision_id"));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM sop_document",Integer.class));
  assertEquals(409,send("DELETE","/sop/"+doc,Map.of("version",0),root).statusCode());
  jdbc.update("INSERT INTO sop_project_member(project_id,user_id,access_level,created_by,updated_by) VALUES(10,3,1,1,1)");var updated=task(jdbc.queryForObject("SELECT station_id FROM sop_task WHERE id=?",Long.class,tid).toString());updated.put("version",1);updated.put("owner_user_id",3);updated.put("name","任务改名");assertEquals(200,send("PUT","/project/10/subproject/11/task/"+tid,updated,root).statusCode());assertEquals(200,send("POST","/project/10/subproject/11/task/"+tid+"/content",Map.of("version",2),root).statusCode());assertEquals(3,jdbc.queryForObject("SELECT owner_user_id FROM sop_document WHERE id=?",Integer.class,doc));assertEquals("任务改名",jdbc.queryForObject("SELECT name FROM sop_document WHERE id=?",String.class,doc));
  jdbc.update("UPDATE sop_task SET status=2 WHERE id=?",tid);assertEquals(409,send("POST","/project/10/subproject/11/task/"+tid+"/content",Map.of("version",2),root).statusCode());assertEquals(403,send("PUT","/sop/"+doc+"/version/"+rid,Map.of("title","不应保存","version",0),root).statusCode());assertEquals(false,JsonPath.read(send("GET","/sop/"+doc,null,root).body(),"$.data.can_edit"));
 }
 @Test void oldSharedContentIsCopiedBeforeEditingAndOtherTaskRemainsUnchanged()throws Exception{
  String station=station();var t=task(station);t.put("sop_id",30);t.put("revision_id",31);t.put("status",1);String firstTask=id(send("POST","/project/10/subproject/11/task",t,root));t.put("code","TSK-202610171030-B1C2D3");String secondTask=id(send("POST","/project/10/subproject/11/task",t,root));
  var prepared=send("POST","/project/10/subproject/11/task/"+firstTask+"/content",Map.of("code","SOP-202610171030-C1D2E3","version",0),root);assertEquals(200,prepared.statusCode(),prepared.body());String doc=JsonPath.read(prepared.body(),"$.data.sop_id"),rid=JsonPath.read(prepared.body(),"$.data.revision_id");assertNotEquals("30",doc);
  Long step=jdbc.queryForObject("SELECT id FROM sop_step WHERE revision_id=?",Long.class,rid);int revisionVersion=jdbc.queryForObject("SELECT version FROM sop_revision WHERE id=?",Integer.class,rid);
  assertEquals(200,send("PUT","/sop/"+doc+"/version/"+rid+"/step/"+step,Map.of("title","仅任务一修改","instruction","新的步骤","version",0,"revision_version",revisionVersion),root).statusCode());assertEquals("检查内容",jdbc.queryForObject("SELECT instruction FROM sop_step WHERE id=41",String.class));
  assertEquals(31,jdbc.queryForObject("SELECT revision_id FROM sop_task_standard WHERE task_id=? AND active=1",Integer.class,secondTask));
  revisionVersion=jdbc.queryForObject("SELECT version FROM sop_revision WHERE id=?",Integer.class,rid);var publish=Map.of("sop_id",Long.parseLong(doc),"revision_id",Long.parseLong(rid),"revision_version",revisionVersion,"task_version",1);assertEquals(200,send("PUT","/project/10/subproject/11/task/"+firstTask+"/publish",publish,root).statusCode());
  jdbc.update("INSERT INTO sop_revision(sop_id,revision_no,title,status,published_by,published_at,created_by,updated_by) VALUES(?,2,'未生效的历史版',1,1,CURRENT_TIMESTAMP,1,1)",doc);
  var next=send("POST","/project/10/subproject/11/task/"+firstTask+"/content",Map.of("version",2),root);assertEquals(200,next.statusCode(),next.body());String nextRid=JsonPath.read(next.body(),"$.data.revision_id");assertNotEquals(rid,nextRid);assertEquals(doc,JsonPath.read(next.body(),"$.data.sop_id"));assertEquals("任务内容更新",jdbc.queryForObject("SELECT revision_note FROM sop_revision WHERE id=?",String.class,nextRid));assertEquals(31,jdbc.queryForObject("SELECT revision_id FROM sop_task_standard WHERE task_id=? AND active=1",Integer.class,secondTask));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sop_step WHERE revision_id=?",Integer.class,nextRid));
  assertEquals("新的步骤",jdbc.queryForObject("SELECT instruction FROM sop_step WHERE revision_id=?",String.class,nextRid));
  assertEquals(3,((Number)JsonPath.read(next.body(),"$.data.task.content_draft_no")).intValue());

 }
 @Test void resourceScopeValidationAndVersionProtection()throws Exception{
  assertEquals(401,send("GET","/resource/station",null,null).statusCode());assertEquals(403,send("GET","/resource/station",null,outsider).statusCode());
  String sid=station();var edit=item("STA","改名");edit.put("version",0);assertEquals(200,send("PUT","/resource/station/"+sid,edit,root).statusCode());assertEquals(409,send("PUT","/resource/station/"+sid,edit,root).statusCode());
  assertEquals(400,send("GET","/resource/station?limit=101",null,root).statusCode());assertEquals(200,send("GET","/resource/owner/options",null,root).statusCode());
 }
 @Test void deviceBindsStationAndConnectionHasNoFakeOnlineOrCredentials()throws Exception{
  String sid=station(),mid=model(),did=device(mid,sid);var list=send("GET","/resource/device",null,root);assertEquals(200,list.statusCode());assertEquals("工位A",JsonPath.read(list.body(),"$.data.items[0].station_name"));var c=new HashMap<String,Object>(Map.of("transport","network","protocol","rtsp","host","192.168.1.10","port",554,"path","/stream","enabled",true,"device_version",0));
  assertEquals(200,send("PUT","/resource/device/"+did+"/connection",c,root).statusCode());assertEquals(409,send("PUT","/resource/device/"+did+"/connection",c,root).statusCode());c.put("device_version",1);c.put("path","rtsp://user:secret@host/live");assertEquals(400,send("PUT","/resource/device/"+did+"/connection",c,root).statusCode());
  var preview=send("GET","/resource/device/"+did+"/preview",null,root);assertEquals(200,preview.statusCode());assertTrue(preview.headers().firstValue("Content-Type").orElse("").startsWith("image/jpeg"));assertEquals(409,send("DELETE","/resource/station/"+sid,Map.of("version",0),root).statusCode());assertEquals(409,send("DELETE","/resource/model/"+mid,Map.of("version",0),root).statusCode());
 }
 @Test void taskBelongsToSubprojectAndPinsStandardHistory()throws Exception{
  String sid=station();var t=task(sid);t.put("sop_id",30);t.put("revision_id",31);t.put("status",1);String tid=id(send("POST","/project/10/subproject/11/task",t,root));
  assertEquals(403,send("GET","/project/10/subproject/11/task",null,outsider).statusCode());assertEquals(404,send("GET","/project/20/subproject/11/task",null,root).statusCode());assertEquals(404,send("GET","/project/10/subproject/11/task/999/preview",null,root).statusCode());assertEquals(200,send("GET","/project/10/subproject/11/task/"+tid+"/preview",null,owner).statusCode());
  t.put("version",0);assertEquals(200,send("PUT","/project/10/subproject/11/task/"+tid,t,root).statusCode());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sop_task_standard WHERE task_id=?",Integer.class,tid));assertEquals(409,send("PUT","/project/10/subproject/11/task/"+tid,t,root).statusCode());
  assertEquals(409,send("PUT","/sop/30/version/31/retire",Map.of("version",0),root).statusCode());assertEquals(200,send("DELETE","/project/10/subproject/11",Map.of("version",0),root).statusCode());assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM sop_task WHERE id=?",java.sql.Timestamp.class,tid));
 }
 @Test void configuredTaskRequiresPublishedStandardStationAndRequiredDevices()throws Exception{
  var t=task(null);t.put("status",1);assertEquals(400,send("POST","/project/10/subproject/11/task",t,root).statusCode());String sid=station(),mid=model();
  jdbc.update("INSERT INTO sop_step_resource_requirement(step_id,model_id,name,type_code,unit,quantity,sort_order) VALUES(41,?,'摄像头','camera','台',1,1)",mid);
  t.put("station_id",sid);t.put("sop_id",30);t.put("revision_id",32);assertEquals(400,send("POST","/project/10/subproject/11/task",t,root).statusCode());t.put("revision_id",31);assertEquals(409,send("POST","/project/10/subproject/11/task",t,root).statusCode());device(mid,sid);assertEquals(200,send("POST","/project/10/subproject/11/task",t,root).statusCode());
 }
 @Test void requirementAtomicRollbackAndPublishedSnapshotDoNotChange()throws Exception{
  String mid=model();var req=Map.of("workplace_requirements","稳定台面","items",List.of(Map.of("model_id",mid,"quantity",1,"description","拍照")));
  var step=Map.of("title","新检查","instruction","操作说明","version",0,"revision_version",0);var body=Map.of("step",step,"visuals",List.of(),"resources",req);
  assertEquals(200,send("PUT","/sop/30/version/32/step/42/authoring",body,root).statusCode());
  assertEquals("稳定台面",jdbc.queryForObject("SELECT workplace_requirements FROM sop_step WHERE id=42",String.class));
  var bad=Map.of("step",Map.of("title","不应保存","instruction","操作","version",1,"revision_version",1),"visuals",List.of(),"resources",Map.of("items",List.of(Map.of("model_id",99999,"quantity",1))));assertEquals(409,send("PUT","/sop/30/version/32/step/42/authoring",bad,root).statusCode());assertEquals("新检查",jdbc.queryForObject("SELECT title FROM sop_step WHERE id=42",String.class));
  assertEquals(403,send("PUT","/sop/30/version/32/step/42/authoring",body,outsider).statusCode());
  jdbc.update("UPDATE sop_revision SET revision_note='修订' WHERE id=32");assertEquals(200,send("PUT","/sop/30/version/32/publish",Map.of("version",1),root).statusCode());
  var edit=item("TMD","修改目录名称");edit.put("type_code","camera");edit.put("unit","台");edit.put("version",0);assertEquals(200,send("PUT","/resource/model/"+mid,edit,root).statusCode());assertEquals("摄像头规格",jdbc.queryForObject("SELECT name FROM sop_step_resource_requirement WHERE step_id=42",String.class));
  assertEquals(409,send("PUT","/sop/30/version/32/step/42/authoring",body,root).statusCode());
  int docVersion=jdbc.queryForObject("SELECT version FROM sop_document WHERE id=30",Integer.class);assertEquals(200,send("POST","/sop/30/draft",Map.of("source_revision_id",32,"version",docVersion),root).statusCode());assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM sop_step_resource_requirement",Integer.class));
 }
 @Test void ownerMayConfigureDraftWithoutGlobalResourceManagementAndArchiveIsReadonly()throws Exception{
  String stationId=station();
  jdbc.update("INSERT INTO sys_role(id,code,status,name) VALUES(19,'resource_reader',1,'资源查询')");jdbc.update("INSERT INTO sys_permission(id,code) VALUES(19,'resource:read')");jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(19,19)");jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(2,19)");owner=auth.login(new LoginRequest("user2","User123!")).access_token();
  var t=task(stationId);String tid=id(send("POST","/project/10/subproject/11/task",t,owner));assertEquals(403,send("POST","/resource/station",item("STA","工位"),owner).statusCode());
  assertEquals(200,send("PUT","/project/10/status",Map.of("version",0,"status",3),owner).statusCode());t.put("version",0);assertEquals(403,send("PUT","/project/10/subproject/11/task/"+tid,t,owner).statusCode());
 }
 @Test void explicitRevisionSwitchKeepsHistoryAndProjectBindingsCannotRewriteTask()throws Exception{
  String sid=station();var t=task(sid);t.put("sop_id",30);t.put("revision_id",31);t.put("status",1);String tid=id(send("POST","/project/10/subproject/11/task",t,root));
  jdbc.update("UPDATE sop_revision SET status=1,published_by=1,published_at=CURRENT_TIMESTAMP WHERE id=32");t.put("revision_id",32);t.put("version",0);
  assertEquals(200,send("PUT","/project/10/subproject/11/task/"+tid,t,root).statusCode());assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM sop_task_standard WHERE task_id=?",Integer.class,tid));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sop_task_standard WHERE task_id=? AND active=1",Integer.class,tid));
  jdbc.update("INSERT INTO sop_subproject_binding(subproject_id,sop_id,revision_id,sort_order,status,created_by,updated_by) VALUES(11,30,31,1,1,1,1)");
  assertEquals(32,jdbc.queryForObject("SELECT revision_id FROM sop_task_standard WHERE task_id=? AND active=1",Integer.class,tid));
  t.put("status",2);t.put("version",1);assertEquals(200,send("PUT","/project/10/subproject/11/task/"+tid,t,root).statusCode());t.put("status",0);t.put("version",2);assertEquals(409,send("PUT","/project/10/subproject/11/task/"+tid,t,root).statusCode());
 }
 @Test void editorTaskPermissionCannotEscapeMembershipOrBecomeGlobalResourceManager()throws Exception{
  jdbc.update("INSERT INTO sys_role(id,code,status,name) VALUES(10,'task_editor',1,'任务编辑')");int i=1;for(String code:List.of("task:manage","project:read","resource:read")){jdbc.update("INSERT INTO sys_permission(id,code) VALUES(?,?)",i,code);jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(10,?)",i++);}jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(3,10)");
  outsider=auth.login(new LoginRequest("user3","User123!")).access_token();var t=task(station());assertEquals(403,send("POST","/project/10/subproject/11/task",t,outsider).statusCode());
  jdbc.update("INSERT INTO sop_project_member(project_id,user_id,access_level,created_by,updated_by) VALUES(10,3,1,1,1)");assertEquals(200,send("GET","/project/10/subproject/11/task/owner/options",null,outsider).statusCode());assertEquals(200,send("POST","/project/10/subproject/11/task",t,outsider).statusCode());assertEquals(403,send("POST","/resource/station",item("STA","工位"),outsider).statusCode());
 }

 @Test void locationCrudAndStationSelectUseDatabaseCatalog()throws Exception{
  var l=item("LOC","新位置");String lid=id(send("POST","/resource/location",l,root));assertEquals(200,send("GET","/resource/location?status=0",null,root).statusCode());assertEquals(403,send("POST","/resource/location",l,owner).statusCode());
  var st=item("STA","工位A");st.put("location_id",lid);String sid=id(send("POST","/resource/station",st,root));
  var list=send("GET","/resource/station?keyword=198.51.100.1",null,root);assertEquals("新位置",JsonPath.read(list.body(),"$.data.items[0].location_name"));
  assertEquals(409,send("DELETE","/resource/location/"+lid,Map.of("version",0),root).statusCode());
  l.put("version",0);l.put("name","位置改名");assertEquals(200,send("PUT","/resource/location/"+lid,l,root).statusCode());assertEquals("位置改名",JsonPath.read(send("GET","/resource/station/"+sid,null,root).body(),"$.data.location_name"));assertEquals(409,send("PUT","/resource/location/"+lid,l,root).statusCode());
  assertEquals(200,send("DELETE","/resource/station/"+sid,Map.of("version",0),root).statusCode());assertEquals(200,send("DELETE","/resource/location/"+lid,Map.of("version",1),root).statusCode());
 }
 @Test void stationRequiresExistingEnabledLocationAndValidUniqueIp()throws Exception{
  var st=item("STA","工位A");st.remove("location_id");assertEquals(400,send("POST","/resource/station",st,root).statusCode());st.put("location_id",99999);assertEquals(400,send("POST","/resource/station",st,root).statusCode());st.put("location_id",1);
  for(String ip:List.of("","localhost","999.1.1.1","1.2.3","01.2.3.4","http://1.2.3.4","fe80::1%eth0")){st.put("ip_address",ip);assertEquals(400,send("POST","/resource/station",st,root).statusCode(),ip);}
  st.put("ip_address","2001:db8::1");String sid=id(send("POST","/resource/station",st,root));st.put("code","STA-202610162030-ABCDEF");st.put("ip_address","2001:0db8:0:0:0:0:0:1");assertEquals(409,send("POST","/resource/station",st,root).statusCode());
  assertEquals(200,send("DELETE","/resource/station/"+sid,Map.of("version",0),root).statusCode());assertEquals(200,send("POST","/resource/station",st,root).statusCode());
  jdbc.update("UPDATE sop_location SET status=2 WHERE id=1");st.put("code","STA-202610162030-ABC123");st.put("ip_address","198.51.100.2");assertEquals(400,send("POST","/resource/station",st,root).statusCode());
 }
 @Test void legacyStationRemainsReadableUntilRealIpAndCatalogLocationAreFilled()throws Exception{
  jdbc.update("INSERT INTO sop_station(id,code,name,location,created_by,updated_by) VALUES(100,'OLD-STATION','旧工位','旧位置',1,1)");
  assertEquals(200,send("GET","/resource/station/100",null,root).statusCode());
  var st=item("STA","旧工位");st.put("code","OLD-STATION");st.put("version",0);st.put("ip_address","");assertEquals(400,send("PUT","/resource/station/100",st,root).statusCode());st.put("ip_address","198.51.100.5");assertEquals(200,send("PUT","/resource/station/100",st,root).statusCode());
 }

 @Test void deviceAndCommunicationAreSavedAtomicallyAndListedWithAddress()throws Exception{
  String mid=model();var d=item("TOL","网络摄像头");d.put("model_id",mid);
  var c=new HashMap<String,Object>(Map.of("transport","network","protocol","rtsp","host","192.0.2.10","port",554,"path","/stream","enabled",true));d.put("connection",c);
  String did=id(send("POST","/resource/device",d,root));var created=send("GET","/resource/device/"+did,null,root);assertEquals("192.0.2.10",JsonPath.read(created.body(),"$.data.connection.host"));assertEquals(0,((Number)JsonPath.read(created.body(),"$.data.version")).intValue());
  var listed=send("GET","/resource/device?keyword=192.0.2.10",null,root);assertEquals(200,listed.statusCode());assertEquals(1,((Number)JsonPath.read(listed.body(),"$.data.total")).intValue());assertEquals("network",JsonPath.read(listed.body(),"$.data.items[0].transport"));assertEquals("192.0.2.10",JsonPath.read(listed.body(),"$.data.items[0].connection_host"));
  d.put("version",0);d.put("name","USB扫码设备");d.put("connection",Map.of("transport","usb","protocol","hid_keyboard","host_key","PC-A","device_key","scanner-01","enabled",true));assertEquals(200,send("PUT","/resource/device/"+did,d,root).statusCode());
  var updated=send("GET","/resource/device/"+did,null,root);assertEquals("PC-A",JsonPath.read(updated.body(),"$.data.connection.host_key"));assertNull(jdbc.queryForObject("SELECT host FROM sop_device_connection WHERE device_id=?",String.class,did));assertEquals(1,((Number)JsonPath.read(updated.body(),"$.data.version")).intValue());assertEquals(409,send("PUT","/resource/device/"+did,d,root).statusCode());
  assertEquals(0,((Number)JsonPath.read(send("GET","/resource/device?keyword=192.0.2.10",null,root).body(),"$.data.total")).intValue());
 }
 @Test void invalidConnectionRollsBackDeviceCreateOrEditAndKeepsExistingConnection()throws Exception{
  String mid=model();var d=item("TOL","设备");d.put("model_id",mid);d.put("connection",Map.of("transport","network","protocol","rtsp","host","999.1.1.1","port",554,"enabled",true));
  assertEquals(400,send("POST","/resource/device",d,root).statusCode());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sop_resource_device",Integer.class));
  d.put("connection",Map.of("transport","network","protocol","rtsp","host","192.0.2.10","port",554,"enabled",true));String did=id(send("POST","/resource/device",d,root));
  d.put("version",0);d.put("name","不可保存的更名");d.put("connection",Map.of("transport","usb","protocol","hid_keyboard","enabled",true));assertEquals(400,send("PUT","/resource/device/"+did,d,root).statusCode());assertEquals("设备",jdbc.queryForObject("SELECT name FROM sop_resource_device WHERE id=?",String.class,did));assertEquals(0,jdbc.queryForObject("SELECT version FROM sop_resource_device WHERE id=?",Integer.class,did));assertEquals("192.0.2.10",jdbc.queryForObject("SELECT host FROM sop_device_connection WHERE device_id=?",String.class,did));
 }


 @Test void publicationDeterminesTaskStateWithoutManualStatus()throws Exception{
  String sid=station();var t=task(sid);t.remove("status");String tid=id(send("POST","/project/10/subproject/11/task",t,root));assertEquals(0,jdbc.queryForObject("SELECT status FROM sop_task WHERE id=?",Integer.class,tid));
  t.put("version",0);t.put("sop_id",30);t.put("revision_id",31);t.put("status",0);var saved=send("PUT","/project/10/subproject/11/task/"+tid,t,root);assertEquals(200,saved.statusCode(),saved.body());assertEquals(1,((Number)JsonPath.read(saved.body(),"$.data.status")).intValue());assertEquals(1,jdbc.queryForObject("SELECT status FROM sop_task WHERE id=?",Integer.class,tid));
 }
}


