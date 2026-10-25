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
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.datasource.url=jdbc:h2:mem:sop_project_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:login-schema.sql,classpath:project-schema.sql,classpath:sop-schema.sql,classpath:resource-schema.sql",
 "sop.bootstrap.root.enabled=false","server.port=0"
})
class ProjectIntegrationTest {
 private final java.util.concurrent.atomic.AtomicLong codes=new java.util.concurrent.atomic.AtomicLong();
 String code(String prefix){return prefix+"-202610161745-"+String.format(java.util.Locale.ROOT,"%06X",codes.incrementAndGet());}
 @Test void clientCodeIsRequiredValidatedAndPreservedAfterEditsAndDeletion()throws Exception{
  String code="PRJ-202610161745-A7F29C";
  var body=Map.of("code",code,"name","客户端编号","owner_user_id",2);
  var created=send("POST","/project",body,root);String id=value(created,"$.data.id");assertEquals(code,value(created,"$.data.code"));
  assertEquals(400,send("POST","/project",Map.of("name","缺少编号","owner_user_id",2),root).statusCode());
  for(String invalid:List.of("SOP-202610161745-A7F29C","PRJ-202602301745-A7F29C","PRJ-202610161745-a7f29c","PRJ-20261016-0001"))assertEquals(400,send("POST","/project",Map.of("code",invalid,"name","错误编号","owner_user_id",2),root).statusCode());
  assertEquals(409,send("POST","/project",body,root).statusCode());
  assertEquals(code,value(send("PUT","/project/"+id,Map.of("name","改名","version",0),root),"$.data.code"));
  assertEquals(200,send("DELETE","/project/"+id,Map.of("version",1),root).statusCode());assertEquals(409,send("POST","/project",body,root).statusCode());
 }
 @Test void subprojectClientCodesKeepExistingParentScopedUniqueness()throws Exception{
  String p=value(create("P",root),"$.data.id"),q=value(create("Q",root),"$.data.id");
  String code="SUB-202610161745-A7F29C";var body=Map.of("code",code,"name","子项目","owner_user_id",2);
  assertEquals(code,value(send("POST","/project/"+p+"/subproject",body,owner),"$.data.code"));
  assertEquals(409,send("POST","/project/"+p+"/subproject",body,owner).statusCode());
  assertEquals(200,send("POST","/project/"+q+"/subproject",body,owner).statusCode());
 }
 @Test void oldSecondAndMillisecondCodesRemainReadableAndEditable()throws Exception{
  String id=value(create("历史编号",root),"$.data.id"),old="PRJ-20261016174530123-A7F29C";
  jdbc.update("UPDATE sop_project SET code=? WHERE id=?",old,id);
  assertEquals(old,value(send("GET","/project/"+id,null,root),"$.data.code"));
  assertEquals(old,value(send("PUT","/project/"+id,Map.of("name","保留历史编号","version",0),root),"$.data.code"));
  assertEquals(400,send("POST","/project",Map.of("code",old,"name","旧格式新建","owner_user_id",2),root).statusCode());
 }
 @Test void racingTheSameClientCodeCreatesExactlyOneProject()throws Exception{
  var body=Map.of("code","PRJ-202610161745-ABCDEF","name","同编号并发","owner_user_id",2);
  var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
  try{Callable<Integer> op=()->{gate.await();return send("POST","/project",body,root).statusCode();};var a=pool.submit(op);var b=pool.submit(op);gate.countDown();var statuses=new ArrayList<>(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)));Collections.sort(statuses);assertEquals(List.of(200,409),statuses);}finally{pool.shutdownNow();}
 }
 @Autowired JdbcTemplate jdbc;
 @Autowired AuthService auth;
 @Autowired RootInitializationService initializer;
 @Autowired PasswordEncoder encoder;
 @Autowired ObjectMapper json;
 @Value("${local.server.port}") int port;
 String root,owner,other;
 final HttpClient http=HttpClient.newHttpClient();
 @BeforeEach void seed(){
  jdbc.update("DELETE FROM sop_camera_config WHERE device_id IS NOT NULL");
  for(String t:List.of("sop_task_standard","sop_task","sop_step_resource_requirement","sop_device_connection","sop_resource_device","sop_resource_model","sop_project_station","sop_station","sop_location","sop_step_visual","sop_project_binding","sop_subproject_binding","sop_step_attachment","sop_file","sop_step","sop_revision","sop_document","sop_project_activity","sop_subproject","sop_project_member","sop_project","sys_user_session","sys_user_role","sys_role_permission","sys_user","sys_team","sys_role","sys_permission"))jdbc.execute("DELETE FROM "+t);
  jdbc.update("INSERT INTO sys_user(id,username,real_name,status,is_superuser) VALUES(1,'root','Root',0,1)");initializer.initialize("Root123!");
  String hash=encoder.encode("User123!");
  jdbc.update("INSERT INTO sys_user(id,username,real_name,password_hash,status,must_change_password) VALUES(2,'owner','负责人',?,1,0),(3,'other','其他人',?,1,0)",hash,hash);
  root=auth.login(new LoginRequest("root","Root123!")).access_token();owner=auth.login(new LoginRequest("owner","User123!")).access_token();other=auth.login(new LoginRequest("other","User123!")).access_token();
 }
 @Test void createQueryEditAndScope()throws Exception{
  var p=create("prj-1",root);String id=value(p,"$.data.id");assertTrue(((String)value(p,"$.data.code")).matches("PRJ-[0-9]{12}-[0-9A-F]{6}"));
  assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM sop_project_member WHERE project_id=?",Integer.class,id));
  assertEquals(1,((Number)value(send("GET","/project/list?status=0",null,owner),"$.data.total")).intValue());
  assertEquals(0,((Number)value(send("GET","/project/list",null,other),"$.data.total")).intValue());
  assertEquals(403,send("GET","/project/"+id,null,other).statusCode());
  assertEquals(401,send("GET","/project/"+id,null,null).statusCode());
  assertEquals(403,create("BLOCKED",other).statusCode());
  assertEquals(400,send("GET","/project/list?status=bad",null,root).statusCode());
  assertEquals(400,send("GET","/project/list?limit=101",null,root).statusCode());
  var edit=Map.of("name","修改名称","version",0);
  assertEquals(200,send("PUT","/project/"+id,edit,owner).statusCode());
  assertEquals(409,send("PUT","/project/"+id,edit,owner).statusCode());
  assertEquals(200,create("PRJ-1",root).statusCode());
  assertEquals(200,create("错误编号",root).statusCode());
 }
 @Test void childrenSortingAndCrossProjectProtection()throws Exception{
  String p=value(create("P",root),"$.data.id"),q=value(create("Q",root),"$.data.id");
  String a=value(createSub(p,"A",2,owner),"$.data.id"),b=value(createSub(p,"B",1,owner),"$.data.id");
  assertEquals(400,createSub(p,"C",3,owner).statusCode());
  assertEquals(404,send("GET","/project/"+q+"/subproject/"+a,null,root).statusCode());
  assertEquals(404,send("PUT","/project/"+q+"/subproject/"+a,Map.of("name","bad","owner_user_id","2","version",0),root).statusCode());
  assertEquals(400,send("PUT","/project/"+p+"/subproject/sort",Map.of("ids",List.of(a,a),"version",2),owner).statusCode());
  assertEquals(200,send("PUT","/project/"+p+"/subproject/sort",Map.of("ids",List.of(b,a),"version",2),owner).statusCode());
  assertEquals(b,value(send("GET","/project/"+p+"/subproject/list",null,owner),"$.data[0].id"));
  assertEquals(409,send("PUT","/project/"+p+"/subproject/sort",Map.of("ids",List.of(a,b),"version",2),owner).statusCode());
  assertEquals(200,send("PUT","/project/"+p+"/subproject/"+a,Map.of("name","新子项目名","owner_user_id","2","version",1),owner).statusCode());
 }
 @Test void lifecycleArchiveAndSoftDelete()throws Exception{
  String p=value(create("P",root),"$.data.id"),s=value(createSub(p,"S",2,owner),"$.data.id");
  assertEquals(403,send("DELETE","/project/"+p,Map.of("version",1),owner).statusCode());
  assertEquals(409,status(p,s,1,0).statusCode());
  assertEquals(200,status(p,null,1,1).statusCode());
  assertEquals(409,status(p,null,2,2).statusCode());
  assertEquals(200,status(p,s,1,0).statusCode());
  assertEquals(403,send("DELETE","/project/"+p+"/subproject/"+s,Map.of("version",1),owner).statusCode());
  assertEquals(200,status(p,s,2,1).statusCode());
  assertEquals(200,status(p,null,2,4).statusCode());
  assertEquals(409,createSub(p,"NEXT",2,owner).statusCode());
  assertEquals(200,status(p,null,1,5).statusCode());
  assertEquals(200,status(p,null,3,6).statusCode());
  assertEquals(3,((Number)value(send("GET","/project/"+p+"/subproject/"+s,null,owner),"$.data.status")).intValue());
  assertEquals(409,status(p,null,1,7).statusCode());
  String d=value(create("DELETE",root),"$.data.id"),c=value(createSub(d,"CHILD",2,owner),"$.data.id");
  assertEquals(200,send("DELETE","/project/"+d+"/subproject/"+c,Map.of("version",0),root).statusCode());
  assertEquals(200,send("DELETE","/project/"+d,Map.of("version",2),root).statusCode());
  assertEquals(404,send("GET","/project/"+d,null,root).statusCode());
  assertEquals(200,create("DELETE",root).statusCode());
 }
 @Test void memberFunctionPermissionsAndForcedPassword()throws Exception{
  String p=value(create("P",root),"$.data.id");
  jdbc.update("INSERT INTO sop_project_member(project_id,user_id,access_level,created_by,updated_by) VALUES(?,3,0,1,1)",p);
  assertEquals(403,send("GET","/project/"+p,null,other).statusCode());
  jdbc.update("INSERT INTO sys_role(id,code,status,name) VALUES(1,'project_viewer',1,'Viewer')");
  jdbc.update("INSERT INTO sys_permission(id,code) VALUES(1,'project:read'),(2,'project:list'),(3,'project:update')");
  jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(1,1),(1,2),(1,3)");jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(3,1)");
  assertEquals(200,send("GET","/project/"+p,null,other).statusCode());
  assertEquals(403,send("PUT","/project/"+p,Map.of("name","forbidden","version",0),other).statusCode());
  jdbc.update("UPDATE sop_project_member SET access_level=1 WHERE project_id=? AND user_id=3",p);
  assertEquals(200,send("PUT","/project/"+p,Map.of("name","allowed","version",0),other).statusCode());
  assertEquals(403,statusWith(p,3,1,other).statusCode());
  jdbc.update("UPDATE sys_user SET must_change_password=1 WHERE id=3");
  assertEquals(403,send("GET","/project/list",null,other).statusCode());
 }
 @Test void restorePreservesStatesAndIndependentArchives()throws Exception{
  String p=value(create("RESTORE",root),"$.data.id");
  String a=value(createSub(p,"A",2,owner),"$.data.id"),b=value(createSub(p,"B",2,owner),"$.data.id"),c=value(createSub(p,"C",2,owner),"$.data.id");
  jdbc.update("UPDATE sop_project SET status=1 WHERE id=?",p);
  jdbc.update("UPDATE sop_subproject SET status=1 WHERE id=?",b);
  jdbc.update("UPDATE sop_subproject SET status=2 WHERE id=?",c);
  assertEquals(200,status(p,a,3,0).statusCode());
  assertEquals(200,status(p,null,3,4).statusCode());
  assertEquals(Boolean.TRUE,value(send("GET","/project/"+p,null,owner),"$.data.can_restore"));
  assertEquals(403,send("PUT","/project/"+p+"/restore",Map.of("version",5),other).statusCode());
  assertEquals(409,send("PUT","/project/"+p+"/restore",Map.of("version",4),owner).statusCode());
  assertEquals(409,send("PUT","/project/"+p+"/subproject/"+a+"/restore",Map.of("version",1),owner).statusCode());
  assertEquals(200,send("PUT","/project/"+p+"/restore",Map.of("version",5),owner).statusCode());
  assertEquals(1,jdbc.queryForObject("SELECT status FROM sop_project WHERE id=?",Integer.class,p));
  assertEquals(3,jdbc.queryForObject("SELECT status FROM sop_subproject WHERE id=?",Integer.class,a));
  assertEquals(1,jdbc.queryForObject("SELECT status FROM sop_subproject WHERE id=?",Integer.class,b));
  assertEquals(2,jdbc.queryForObject("SELECT status FROM sop_subproject WHERE id=?",Integer.class,c));
  assertEquals(200,send("PUT","/project/"+p+"/subproject/"+a+"/restore",Map.of("version",1),owner).statusCode());
  assertEquals(0,jdbc.queryForObject("SELECT status FROM sop_subproject WHERE id=?",Integer.class,a));
  assertEquals(409,send("PUT","/project/"+p+"/restore",Map.of("version",7),owner).statusCode());
  // A second archive/restore cycle captures new states and does not reuse an old snapshot.
  assertEquals(200,status(p,null,3,7).statusCode());
  assertEquals(200,send("PUT","/project/"+p+"/restore",Map.of("version",8),root).statusCode());
  assertEquals(0,jdbc.queryForObject("SELECT status FROM sop_subproject WHERE id=?",Integer.class,a));
 }
 @Test void childRestoreRequiresDedicatedPermissionAndValidParentState()throws Exception{
  String p=value(create("CHILD",root),"$.data.id"),s=value(createSub(p,"S",2,owner),"$.data.id");
  jdbc.update("INSERT INTO sop_project_member(project_id,user_id,access_level,created_by,updated_by) VALUES(?,3,1,1,1)",p);
  jdbc.update("UPDATE sop_subproject SET owner_user_id=3,status=3,archived_from_status=1 WHERE id=?",s);
  assertEquals(403,send("PUT","/project/"+p+"/subproject/"+s+"/restore",Map.of("version",0),other).statusCode());
  jdbc.update("INSERT INTO sys_role(id,code,status,name) VALUES(1,'restorer',1,'Restorer')");
  jdbc.update("INSERT INTO sys_permission(id,code) VALUES(1,'subproject:restore')");
  jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(1,1)");jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(3,1)");
  assertEquals(409,send("PUT","/project/"+p+"/subproject/"+s+"/restore",Map.of("version",0),other).statusCode());
  jdbc.update("UPDATE sop_project SET status=1 WHERE id=?",p);
  assertEquals(200,send("PUT","/project/"+p+"/subproject/"+s+"/restore",Map.of("version",0),other).statusCode());
  assertEquals(1,jdbc.queryForObject("SELECT status FROM sop_subproject WHERE id=?",Integer.class,s));
 }
 @Test void legacyArchiveRestoresSafelyWithoutGuessingHistory()throws Exception{
  String p=value(create("LEGACY",root),"$.data.id");
  jdbc.update("UPDATE sop_project SET status=3 WHERE id=?",p);
  assertEquals(200,send("PUT","/project/"+p+"/restore",Map.of("version",0),owner).statusCode());
  assertEquals(0,jdbc.queryForObject("SELECT status FROM sop_project WHERE id=?",Integer.class,p));
 }
 @Test void membershipLifecycleRevokesAccessAndRetainsHistory()throws Exception{
  String p=value(create("MEMBERS",root),"$.data.id");
  assertEquals(403,send("POST","/project/"+p+"/member",Map.of("user_id",3,"access_level",0,"version",0),other).statusCode());
  assertEquals(200,send("POST","/project/"+p+"/member",Map.of("user_id",3,"access_level",0,"version",0),owner).statusCode());
  assertEquals(409,send("POST","/project/"+p+"/member",Map.of("user_id",3,"access_level",0,"version",1),owner).statusCode());
  jdbc.update("INSERT INTO sys_role(id,code,status,name) VALUES(1,'reader',1,'Reader')");
  jdbc.update("INSERT INTO sys_permission(id,code) VALUES(1,'project:read'),(2,'project:list')");
  jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(1,1),(1,2)");jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(3,1)");
  assertEquals(200,send("GET","/project/"+p+"/member/list",null,other).statusCode());
  assertEquals(200,send("PUT","/project/"+p+"/member/3",Map.of("access_level",1,"version",0),owner).statusCode());
  assertEquals(409,send("PUT","/project/"+p+"/member/3",Map.of("access_level",0,"version",0),owner).statusCode());
  assertEquals(200,send("DELETE","/project/"+p+"/member/3",Map.of("version",1),owner).statusCode());
  assertEquals(403,send("GET","/project/"+p,null,other).statusCode());
  assertEquals(0,((Number)value(send("GET","/project/list",null,other),"$.data.total")).intValue());
  assertEquals(200,send("GET","/user/me",null,other).statusCode());
  assertEquals(200,send("POST","/project/"+p+"/member",Map.of("user_id",3,"access_level",0,"version",3),owner).statusCode());
  assertEquals(3,jdbc.queryForObject("SELECT version FROM sop_project_member WHERE project_id=? AND user_id=3",Integer.class,p));
  assertEquals(4,((Number)value(send("GET","/project/"+p+"/activity/list",null,owner),"$.data.total")).intValue());
  assertEquals("MEMBER_RESTORED",value(send("GET","/project/"+p+"/activity/list",null,owner),"$.data.items[0].action"));
 }
 @Test void ownerTransferProtectsResponsibilitiesAndWorksForArchivedProjects()throws Exception{
  String p=value(create("HANDOVER",root),"$.data.id"),s=value(createSub(p,"S",2,owner),"$.data.id");
  assertEquals(409,send("DELETE","/project/"+p+"/member/2",Map.of("version",0),owner).statusCode());
  assertEquals(200,send("POST","/project/"+p+"/member",Map.of("user_id",3,"access_level",1,"version",1),owner).statusCode());
  assertEquals(403,send("PUT","/project/"+p+"/subproject/"+s+"/owner",Map.of("owner_user_id",3,"version",0),other).statusCode());
  assertEquals(400,send("PUT","/project/"+p+"/subproject/"+s,Map.of("name","Sneak","owner_user_id",3,"version",0),owner).statusCode());
  assertEquals(200,status(p,null,3,2).statusCode());
  assertEquals(200,send("PUT","/project/"+p+"/subproject/"+s+"/owner",Map.of("owner_user_id",3,"version",1),owner).statusCode());
  assertEquals(200,send("PUT","/project/"+p+"/owner",Map.of("owner_user_id",3,"version",4),owner).statusCode());
  assertEquals(1,jdbc.queryForObject("SELECT access_level FROM sop_project_member WHERE project_id=? AND user_id=2",Integer.class,p));
  assertEquals(403,send("PUT","/project/"+p+"/owner",Map.of("owner_user_id",2,"version",5),owner).statusCode());
  assertEquals(200,send("GET","/project/"+p,null,other).statusCode());
  assertEquals(409,send("PUT","/project/"+p,Map.of("name","Read only","version",5),other).statusCode());
  assertEquals(3,jdbc.queryForObject("SELECT status FROM sop_project WHERE id=?",Integer.class,p));
  assertEquals(409,send("PUT","/project/"+p+"/member/3",Map.of("access_level",0,"version",0),root).statusCode());
 }
 @Test void removedHistoricalOwnersDoNotBreakForeignKeysAndInvalidNewOwnersAreRejected()throws Exception{
  String p=value(create("HISTORY",root),"$.data.id"),s=value(createSub(p,"S",1,owner),"$.data.id");
  assertEquals(200,send("DELETE","/project/"+p+"/subproject/"+s,Map.of("version",0),root).statusCode());
  assertEquals(200,send("DELETE","/project/"+p+"/member/1",Map.of("version",0),owner).statusCode());
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sop_project_member WHERE project_id=? AND user_id=1 AND member_status=0",Integer.class,p));
  jdbc.update("UPDATE sys_user SET status=0 WHERE id=3");
  assertEquals(400,send("PUT","/project/"+p+"/owner",Map.of("owner_user_id",3,"version",3),root).statusCode());
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sop_project_activity WHERE project_id=? AND action='PROJECT_OWNER_CHANGED'",Integer.class,p));
  assertEquals(400,send("GET","/project/"+p+"/member/list?limit=101",null,owner).statusCode());
 }
 @Test void activeChildOwnerCannotBeRemovedOrDowngradedAndOwnershipPromotesMember()throws Exception{
  String p=value(create("PROTECT",root),"$.data.id");
  assertEquals(200,send("POST","/project/"+p+"/member",Map.of("user_id",3,"access_level",1,"version",0),owner).statusCode());
  String s=value(createSub(p,"S",3,owner),"$.data.id");
  assertEquals(409,send("DELETE","/project/"+p+"/member/3",Map.of("version",0),owner).statusCode());
  assertEquals(409,send("PUT","/project/"+p+"/member/3",Map.of("access_level",0,"version",0),owner).statusCode());
  assertEquals(200,send("PUT","/project/"+p+"/subproject/"+s+"/owner",Map.of("owner_user_id",2,"version",0),owner).statusCode());
  assertEquals(200,send("PUT","/project/"+p+"/member/3",Map.of("access_level",0,"version",0),owner).statusCode());
  assertEquals(200,send("PUT","/project/"+p+"/owner",Map.of("owner_user_id",3,"version",4),owner).statusCode());
  assertEquals(1,jdbc.queryForObject("SELECT access_level FROM sop_project_member WHERE project_id=? AND user_id=3",Integer.class,p));
 }
 @Test void concurrentOwnerTransfersOnlyOneCommitsAndLogs()throws Exception{
  String p=value(create("TRANSFER_RACE",root),"$.data.id");
  var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
  try {
   Callable<Integer> op=()->{gate.await();return send("PUT","/project/"+p+"/owner",Map.of("owner_user_id",3,"version",0),root).statusCode();};
   var a=pool.submit(op);var b=pool.submit(op);gate.countDown();var statuses=new ArrayList<>(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)));Collections.sort(statuses);assertEquals(List.of(200,409),statuses);
   assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sop_project_activity WHERE project_id=? AND action='PROJECT_OWNER_CHANGED'",Integer.class,p));
  }finally{pool.shutdownNow();}
 }
 @Test void concurrentEditsOnlyOneWins()throws Exception{
  String id=value(create("RACE",root),"$.data.id");
  var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
  try {
   Callable<Integer> operation=()->{gate.await();return send("PUT","/project/"+id,Map.of("name","Concurrent","version",0),owner).statusCode();};
   var a=pool.submit(operation);var b=pool.submit(operation);gate.countDown();var statuses=new ArrayList<>(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)));Collections.sort(statuses);assertEquals(List.of(200,409),statuses);
  }finally{pool.shutdownNow();}
 }
 HttpResponse<String> create(String code,String token)throws Exception{return send("POST","/project",Map.of("code",code("PRJ"),"name","通用项目","owner_user_id","2"),token);}
 HttpResponse<String> createSub(String p,String code,int owner,String token)throws Exception{return send("POST","/project/"+p+"/subproject",Map.of("code",code("SUB"),"name","通用子项目","owner_user_id",owner),token);}
 HttpResponse<String> status(String p,String s,int status,int version)throws Exception{return send("PUT","/project/"+p+(s==null?"":"/subproject/"+s)+"/status",Map.of("status",status,"version",version),owner);}
 HttpResponse<String> statusWith(String p,int status,int version,String t)throws Exception{return send("PUT","/project/"+p+"/status",Map.of("status",status,"version",version),t);}
 HttpResponse<String> send(String method,String path,Object body,String token)throws Exception{
  var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path));if(token!=null)b.header("Authorization","Bearer "+token);
  if(body!=null)b.header("Content-Type","application/json");b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
  return http.send(b.build(),HttpResponse.BodyHandlers.ofString());
 }
 @SuppressWarnings("unchecked") <T>T value(HttpResponse<String> r,String path){assertEquals(200,r.statusCode(),r.body());return (T)JsonPath.read(r.body(),path);}
}
