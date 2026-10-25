package org.jsut;

import com.jayway.jsonpath.JsonPath;
import org.jsut.modules.user.dto.LoginRequest;
import org.jsut.modules.user.service.AuthService;
import org.jsut.modules.user.service.RootInitializationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.nio.file.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.datasource.url=jdbc:h2:mem:sop_text_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:login-schema.sql,classpath:project-schema.sql,classpath:sop-schema.sql,classpath:resource-schema.sql",
 "sop.bootstrap.root.enabled=false","server.port=0"
})
class SopIntegrationTest {
 final java.util.concurrent.atomic.AtomicLong codes=new java.util.concurrent.atomic.AtomicLong();
 @Test void clientCodeIsSavedUnchangedAndRejectsDuplicatesWrongPrefixAndInvalidDates()throws Exception{
  String code="SOP-202610161745-A7F29C";var body=Map.of("code",code,"name","客户端SOP","managing_project_id",10,"owner_user_id",2);
  var created=send("POST","/sop",body,owner);String id=value(created,"$.data.id");assertEquals(code,value(created,"$.data.code"));
  assertEquals(409,send("POST","/sop",body,owner).statusCode());
  assertEquals(400,send("POST","/sop",Map.of("name","缺编号","managing_project_id",10,"owner_user_id",2),owner).statusCode());
  for(String invalid:List.of("PRJ-202610161745-A7F29C","SOP-202602301745-A7F29C","SOP-202610161745-abcdef"))assertEquals(400,send("POST","/sop",Map.of("code",invalid,"name","无效","managing_project_id",10,"owner_user_id",2),owner).statusCode());
  assertEquals(code,value(send("PUT","/sop/"+id,Map.of("name","改目录名","version",0),owner),"$.data.code"));
 }
 static final Path FILES;
 static { try { FILES=Files.createTempDirectory("sop-media-test-").toAbsolutePath().normalize(); } catch(IOException e){throw new ExceptionInInitializerError(e);} }
 @DynamicPropertySource static void storage(DynamicPropertyRegistry r){r.add("sop.storage.directory",()->FILES.toString());r.add("sop.storage.data-root",()->FILES.resolve("data").toString());}
 @AfterAll static void cleanup()throws IOException{
  // Only this test's freshly created directory; Files.walk does not follow symbolic links.
  try(var files=Files.walk(FILES)){for(Path p:files.sorted(java.util.Comparator.reverseOrder()).toList()){assertTrue(p.toAbsolutePath().normalize().startsWith(FILES));Files.delete(p);}}
 }
 @Autowired JdbcTemplate jdbc;
 @Autowired AuthService auth;
 @Autowired RootInitializationService initializer;
 @Autowired PasswordEncoder encoder;
 @Autowired ObjectMapper json;
 @Value("${local.server.port}") int port;
 String root,owner,outsider,author;
 final HttpClient http=HttpClient.newHttpClient();
 @BeforeEach void seed(){
  jdbc.update("DELETE FROM sop_camera_config WHERE device_id IS NOT NULL");
  for(String t:List.of("sop_step_detection","sop_task_standard","sop_task","sop_step_resource_requirement","sop_device_connection","sop_resource_device","sop_resource_model","sop_project_station","sop_station","sop_location","sop_step_visual","sop_project_binding","sop_subproject_binding","sop_step_attachment","sop_file","sop_step","sop_revision","sop_document","sop_project_activity","sop_subproject","sop_project_member","sop_project","sys_user_session","sys_user_role","sys_role_permission","sys_user","sys_team","sys_role","sys_permission"))jdbc.execute("DELETE FROM "+t);
  jdbc.update("INSERT INTO sys_user(id,username,real_name,status,is_superuser) VALUES(1,'root','Root',0,1)");initializer.initialize("Root123!");
  String hash=encoder.encode("User123!");
  for(int i=2;i<=4;i++)jdbc.update("INSERT INTO sys_user(id,username,real_name,password_hash,status,must_change_password) VALUES(?,?,?,?,1,0)",i,"user"+i,"用户"+i,hash);
  jdbc.update("INSERT INTO sop_project(id,code,name,owner_user_id,created_by,updated_by) VALUES(10,'P10','管理项目',2,1,1),(20,'P20','其他项目',3,1,1)");
  jdbc.update("INSERT INTO sop_project_member(project_id,user_id,access_level,created_by,updated_by) VALUES(10,1,1,1,1),(10,2,1,1,1),(10,4,1,1,1),(20,3,1,1,1)");
  jdbc.update("INSERT INTO sys_role(id,code,status,name) VALUES(10,'sop_author',1,'编写者')");int i=1;
  for(String code:List.of("project:read","project:list","sop:list","sop:read","sop:create","sop:update","sop:publish","sop:retire","sop:delete")){
   jdbc.update("INSERT INTO sys_permission(id,code) VALUES(?,?)",i,code);jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(10,?)",i++);
  }
  jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(3,10),(4,10)");
  root=auth.login(new LoginRequest("root","Root123!")).access_token();owner=auth.login(new LoginRequest("user2","User123!")).access_token();outsider=auth.login(new LoginRequest("user3","User123!")).access_token();author=auth.login(new LoginRequest("user4","User123!")).access_token();
 }
 @Test void createScopeValidationAndProtectedMetadata()throws Exception{
  var created=create(" sop-1 ",2,owner);String id=value(created,"$.data.id");
  assertTrue(((String)value(created,"$.data.code")).matches("SOP-[0-9]{12}-[0-9A-F]{6}"));assertInstanceOf(String.class,value(created,"$.data.draft.id"));
  assertEquals(1,((Number)value(send("GET","/sop/list?project_id=10",null,author),"$.data.total")).intValue());
  assertEquals(0,((Number)value(send("GET","/sop/list",null,outsider),"$.data.total")).intValue());
  assertEquals(403,send("GET","/sop/"+id,null,outsider).statusCode());
  assertEquals(401,send("GET","/sop/"+id,null,null).statusCode());
  assertEquals(200,create("SOP-1",2,owner).statusCode());
  assertEquals(400,create("BAD-OWNER",3,owner).statusCode());
  assertEquals(403,create("NO-SCOPE",3,outsider).statusCode());
  assertEquals(400,send("GET","/sop/list?limit=101",null,root).statusCode());
  assertEquals(400,send("GET","/sop/list?project_id=bad",null,root).statusCode());
  var detail=send("GET","/sop/"+id,null,owner);assertFalse(detail.body().contains("password_hash"));assertFalse(created.body().contains("draft_slot"));
  jdbc.update("UPDATE sys_user SET must_change_password=1 WHERE id=4");assertEquals(403,send("GET","/sop/list",null,author).statusCode());
 }
 @Test void draftStepsVersionsSortAndCrossSopProtection()throws Exception{
  var created=create("STEPS",2,owner);String id=value(created,"$.data.id"),rid=value(created,"$.data.draft.id");
  var one=add(id,rid,"操作一",0);String a=value(one,"$.data.steps[0].id");
  assertEquals(409,add(id,rid,"旧草稿版本",0).statusCode());
  var two=add(id,rid,"操作二",1);String b=value(two,"$.data.steps[1].id");
  assertEquals(400,send("PUT",path(id,rid)+"/step/sort",Map.of("ids",List.of(a,a),"version",2),owner).statusCode());
  var sorted=send("PUT",path(id,rid)+"/step/sort",Map.of("ids",List.of(b,a),"version",2),owner);
  assertEquals(b,value(sorted,"$.data.steps[0].id"));
  var edit=Map.of("title","修改操作","instruction","正确操作说明","version",1,"revision_version",3);
  assertEquals(200,send("PUT",path(id,rid)+"/step/"+a,edit,owner).statusCode());
  var another=create("OTHER",2,owner);String otherId=value(another,"$.data.id");
  assertEquals(404,send("GET",path(otherId,rid),null,owner).statusCode());
  assertEquals(404,send("PUT",path(otherId,rid)+"/step/"+a,edit,owner).statusCode());
  var deleted=send("DELETE",path(id,rid)+"/step/"+b,Map.of("version",1,"revision_version",4),owner);
  assertEquals(1,((Number)value(deleted,"$.data.steps[0].sort_order")).intValue());
  assertEquals(5,((Number)value(deleted,"$.data.version")).intValue());
 }
 @Test void publishImmutableCloneAndRetireWithoutChangingBindings()throws Exception{
  var created=create("VERSION",2,owner);String id=value(created,"$.data.id"),rid=value(created,"$.data.draft.id");
  assertEquals(400,publish(id,rid,0,owner).statusCode());
  add(id,rid,"初始操作",0);assertEquals(403,publish(id,rid,1,author).statusCode());
  assertEquals(200,publish(id,rid,1,owner).statusCode());
  assertEquals(409,add(id,rid,"不能写发布内容",2).statusCode());
  assertEquals(409,send("PUT",path(id,rid),Map.of("title","不能改","version",2),owner).statusCode());
  assertEquals(409,send("DELETE",path(id,rid),Map.of("version",2),owner).statusCode());
  assertEquals(409,send("DELETE","/sop/"+id,Map.of("version",2),owner).statusCode());
  jdbc.update("INSERT INTO sop_project_binding(project_id,sop_id,revision_id,created_by,updated_by) VALUES(20,?,?,1,1)",id,rid);
  var copy=send("POST","/sop/"+id+"/draft",Map.of("source_revision_id",rid,"version",2),owner);
  String next=value(copy,"$.data.id");String copiedStep=value(copy,"$.data.steps[0].id"),oldStep=value(preview(id,rid),"$.data.steps[0].id");assertNotEquals(copiedStep,oldStep);
  assertEquals(409,send("POST","/sop/"+id+"/draft",Map.of("source_revision_id",rid,"version",3),owner).statusCode());
  assertEquals(400,publish(id,next,0,owner).statusCode());
  assertEquals(200,send("PUT",path(id,next)+"/step/"+copiedStep,Map.of("title","新版步骤","instruction","只修改新草稿","version",0,"revision_version",0),owner).statusCode());
  assertEquals("按照说明操作",value(preview(id,rid),"$.data.steps[0].instruction"));
  assertEquals(200,send("PUT",path(id,next),Map.of("title","第二版","revision_note","更新说明","version",1),owner).statusCode());
  assertEquals(200,publish(id,next,2,owner).statusCode());
  assertEquals(Long.parseLong(rid),jdbc.queryForObject("SELECT revision_id FROM sop_project_binding WHERE project_id=20",Long.class));
  assertEquals("通用SOP",value(preview(id,rid),"$.data.title"));
  assertEquals(409,send("PUT",path(id,rid)+"/retire",Map.of("version",2),owner).statusCode());
  jdbc.update("UPDATE sop_project SET status=3 WHERE id=20");
  assertEquals(200,send("PUT",path(id,rid)+"/retire",Map.of("version",2),owner).statusCode());
  assertEquals(2,((Number)value(preview(id,rid),"$.data.status")).intValue());
 }
 @Test void sopReferencesProtectMembersProjectsAndSubprojects()throws Exception{
  var created=create("GUARDS",4,owner);String id=value(created,"$.data.id"),rid=value(created,"$.data.draft.id");
  assertEquals(403,send("DELETE","/project/10",Map.of("version",0),owner).statusCode());
  assertEquals(409,send("DELETE","/project/10/member/4",Map.of("version",0),owner).statusCode());
  assertEquals(409,send("PUT","/project/10/member/4",Map.of("access_level",0,"version",0),owner).statusCode());
  assertEquals(403,send("PUT","/sop/"+id+"/owner",Map.of("owner_user_id",2,"version",0),author).statusCode());
  assertEquals(200,send("PUT","/sop/"+id+"/owner",Map.of("owner_user_id",2,"version",0),owner).statusCode());
  assertEquals(200,send("DELETE","/project/10/member/4",Map.of("version",0),owner).statusCode());
  assertEquals(403,previewWith(id,rid,author).statusCode());
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sop_project_activity WHERE action='SOP_OWNER_CHANGED'",Integer.class));
  jdbc.update("INSERT INTO sop_subproject(id,project_id,code,name,owner_user_id,created_by,updated_by) VALUES(30,20,'S','子项目',3,1,1)");
  jdbc.update("INSERT INTO sop_subproject_binding(subproject_id,sop_id,revision_id,status,created_by,updated_by) VALUES(30,?,?,0,1,1)",id,rid);
  assertEquals(403,send("DELETE","/project/20/subproject/30",Map.of("version",0),owner).statusCode());
  assertEquals(409,send("DELETE","/sop/"+id,Map.of("version",1),owner).statusCode());
 }
 @Test void discardReleasesDraftSlotButNeverReusesVersionOrCode()throws Exception{
  var created=create("DISCARD",2,owner);String id=value(created,"$.data.id"),rid=value(created,"$.data.draft.id");
  add(id,rid,"操作",0);publish(id,rid,1,owner);
  var copy=send("POST","/sop/"+id+"/draft",Map.of("source_revision_id",rid,"version",2),owner);String next=value(copy,"$.data.id");
  assertEquals(200,send("DELETE",path(id,next),Map.of("version",0),owner).statusCode());
  var third=send("POST","/sop/"+id+"/draft",Map.of("source_revision_id",rid,"version",4),owner);
  assertEquals(3,((Number)value(third,"$.data.revision_no")).intValue());
  var disposable=create("DELETE-ME",2,owner);String deleted=value(disposable,"$.data.id");
  assertEquals(200,send("DELETE","/sop/"+deleted,Map.of("version",0),owner).statusCode());
  assertEquals(404,send("GET","/sop/"+deleted,null,owner).statusCode());
  assertEquals(200,create("DELETE-ME",2,owner).statusCode());
 }
 @Test void archiveAndInvalidOwnerBlockWritesButAllowHandover()throws Exception{
  var created=create("ARCHIVE",4,owner);String id=value(created,"$.data.id"),rid=value(created,"$.data.draft.id");add(id,rid,"操作",0);
  jdbc.update("UPDATE sys_user SET status=0 WHERE id=4");assertEquals(400,publish(id,rid,1,owner).statusCode());
  jdbc.update("UPDATE sop_project SET status=3 WHERE id=10");assertEquals(409,add(id,rid,"禁止写",1).statusCode());
  assertEquals(200,preview(id,rid).statusCode());
  assertEquals(200,send("PUT","/sop/"+id+"/owner",Map.of("owner_user_id",2,"version",1),owner).statusCode());
  assertEquals(409,send("PUT","/sop/"+id,Map.of("name","禁止改","version",2),owner).statusCode());
 }
 @Test void missingOrCorruptStorageBlocksPublishingAndNeverLeaksPrivateKeys()throws Exception{
  var created=create("FILES",2,owner);String id=value(created,"$.data.id"),rid=value(created,"$.data.draft.id");String step=value(add(id,rid,"操作",0),"$.data.steps[0].id");
  jdbc.update("INSERT INTO sop_file(id,storage_key,original_name,mime_type,file_kind,byte_size,sha256,uploaded_by) VALUES(50,'private-object','image.png','image/png',0,100,?,1)",new byte[32]);
  jdbc.update("INSERT INTO sop_step_attachment(step_id,file_id) VALUES(?,50)",step);
  assertEquals(409,publish(id,rid,1,owner).statusCode());
  var result=preview(id,rid);assertFalse(result.body().contains("private-object"));assertFalse(result.body().contains("sha256"));assertEquals("image.png",value(result,"$.data.steps[0].attachments[0].original_name"));
 }
 @Test void imageUploadValidationPublicationAndCloneIsolation()throws Exception{
  var created=create("IMAGE",2,owner);String id=value(created,"$.data.id"),rid=value(created,"$.data.draft.id");
  String step=value(add(id,rid,"操作图片",0),"$.data.steps[0].id");
  assertEquals(400,upload(id,rid,step,1,"not an image".getBytes(),owner).statusCode());
  byte[] png=png();var uploaded=upload(id,rid,step,1,png,owner);String fid=value(uploaded,"$.data.steps[0].attachments[0].file_id");
  String imagePath=path(id,rid)+"/image/"+fid;
  assertEquals(401,send("GET",imagePath,null,null).statusCode());assertEquals(403,send("GET",imagePath,null,outsider).statusCode());
  var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+imagePath)).header("Authorization","Bearer "+owner).build();
  var response=http.send(request,HttpResponse.BodyHandlers.ofByteArray());assertArrayEquals(png,response.body());assertEquals("image/png",response.headers().firstValue("Content-Type").orElseThrow());assertEquals("nosniff",response.headers().firstValue("X-Content-Type-Options").orElseThrow());
  assertEquals(409,upload(id,rid,step,1,png,owner).statusCode());
  assertEquals(200,publish(id,rid,2,owner).statusCode());assertEquals(409,upload(id,rid,step,3,png,owner).statusCode());
  var copy=send("POST","/sop/"+id+"/draft",Map.of("source_revision_id",rid,"version",3),owner);
  String next=value(copy,"$.data.id"),nextStep=value(copy,"$.data.steps[0].id"),attachment=value(copy,"$.data.steps[0].attachments[0].id");
  assertEquals(fid,value(copy,"$.data.steps[0].attachments[0].file_id"));assertNotEquals(step,nextStep);
  assertEquals(200,send("DELETE",path(id,next)+"/step/"+nextStep+"/image/"+attachment,Map.of("version",0),owner).statusCode());
  assertEquals(404,send("GET",path(id,next)+"/image/"+fid,null,owner).statusCode());assertEquals(200,send("GET",imagePath,null,owner).statusCode());
  assertEquals(413,upload(id,next,nextStep,1,new byte[5*1024*1024+1],owner).statusCode());
 }
 @Test void bindingPinsPublishedVersionAndRestrictsTargetPreviewAndImages()throws Exception{
  var created=create("SHARE",2,owner);String id=value(created,"$.data.id"),rid=value(created,"$.data.draft.id");String step=value(add(id,rid,"发布操作",0),"$.data.steps[0].id");
  String fid=value(upload(id,rid,step,1,png(),owner),"$.data.steps[0].attachments[0].file_id");publish(id,rid,2,owner);
  String base="/project/20/sop";
  assertEquals(403,send("POST",base,Map.of("sop_id",id,"revision_id",rid,"target_version",0),outsider).statusCode());
  var bound=send("POST",base,Map.of("sop_id",id,"revision_id",rid,"target_version",0),root);String bid=value(bound,"$.data.items[0].id");
  assertEquals(1,((Number)value(bound,"$.data.target_version")).intValue());
  assertEquals(200,send("GET",base+"/"+bid+"/preview",null,outsider).statusCode());assertEquals(200,send("GET",base+"/"+bid+"/image/"+fid,null,outsider).statusCode());
  assertEquals(403,previewWith(id,rid,outsider).statusCode());assertEquals(403,send("GET",path(id,rid)+"/image/"+fid,null,outsider).statusCode());
  var next=send("POST","/sop/"+id+"/draft",Map.of("source_revision_id",rid,"version",3),owner);String nr=value(next,"$.data.id");
  send("PUT",path(id,nr),Map.of("title","新版标题","revision_note","步骤改进","version",0),owner);publish(id,nr,1,owner);
  assertEquals(rid,value(send("GET",base+"/"+bid+"/preview",null,outsider),"$.data.id"));
  assertEquals(409,send("PUT",path(id,rid)+"/retire",Map.of("version",3),owner).statusCode());
  assertEquals(409,send("PUT",base+"/"+bid,Map.of("sop_id",id,"revision_id",nr,"version",0,"target_version",0),root).statusCode());
  assertEquals(200,send("PUT",base+"/"+bid,Map.of("sop_id",id,"revision_id",nr,"version",0,"target_version",1),root).statusCode());
  assertEquals(nr,value(send("GET",base+"/"+bid+"/preview",null,outsider),"$.data.id"));
  assertEquals(200,send("PUT",path(id,rid)+"/retire",Map.of("version",3),owner).statusCode());
  assertEquals(409,send("PUT",base+"/"+bid,Map.of("sop_id",id,"revision_id",rid,"version",1,"target_version",2),root).statusCode());
  assertEquals(200,send("DELETE",base+"/"+bid,Map.of("version",1,"target_version",2),outsider).statusCode());
  assertEquals(404,send("GET",base+"/"+bid+"/preview",null,outsider).statusCode());assertEquals(404,send("GET",base+"/"+bid+"/image/"+fid,null,outsider).statusCode());
  assertEquals(200,send("PUT",base+"/"+bid,Map.of("sop_id",id,"revision_id",nr,"version",2,"target_version",3),root).statusCode());
  jdbc.update("INSERT INTO sop_project_member(project_id,user_id,access_level,created_by,updated_by) VALUES(20,4,0,1,1)");
  assertEquals(200,send("GET",base+"/"+bid+"/preview",null,author).statusCode());jdbc.update("UPDATE sop_project_member SET member_status=0 WHERE project_id=20 AND user_id=4");
  assertEquals(403,send("GET",base+"/"+bid+"/preview",null,author).statusCode());assertEquals(403,send("GET",base+"/"+bid+"/image/"+fid,null,author).statusCode());
 }
 @Test void subprojectBindingsCannotCrossParentsOrAcceptDraftAndClosedTargets()throws Exception{
  var created=create("SUB-BIND",2,owner);String id=value(created,"$.data.id"),rid=value(created,"$.data.draft.id");add(id,rid,"步骤",0);
  jdbc.update("INSERT INTO sop_subproject(id,project_id,code,name,owner_user_id,created_by,updated_by) VALUES(30,20,'S','子项目',3,1,1)");
  String base="/project/20/subproject/30/sop";
  assertEquals(404,send("GET","/project/10/subproject/30/sop/list",null,root).statusCode());
  assertEquals(409,send("POST",base,Map.of("sop_id",id,"revision_id",rid,"target_version",0),root).statusCode());
  publish(id,rid,1,owner);var bind=send("POST",base,Map.of("sop_id",id,"revision_id",rid,"target_version",0),root);String bid=value(bind,"$.data.items[0].id");
  assertEquals(200,send("GET",base+"/"+bid+"/preview",null,outsider).statusCode());
  assertEquals(200,send("PUT",base+"/sort",Map.of("ids",List.of(bid),"target_version",1),root).statusCode());
  assertEquals(400,send("PUT",base+"/sort",Map.of("ids",List.of(bid,bid),"target_version",2),root).statusCode());
  jdbc.update("UPDATE sop_subproject SET status=3 WHERE id=30");
  assertFalse((Boolean)value(send("GET",base+"/list",null,root),"$.data.can_manage"));
  assertEquals(409,send("DELETE",base+"/"+bid,Map.of("version",1,"target_version",2),root).statusCode());
  assertEquals(200,send("GET",base+"/"+bid+"/preview",null,outsider).statusCode());
 }
 byte[] png()throws IOException{var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(3,3,BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();}
 @Test void detectionParametersAreValidatedByTypeWithoutClaimingInference()throws Exception{
  var created=create("DETECTION-CONFIG",2,owner);String id=value(created,"$.data.id"),rid=value(created,"$.data.draft.id"),step=value(add(id,rid,"检查",0),"$.data.steps[0].id");String aid=value(upload(id,rid,step,1,png(),owner),"$.data.steps[0].attachments[0].id");String url=path(id,rid)+"/step/"+step+"/authoring";
  var region=guide();region.put("id","region1");region.put("enabled",true);region.put("detect_type","count");region.put("class_name","标签");region.put("confidence",75);region.put("expected_count",0);
  var v=new HashMap<>(visual(aid,guide()));v.put("guide_items",List.of());v.put("detection_regions",List.of(region));
  assertEquals(400,send("PUT",url,Map.of("step",authorStep(0,2),"visuals",List.of(v)),owner).statusCode());region.put("expected_count",2);region.put("confidence",101);
  assertEquals(400,send("PUT",url,Map.of("step",authorStep(0,2),"visuals",List.of(v)),owner).statusCode());region.put("confidence",75);region.put("detect_type","ocr");region.put("expected_text","产品合格");
  var saved=send("PUT",url,Map.of("step",authorStep(0,2),"visuals",List.of(v)),owner);assertEquals("ocr",value(saved,"$.data.steps[0].attachments[0].visual.detection_regions[0].detect_type"));assertFalse(saved.body().contains("inference_result"));
  region.put("detect_type","status");assertEquals(400,send("PUT",url,Map.of("step",authorStep(1,3),"visuals",List.of(v)),owner).statusCode());region.put("expected_state","关闭");
  assertEquals(200,send("PUT",url,Map.of("step",authorStep(1,3),"visuals",List.of(v)),owner).statusCode());
 }
 Map<String,Object> guide(){return new HashMap<>(Map.of("id","guide1","kind","rect","name","操作位置","color","#409EFF","x",0.1,"y",0.1,"width",0.5,"height",0.5));}
 Map<String,Object> visual(String aid,Map<String,Object> mark){return Map.of("attachment_id",aid,"schema_version",1,"image_width",3,"image_height",3,"guide_items",List.of(mark),"detection_regions",List.of());}
 Map<String,Object> authorStep(int version,int revisionVersion){return Map.of("title","标注步骤","instruction","按图操作","version",version,"revision_version",revisionVersion,"detection_wait_seconds",2.5,"voice_enabled",true,"voice_text","请按图操作");}
 @Test void authoringAtomicPersistenceImmutablePublicationAndCloneIsolation()throws Exception{
  var created=create("VISUAL",2,owner);String id=value(created,"$.data.id"),rid=value(created,"$.data.draft.id");String step=value(add(id,rid,"步骤",0),"$.data.steps[0].id");
  var upload=upload(id,rid,step,1,png(),owner);String aid=value(upload,"$.data.steps[0].attachments[0].id");
  var request=Map.of("step",authorStep(0,2),"visuals",List.of(visual(aid,guide())),"revision",Map.of("title","制作标准","version",2));
  String url=path(id,rid)+"/step/"+step+"/authoring";var saved=send("PUT",url,request,owner);
  assertEquals("制作标准",value(saved,"$.data.title"));assertEquals(3,((Number)value(saved,"$.data.version")).intValue());
  assertEquals("操作位置",value(saved,"$.data.steps[0].attachments[0].visual.guide_items[0].name"));assertEquals(1,((Number)value(saved,"$.data.steps[0].voice_enabled")).intValue());
  assertEquals(2.5,((Number)value(preview(id,rid),"$.data.steps[0].detection_wait_seconds")).doubleValue());
  assertEquals(409,send("PUT",url,request,owner).statusCode());assertEquals(403,send("PUT",url,request,outsider).statusCode());
  assertEquals(200,publish(id,rid,3,owner).statusCode());assertEquals(409,send("PUT",url,Map.of("step",authorStep(1,4),"visuals",List.of()),owner).statusCode());
  var bound=send("POST","/project/20/sop",Map.of("sop_id",id,"revision_id",rid,"target_version",0),root);String bid=value(bound,"$.data.items[0].id");
  assertEquals("操作位置",value(send("GET","/project/20/sop/"+bid+"/preview",null,outsider),"$.data.steps[0].attachments[0].visual.guide_items[0].name"));
  var copy=send("POST","/sop/"+id+"/draft",Map.of("source_revision_id",rid,"version",4),owner);String next=value(copy,"$.data.id"),ns=value(copy,"$.data.steps[0].id"),na=value(copy,"$.data.steps[0].attachments[0].id");assertNotEquals(aid,na);
  var mark=guide();mark.put("name","新版位置");assertEquals(200,send("PUT",path(id,next)+"/step/"+ns+"/authoring",Map.of("step",authorStep(0,0),"visuals",List.of(visual(na,mark))),owner).statusCode());
  assertEquals("操作位置",value(preview(id,rid),"$.data.steps[0].attachments[0].visual.guide_items[0].name"));
  assertEquals(200,send("DELETE",path(id,next)+"/step/"+ns+"/image/"+na,Map.of("version",1),owner).statusCode());
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sop_step_visual",Integer.class));
 }
 @Test void stepDetectionPersistsValidatesAndRemapsBaselineWhenCopied()throws Exception{
  jdbc.update("INSERT INTO sop_station(id,code,name,created_by,updated_by) VALUES(901,'DETECT-ST','检测工位',1,1)");
  jdbc.update("INSERT INTO sop_resource_model(id,code,name,type_code,unit,created_by,updated_by) VALUES(901,'DETECT-M','摄像头','camera','台',1,1)");
  jdbc.update("INSERT INTO sop_resource_device(id,code,name,model_id,station_id,created_by,updated_by) VALUES(901,'DETECT-C','摄像头A',901,901,1,1)");
  var created=create("DETECT",2,owner);String id=value(created,"$.data.id"),rid=value(created,"$.data.draft.id"),sid=value(add(id,rid,"检测杯子",0),"$.data.steps[0].id");
  String aid=value(upload(id,rid,sid,1,png(),owner),"$.data.steps[0].attachments[0].id");
  var region=guide();region.put("id","region1");region.put("enabled",true);region.put("detect_type","object");region.put("class_name","cup");region.put("confidence",60);
  var visual=new HashMap<>(visual(aid,guide()));visual.put("detection_regions",List.of(region));
  var config=new HashMap<String,Object>();config.put("enabled",true);config.put("camera_id",901);config.put("attachment_id",aid);config.put("confirmed",false);
  String url=path(id,rid)+"/step/"+sid+"/authoring";
  assertEquals(200,send("PUT",url,Map.of("step",authorStep(0,2),"visual",visual,"detection",config),owner).statusCode());
  assertEquals(aid,value(preview(id,rid),"$.data.steps[0].detection.attachment_id"));
  assertEquals(400,publish(id,rid,3,owner).statusCode());
  config.put("confirmed",true);config.put("attachment_id",999999);
  assertEquals(400,send("PUT",url,Map.of("step",authorStep(1,3),"visual",visual,"detection",config),owner).statusCode());
  config.put("attachment_id",aid);
  assertEquals(200,send("PUT",url,Map.of("step",authorStep(1,3),"visual",visual,"detection",config),owner).statusCode());
  assertEquals(200,publish(id,rid,4,owner).statusCode());
  var copy=send("POST","/sop/"+id+"/draft",Map.of("source_revision_id",rid,"version",5),owner);
  assertEquals(200,copy.statusCode());
  String copiedAid=value(copy,"$.data.steps[0].attachments[0].id");
  assertNotEquals(aid,copiedAid);assertEquals(copiedAid,value(copy,"$.data.steps[0].detection.attachment_id"));
  assertEquals("901",value(copy,"$.data.steps[0].detection.camera_id"));
  assertEquals(aid,value(preview(id,rid),"$.data.steps[0].detection.attachment_id"));
  String next=value(copy,"$.data.id"),nextStep=value(copy,"$.data.steps[0].id");
  var removed=send("DELETE",path(id,next)+"/step/"+nextStep+"/image/"+copiedAid,Map.of("version",0),owner);
  assertEquals(200,removed.statusCode());assertEquals(false,value(removed,"$.data.steps[0].detection.enabled"));
  assertNull(((Map<?,?>)value(removed,"$.data.steps[0].detection")).get("attachment_id"));
 }
 @Test void cameraPhotoAtomicallyBindsAndReplacesTheCurrentImage()throws Exception{
  jdbc.update("INSERT INTO sop_station(id,code,name,created_by,updated_by) VALUES(902,'PHOTO-ST','拍照工位',1,1)");
  jdbc.update("INSERT INTO sop_resource_model(id,code,name,type_code,unit,created_by,updated_by) VALUES(902,'PHOTO-M','摄像头','camera','台',1,1)");
  jdbc.update("INSERT INTO sop_resource_device(id,code,name,model_id,station_id,created_by,updated_by) VALUES(902,'PHOTO-C','摄像头B',902,902,1,1)");
  var created=create("PHOTO",2,owner);String id=value(created,"$.data.id"),rid=value(created,"$.data.draft.id"),sid=value(add(id,rid,"检测",0),"$.data.steps[0].id");
  var first=uploadCamera(id,rid,sid,1,png(),owner,902L,null);assertEquals(200,first.statusCode());
  String aid=value(first,"$.data.steps[0].attachments[0].id");
  assertEquals("902",value(first,"$.data.steps[0].detection.camera_id"));assertEquals(aid,value(first,"$.data.steps[0].detection.attachment_id"));assertEquals(true,value(first,"$.data.steps[0].detection.confirmed"));
  var rejected=uploadCamera(id,rid,sid,2,png(),owner,9999L,aid);assertEquals(400,rejected.statusCode());assertEquals(aid,value(preview(id,rid),"$.data.steps[0].attachments[0].id"));
  var legacy=create("LEGACY-IMAGE",2,owner);String legacyId=value(legacy,"$.data.id"),legacyRevision=value(legacy,"$.data.draft.id"),legacyStep=value(add(legacyId,legacyRevision,"旧图",0),"$.data.steps[0].id");
  String legacyFile=value(upload(legacyId,legacyRevision,legacyStep,1,png(),owner),"$.data.steps[0].attachments[0].file_id");
  jdbc.update("INSERT INTO sop_step_attachment(step_id,file_id,sort_order) VALUES(?,?,99)",sid,legacyFile);
  assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM sop_step_attachment WHERE step_id=?",Integer.class,sid));
  var second=uploadCamera(id,rid,sid,2,png(),owner,902L,aid);assertEquals(200,second.statusCode());
  assertEquals(1,((List<?>)value(second,"$.data.steps[0].attachments")).size());
  String next=value(second,"$.data.steps[0].attachments[0].id");assertNotEquals(aid,next);assertEquals(next,value(second,"$.data.steps[0].detection.attachment_id"));
  assertEquals(next,value(second,"$.data.steps[0].image.id"));
  jdbc.update("INSERT INTO sop_step_attachment(step_id,file_id,sort_order) VALUES(?,?,99)",sid,legacyFile);
  var removed=send("DELETE",path(id,rid)+"/step/"+sid+"/image/"+next,Map.of("version",3),owner);
  assertEquals(200,removed.statusCode());assertTrue(((List<?>)value(removed,"$.data.steps[0].attachments")).isEmpty());
  assertNull(((Map<?,?>)value(removed,"$.data.steps[0]")).get("image"));
  assertEquals(false,value(removed,"$.data.steps[0].detection.enabled"));

 }
 @Test void authoringRejectsInvalidGeometryWrongImageScopeAndRollsBackWholeUnit()throws Exception{
  var created=create("VISUAL-INVALID",2,owner);String id=value(created,"$.data.id"),rid=value(created,"$.data.draft.id"),step=value(add(id,rid,"原步骤",0),"$.data.steps[0].id");String aid=value(upload(id,rid,step,1,png(),owner),"$.data.steps[0].attachments[0].id");String url=path(id,rid)+"/step/"+step+"/authoring";
  var invalid=guide();invalid.put("x",0.9);assertEquals(400,send("PUT",url,Map.of("step",authorStep(0,2),"visuals",List.of(visual(aid,invalid))),owner).statusCode());
  var valid=visual(aid,guide());var foreign=visual("999999",guide());assertEquals(404,send("PUT",url,Map.of("step",authorStep(0,2),"visuals",List.of(valid,foreign)),owner).statusCode());
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sop_step_visual",Integer.class));assertEquals("原步骤",value(preview(id,rid),"$.data.steps[0].title"));
  assertEquals(2,((Number)value(preview(id,rid),"$.data.version")).intValue());
  var wrongSize=new HashMap<>(valid);wrongSize.put("image_width",4);assertEquals(400,send("PUT",url,Map.of("step",authorStep(0,2),"visuals",List.of(wrongSize)),owner).statusCode());
  var duplicate=new HashMap<>(valid);duplicate.put("guide_items",List.of(guide(),guide()));assertEquals(400,send("PUT",url,Map.of("step",authorStep(0,2),"visuals",List.of(duplicate)),owner).statusCode());
  var badVoice=new HashMap<>(authorStep(0,2));badVoice.put("voice_text","");assertEquals(400,send("PUT",url,Map.of("step",badVoice,"visuals",List.of(valid)),owner).statusCode());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sop_step_visual",Integer.class));
 }
 @Test void ordinaryBindingRequiresEditorPermissionAndSourceScope()throws Exception{
  var created=create("EDITOR-BIND",2,owner);String id=value(created,"$.data.id"),rid=value(created,"$.data.draft.id");add(id,rid,"步骤",0);publish(id,rid,1,owner);
  assertEquals(403,send("POST","/project/10/sop",Map.of("sop_id",id,"revision_id",rid,"target_version",0),author).statusCode());
  jdbc.update("INSERT INTO sys_permission(id,code) VALUES(99,'sop:bind')");jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(10,99)");
  assertEquals(0,((List<?>)value(send("GET","/project/20/sop/options",null,outsider),"$.data")).size());
  var bound=send("POST","/project/10/sop",Map.of("sop_id",id,"revision_id",rid,"target_version",0),author);String bid=value(bound,"$.data.items[0].id");
  jdbc.update("UPDATE sop_project_member SET access_level=0 WHERE project_id=10 AND user_id=4");
  assertFalse((Boolean)value(send("GET","/project/10/sop/list",null,author),"$.data.can_manage"));
  assertEquals(403,send("DELETE","/project/10/sop/"+bid,Map.of("version",0,"target_version",1),author).statusCode());
  assertEquals(200,send("GET","/project/10/sop/"+bid+"/preview",null,author).statusCode());
 }
 @Test void concurrentRetirementAndBindingCannotCreateRetiredActiveUse()throws Exception{
  var created=create("BIND-RACE",2,owner);String id=value(created,"$.data.id"),rid=value(created,"$.data.draft.id");add(id,rid,"步骤",0);publish(id,rid,1,owner);
  var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
  try{
   var bind=pool.submit(()->{gate.await();return send("POST","/project/20/sop",Map.of("sop_id",id,"revision_id",rid,"target_version",0),root).statusCode();});
   var retire=pool.submit(()->{gate.await();return send("PUT",path(id,rid)+"/retire",Map.of("version",2),owner).statusCode();});
   gate.countDown();var statuses=new ArrayList<>(List.of(bind.get(15,TimeUnit.SECONDS),retire.get(15,TimeUnit.SECONDS)));Collections.sort(statuses);assertEquals(List.of(200,409),statuses);
   assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sop_project_binding b JOIN sop_revision r ON r.id=b.revision_id WHERE b.status=1 AND r.status=2",Integer.class));
  }finally{pool.shutdownNow();}
 }
 HttpResponse<String> upload(String id,String rid,String step,int version,byte[] bytes,String token)throws Exception{
  return uploadCamera(id,rid,step,version,bytes,token,null,null);
 }
 HttpResponse<String> uploadCamera(String id,String rid,String step,int version,byte[] bytes,String token,Long camera,String replaced)throws Exception{
  String boundary="sop-test-boundary";var out=new ByteArrayOutputStream();
  if(camera!=null)out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"camera_id\"\r\n\r\n"+camera+"\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
  if(replaced!=null)out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"replace_attachment_id\"\r\n\r\n"+replaced+"\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
  out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"version\"\r\n\r\n"+version+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"step.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));out.write(bytes);out.write(("\r\n--"+boundary+"--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
  var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path(id,rid)+"/step/"+step+"/image")).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()));if(token!=null)b.header("Authorization","Bearer "+token);return http.send(b.build(),HttpResponse.BodyHandlers.ofString());
 }
 @Test void concurrentPublishAndConcurrentDraftCreationHaveOneWinner()throws Exception{
  var created=create("RACE",2,owner);String id=value(created,"$.data.id"),rid=value(created,"$.data.draft.id");add(id,rid,"操作",0);
  race(()->publish(id,rid,1,owner).statusCode());
  race(()->send("POST","/sop/"+id+"/draft",Map.of("source_revision_id",rid,"version",2),owner).statusCode());
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sop_revision WHERE sop_id=? AND status=0 AND deleted_at IS NULL",Integer.class,id));
 }
 @Test void sopOwnerWithPublishPermissionCanPublishButReadOnlyMembersCannotEdit()throws Exception{
  var created=create("RESPONSIBLE",4,author);String id=value(created,"$.data.id"),rid=value(created,"$.data.draft.id");
  assertEquals(200,add(id,rid,"步骤",0).statusCode());assertEquals(200,publish(id,rid,1,author).statusCode());
  jdbc.update("UPDATE sop_project_member SET access_level=0 WHERE project_id=10 AND user_id=4");
  // Raw fixture downgrade only: business member APIs would refuse a downgrade before ownership handover.
  assertEquals(403,send("POST","/sop/"+id+"/draft",Map.of("source_revision_id",rid,"version",2),author).statusCode());
  assertEquals(200,previewWith(id,rid,author).statusCode());
 }
 @Test void removingMemberAndCreatingOwnedSopCannotLeaveInactiveOwnership()throws Exception{
  var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
  try{
   var create=pool.submit(()->{gate.await();return create("MEMBER-RACE",4,root).statusCode();});
   var remove=pool.submit(()->{gate.await();return send("DELETE","/project/10/member/4",Map.of("version",0),root).statusCode();});
   gate.countDown();int a=create.get(15,TimeUnit.SECONDS),b=remove.get(15,TimeUnit.SECONDS);
   assertTrue(a==200&&b==409||a==400&&b==200,"create="+a+", remove="+b);
   assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sop_document d JOIN sop_project_member m ON m.project_id=d.managing_project_id AND m.user_id=d.owner_user_id WHERE d.deleted_at IS NULL AND m.member_status=0",Integer.class));
  }finally{pool.shutdownNow();}
 }
 void race(Callable<Integer> action)throws Exception{var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);try{Callable<Integer> op=()->{gate.await();return action.call();};var a=pool.submit(op);var b=pool.submit(op);gate.countDown();var statuses=new ArrayList<>(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)));Collections.sort(statuses);assertEquals(List.of(200,409),statuses);}finally{pool.shutdownNow();}}
 HttpResponse<String> create(String code,long ownerId,String token)throws Exception{return send("POST","/sop",Map.of("code","SOP-202610161745-"+String.format(java.util.Locale.ROOT,"%06X",codes.incrementAndGet()),"name","通用SOP","managing_project_id","10","owner_user_id",ownerId),token);}
 String path(String id,String rid){return "/sop/"+id+"/version/"+rid;}
 HttpResponse<String> add(String id,String rid,String title,int version)throws Exception{return send("POST",path(id,rid)+"/step",Map.of("title",title,"instruction","按照说明操作","version",version),owner);}
 HttpResponse<String> publish(String id,String rid,int version,String token)throws Exception{return send("PUT",path(id,rid)+"/publish",Map.of("version",version),token);}
 HttpResponse<String> preview(String id,String rid)throws Exception{return previewWith(id,rid,owner);}
 HttpResponse<String> previewWith(String id,String rid,String token)throws Exception{return send("GET",path(id,rid),null,token);}
 HttpResponse<String> send(String method,String path,Object body,String token)throws Exception{var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path));if(token!=null)b.header("Authorization","Bearer "+token);if(body!=null)b.header("Content-Type","application/json");b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));return http.send(b.build(),HttpResponse.BodyHandlers.ofString());}
 @SuppressWarnings("unchecked") <T>T value(HttpResponse<String> response,String path){assertEquals(200,response.statusCode(),response.body());return (T)JsonPath.read(response.body(),path);}
}
