package org.jsut;

import com.jayway.jsonpath.JsonPath;
import org.jsut.modules.user.dto.LoginRequest;
import org.jsut.modules.user.service.AuthService;
import org.jsut.modules.user.service.RootInitializationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
        "spring.datasource.url=jdbc:h2:mem:sop_user_management_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:login-schema.sql",
        "sop.bootstrap.root.enabled=false", "server.port=0"
})
class UserManagementIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired RootInitializationService initializer;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Value("${local.server.port}") int port;
    String root;
    final HttpClient client=HttpClient.newHttpClient();

    @BeforeEach void seed(){
        for(String table:List.of("sys_user_session","sys_user_role","sys_role_permission","sys_user","sys_team","sys_role","sys_permission"))jdbc.execute("DELETE FROM "+table);
        jdbc.update("INSERT INTO sys_user(id,username,real_name,status,is_superuser) VALUES(1,'root','超级管理员',0,1)");
        initializer.initialize("Root123!");
        jdbc.update("INSERT INTO sys_team(id,name,status) VALUES(10,'测试班组',1),(11,'停用班组',0)");
        jdbc.update("INSERT INTO sys_role(id,code,status,name) VALUES(10,'user_admin',1,'用户管理员'),(11,'member',1,'普通用户'),(12,'external',1,'其他角色')");
        int i=0;
        for(String code:List.of("user:list","user:create","user:update","user:status","user:delete","user:reset-password","user:assign-role")){
            jdbc.update("INSERT INTO sys_permission(id,code) VALUES(?,?)",++i,code);
            jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(10,?)",i);
        }
        root=auth.login(new LoginRequest("root","Root123!")).access_token();
    }

    @Test void createListFilterDetailAndEditPreserveContract()throws Exception{
        var response=send("POST","/user",create(" Alice ","CARD-1",0),root);
        assertEquals(200,response.statusCode(),response.body());
        String id=read(response,"$.data.id");
        assertEquals("alice",read(response,"$.data.username"));
        assertEquals("测试班组",read(response,"$.data.shift_group"));
        assertEquals("member",read(response,"$.data.roles[0].code"));
        String hash=jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE id=?",String.class,Long.parseLong(id));
        assertTrue(encoder.matches("User123!",hash));
        assertFalse(response.body().contains("password_hash"));assertFalse(response.body().contains("$argon2"));
        assertEquals(1,jdbc.queryForObject("SELECT must_change_password FROM sys_user WHERE id=?",Integer.class,Long.parseLong(id)));
        var list=send("GET","/user/list?page=1&limit=10&status=0&team_id=10&username=ali",null,root);
        assertEquals(1,((Number)read(list,"$.data.total")).intValue());
        assertEquals(400,send("GET","/user/list?limit=101",null,root).statusCode());
        assertEquals(400,send("GET","/user/list?status=invalid",null,root).statusCode());
        assertEquals(400,send("GET","/user/list?team_id=bad",null,root).statusCode());
        var change=profile(0);change.put("real_name","新姓名");
        var saved=send("PUT","/user/"+id,change,root);
        assertEquals(200,saved.statusCode(),saved.body());
        assertEquals(1,((Number)read(saved,"$.data.version")).intValue());
        assertEquals("新姓名",read(send("GET","/user/"+id,null,root),"$.data.real_name"));
        assertEquals(409,send("PUT","/user/"+id,change,root).statusCode());
        assertEquals(400,send("POST","/user",create("bad name",null,1),root).statusCode());
        var invalidTeam=create("badteam",null,1);invalidTeam.put("team_id","11");
        assertEquals(400,send("POST","/user",invalidTeam,root).statusCode());
    }

    @Test void duplicateKeysSoftDeleteCardReuseAndSessionRevocation()throws Exception{
        var user=send("POST","/user",create("worker","CARD-A",1),root);String id=read(user,"$.data.id");
        String session=auth.login(new LoginRequest("worker","User123!")).access_token();
        assertEquals(409,send("POST","/user",create("WORKER",null,1),root).statusCode());
        assertEquals(409,send("POST","/user",create("other","CARD-A",1),root).statusCode());
        assertEquals(200,send("DELETE","/user/"+id,Map.of("version",0),root).statusCode());
        assertEquals(401,send("GET","/user/me",null,session).statusCode());
        assertEquals(404,send("GET","/user/"+id,null,root).statusCode());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id=? AND deleted_at IS NOT NULL",Integer.class,Long.parseLong(id)));
        assertEquals(409,send("POST","/user",create("worker",null,1),root).statusCode());
        assertEquals(200,send("POST","/user",create("replacement","CARD-A",1),root).statusCode());
    }

    @Test void disableEnableDepartureAndResetPasswordInvalidateOldSessions()throws Exception{
        String id=read(send("POST","/user",create("worker",null,1),root),"$.data.id");
        String old=auth.login(new LoginRequest("worker","User123!")).access_token();
        assertEquals(200,send("PUT","/user/"+id+"/status",Map.of("version",0,"status",0),root).statusCode());
        assertEquals(401,send("GET","/user/me",null,old).statusCode());
        assertEquals(200,send("PUT","/user/"+id+"/status",Map.of("version",1,"status",1),root).statusCode());
        String next=auth.login(new LoginRequest("worker","User123!")).access_token();
        assertEquals(200,send("PUT","/user/"+id+"/password",Map.of("version",2,"new_password","Next123!"),root).statusCode());
        assertEquals(401,send("GET","/user/me",null,next).statusCode());
        String fresh=auth.login(new LoginRequest("worker","Next123!")).access_token();
        assertEquals(Boolean.TRUE,read(send("GET","/user/me",null,fresh),"$.data.must_change_password"));
        assertEquals(403,send("GET","/user/list",null,fresh).statusCode());
        assertEquals(200,send("PUT","/user/"+id+"/status",Map.of("version",3,"status",2),root).statusCode());
        assertEquals(401,send("GET","/user/me",null,fresh).statusCode());
    }

    @Test void ordinaryUsersCannotReadManagementAndRootCannotBeMutated()throws Exception{
        String member=createActor("viewer",11);
        assertEquals(403,send("GET","/user/list",null,member).statusCode());
        assertEquals(403,send("GET","/user/1",null,member).statusCode());
        assertEquals(403,send("POST","/user",create("blocked",null,1),member).statusCode());
        assertEquals(401,send("GET","/user/list",null,null).statusCode());
        var version=read(send("GET","/user/1",null,root),"$.data.version");
        assertEquals(403,send("PUT","/user/1",profile(((Number)version).intValue()),root).statusCode());
        assertEquals(403,send("DELETE","/user/1",Map.of("version",version),root).statusCode());
        assertEquals(403,send("PUT","/user/1/status",Map.of("version",version,"status",0),root).statusCode());
        assertEquals(403,send("PUT","/user/1/password",Map.of("version",version,"new_password","Next123!"),root).statusCode());
    }

    @Test void roleAssignmentCannotEscalateOrChangeSelfAndIsAtomicWithProfile()throws Exception{
        String admin=createActor("manager",10);
        long adminId=jdbc.queryForObject("SELECT id FROM sys_user WHERE username='manager'",Long.class);
        String target=read(send("POST","/user",create("target",null,1),root),"$.data.id");
        var update=profile(0);update.put("role_ids",List.of("12"));update.put("real_name","不得保存");
        assertEquals(403,send("PUT","/user/"+target,update,admin).statusCode());
        assertEquals("测试人员",read(send("GET","/user/"+target,null,root),"$.data.real_name"));
        assertEquals(403,send("PUT","/user/"+adminId+"/roles",Map.of("version",0,"role_ids",List.of()),admin).statusCode());
        assertEquals(403,send("DELETE","/user/"+adminId,Map.of("version",0),admin).statusCode());
        assertEquals(403,send("PUT","/user/"+adminId+"/status",Map.of("version",0,"status",2),admin).statusCode());
        assertEquals(200,send("PUT","/user/"+target+"/roles",Map.of("version",0,"role_ids",List.of("10")),admin).statusCode());
        assertEquals("user_admin",read(send("GET","/user/"+target,null,root),"$.data.roles[0].code"));
        var options=send("GET","/user/role/options",null,admin);
        assertEquals(1,((List<?>)read(options,"$.data")).size());
        String keepMember=read(send("POST","/user",create("keepmember",null,1),root),"$.data.id");
        assertEquals(200,send("PUT","/user/"+keepMember+"/roles",Map.of("version",0,"role_ids",List.of("11","10")),admin).statusCode());
        var elevated=create("special",null,1);elevated.put("role_ids",List.of("12"));
        String protectedId=read(send("POST","/user",elevated,root),"$.data.id");
        assertEquals(403,send("PUT","/user/"+protectedId+"/password",Map.of("version",0,"new_password","Take123!"),admin).statusCode());
    }

    @Test void concurrentEditsOfSameVersionAllowOnlyOneWinner()throws Exception{
        String id=read(send("POST","/user",create("racer",null,1),root),"$.data.id");
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            var a=pool.submit(()->send("PUT","/user/"+id,profile(0),root).statusCode());
            var b=pool.submit(()->send("PUT","/user/"+id,profile(0),root).statusCode());
            var results=new ArrayList<>(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)));
            Collections.sort(results);assertEquals(List.of(200,409),results);
        }finally{pool.shutdownNow();}
    }

    String createActor(String name,long role){
        jdbc.update("INSERT INTO sys_user(username,real_name,password_hash,status,must_change_password) VALUES(?,?,?,1,0)",name,name,encoder.encode("User123!"));
        long id=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,name);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)",id,role);
        return auth.login(new LoginRequest(name,"User123!")).access_token();
    }
    Map<String,Object> create(String name,String card,int status){
        var data=profile(0);data.remove("version");data.put("username",name);data.put("password","User123!");data.put("status",status);data.put("card_number",card);return data;
    }
    Map<String,Object> profile(int version){
        var data=new HashMap<String,Object>();data.put("real_name","测试人员");data.put("team_id","10");data.put("card_number",null);
        data.put("phone","+86 123456789");data.put("avatar_url",null);data.put("version",version);return data;
    }
    <T>T read(HttpResponse<String> response,String path){assertEquals(200,response.statusCode(),response.body());return JsonPath.read(response.body(),path);}
    HttpResponse<String> send(String method,String path,Object body,String token)throws Exception{
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
        if(token!=null)builder.header("Authorization","Bearer "+token);
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
}
