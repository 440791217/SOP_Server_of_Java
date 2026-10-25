import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;
class ApplyStepDetection {
 static String resolve(Object raw){String s=String.valueOf(raw);if(s.startsWith("${")&&s.endsWith("}")){int colon=s.indexOf(':');String key=s.substring(2,colon);return System.getenv().getOrDefault(key,s.substring(colon+1,s.length()-1));}return s;}
 public static void main(String[] args) throws Exception {
  Map<?,?> config=new Yaml().load(Files.readString(Path.of("src/main/resources/application.yml")));
  Map<?,?> spring=(Map<?,?>)config.get("spring"), ds=(Map<?,?>)spring.get("datasource");
  String url=resolve(ds.get("url"));
  if(!url.matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1)(:[0-9]+)?/sop_dev(\\?.*)?"))throw new IllegalStateException("Expected local sop_dev database; no changes made");
  try(Connection c=DriverManager.getConnection(url,resolve(ds.get("username")),resolve(ds.get("password")))) {
   boolean exists;
   try(var q=c.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='sop_step_detection'");var r=q.executeQuery()){r.next();exists=r.getInt(1)>0;}
   if(!exists){String sql=Files.readString(Path.of("../../SOP_MySQL/sop-module-v1/migrations/002_step_detection.sql")).replaceAll("(?m)^--.*$","").trim();try(var q=c.createStatement()){q.execute(sql);}System.out.println("Created sop_step_detection in local sop_dev; existing data unchanged.");}
   else System.out.println("sop_step_detection already exists; no DDL executed.");
   try(var q=c.createStatement();var r=q.executeQuery("SELECT step_id,enabled,camera_id,attachment_id,confirmed FROM sop_step_detection LIMIT 0")){System.out.println("Schema verified.");}
  }
 }
}
