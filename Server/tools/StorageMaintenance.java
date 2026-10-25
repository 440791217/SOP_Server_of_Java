import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;
import org.jsut.common.storage.StoragePaths;
import org.jsut.modules.sop.service.SopImageStorage;
import org.springframework.web.multipart.MultipartFile;

/** Run from Server/. No passwords/tokens or photo contents are printed. Default is read-only verification. */
class StorageMaintenance {
 static String resolve(Object raw,String fallback){String value=raw==null?fallback:String.valueOf(raw);if(value.startsWith("${")&&value.endsWith("}")){int colon=value.indexOf(':');return System.getenv().getOrDefault(value.substring(2,colon),value.substring(colon+1,value.length()-1));}return value;}
 static Map<String,Object> row(ResultSet r)throws SQLException{var out=new HashMap<String,Object>();var meta=r.getMetaData();for(int i=1;i<=meta.getColumnCount();i++)out.put(meta.getColumnLabel(i).toLowerCase(Locale.ROOT),r.getObject(i));return out;}
 public static void main(String[] args)throws Exception{
  String mode=args.length==0?"--verify":args[0];if(!Set.of("--verify","--apply-schema","--apply-frame-rates","--apply-frame-statistics","--apply-device-settings","--apply-execution-clock","--migrate-files").contains(mode))throw new IllegalArgumentException("Expected a supported maintenance mode");
  Map<?,?> config=new Yaml().load(Files.readString(Path.of("src/main/resources/application.yml")));var spring=(Map<?,?>)config.get("spring");var ds=(Map<?,?>)spring.get("datasource");var sop=(Map<?,?>)config.get("sop");var storage=(Map<?,?>)sop.get("storage");
  String url=resolve(ds.get("url"),"");if(!url.matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1)(:[0-9]+)?/sop_dev(\\?.*)?"))throw new IllegalStateException("Maintenance is restricted to local sop_dev; no changes made");
  String root=resolve(storage.get("data-root"),"./sop-data"),legacy=resolve(storage.get("directory"),"./data/sop-files");var files=new SopImageStorage(root,legacy);
  try(var db=DriverManager.getConnection(url,resolve(ds.get("username"),""),resolve(ds.get("password"),""))){
   if (Set.of("--apply-frame-rates", "--apply-frame-statistics").contains(mode)) {
    try (var tables = db.getMetaData().getTables(db.getCatalog(), null, "sop_camera_config", new String[]{"TABLE"})) {
     if (tables.next()) throw new IllegalStateException("Camera configuration already migrated; do not recreate the legacy frame-rate table");
    }
   }
   if (mode.equals("--apply-device-settings")) {
    for (String migration : List.of("resource-module-v1/migrations/006_camera_config.sql", "common-module-v1/migrations/002_system_settings.sql")) {
     String sql = Files.readString(Path.of("../../SOP_MySQL/" + migration));
     for (String part : sql.split(";")) {
      if (!part.isBlank()) try (var statement = db.createStatement()) { statement.execute(part); }
     }
    }
    Path backup = Path.of("../../logs/sop-legacy-frame-settings-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")) + ".csv");
    int copied = org.jsut.common.storage.CameraConfigurationMigration.migrate(db, backup);
    System.out.println("Camera and system configuration ready. Legacy rows verified and migrated: " + copied);
    if (copied > 0) System.out.println("Legacy configuration backup: " + backup.toAbsolutePath().normalize());
    try (var statement = db.createStatement(); var result = statement.executeQuery("SELECT COUNT(*) FROM sop_camera_config WHERE device_id IS NULL")) {
     result.next();
     if (result.getInt(1) != 1) throw new IllegalStateException("Expected exactly one camera defaults row");
     System.out.println("Camera defaults rows verified: 1");
    }
    try (var statement = db.createStatement(); var result = statement.executeQuery("SELECT COUNT(*) FROM sop_system_setting")) {
     result.next();
     System.out.println("SOP system parameter rows: " + result.getInt(1));
    }
    return;
   }
   if (mode.equals("--apply-frame-statistics")) {
    String sql = Files.readString(Path.of("../../SOP_MySQL/resource-module-v1/migrations/005_frame_statistics.sql"));
    for (String part : sql.split(";")) {
     if (part.isBlank()) continue;
     var column = java.util.regex.Pattern.compile("ADD COLUMN ([a-z_]+)").matcher(part);
     if (!column.find()) throw new IllegalStateException("Unexpected statistics migration");
     try (var query = db.prepareStatement("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sop_frame_rate_setting' AND column_name=?")) {
      query.setString(1, column.group(1));
      try (var result = query.executeQuery()) {
       result.next();
       if (result.getInt(1) == 0) {
        try (var statement = db.createStatement()) { statement.execute(part); }
       }
      }
     }
    }
    System.out.println("Frame statistics columns ready. Existing frame rates and device overrides unchanged.");
    return;
   }
   if(mode.equals("--apply-execution-clock")){
    String sql=Files.readString(Path.of("../../SOP_MySQL/execution-module-v1/migrations/002_execution_clock.sql"));
    for(String part:sql.split(";")){if(!part.isBlank())try(var statement=db.createStatement()){statement.execute(part);}}
    System.out.println("Execution heartbeat clock schema ready. Existing execution rows and photos unchanged.");return;
   }
   if(mode.equals("--apply-frame-rates")){
    String sql=Files.readString(Path.of("../../SOP_MySQL/resource-module-v1/migrations/004_frame_rates.sql"));
    for(String part:sql.split(";")){if(!part.isBlank())try(var statement=db.createStatement()){statement.execute(part);}}
    System.out.println("Frame rate settings schema ready. Existing camera and execution data unchanged.");return;
   }
   if(mode.equals("--apply-schema")){
    String sql=Files.readString(Path.of("../../SOP_MySQL/execution-module-v1/migrations/001_evidence_storage.sql")).replaceAll("(?m)^--.*$","");
    for(String part:sql.split(";")){if(part.isBlank())continue;try(var statement=db.createStatement()){statement.execute(part);}}
    Files.createDirectories(Path.of(root));System.out.println("Evidence schema and configured data root ready. Existing rows/photos unchanged.");return;
   }
   var rows=new ArrayList<Map<String,Object>>();try(var statement=db.createStatement();var r=statement.executeQuery("SELECT * FROM sop_file WHERE deleted_at IS NULL")){while(r.next())rows.add(row(r));}
   int verified=0;for(var file:rows){files.read(file);verified++;}
   try(var statement=db.createStatement();var r=statement.executeQuery("SELECT * FROM sop_execution_evidence")){while(r.next()){files.read(row(r));verified++;}}
   System.out.println("Verified "+verified+" stored image objects, including legacy paths.");
   if(mode.equals("--verify"))return;
   var references=new LinkedHashMap<Long,Map<String,Object>>();
   String query="SELECT f.*,d.id AS sop_id,d.managing_project_id AS project_id,r.revision_no,s.id AS step_id,c.task_id,t.subproject_id FROM sop_file f JOIN sop_step_attachment a ON a.file_id=f.id JOIN sop_step s ON s.id=a.step_id JOIN sop_revision r ON r.id=s.revision_id JOIN sop_document d ON d.id=r.sop_id LEFT JOIN sop_task_content c ON c.sop_id=d.id LEFT JOIN sop_task t ON t.id=c.task_id WHERE f.deleted_at IS NULL ORDER BY f.id,s.id";
   try(var statement=db.createStatement();var r=statement.executeQuery(query)){while(r.next()){var item=row(r);references.putIfAbsent(((Number)item.get("id")).longValue(),item);}}
   int migrated=0;var paths=new StoragePaths();
   for(var file:references.values()){
    String previous=(String)file.get("storage_key");if(previous.startsWith("projects/"))continue;
    Long task=file.get("task_id")==null?null:((Number)file.get("task_id")).longValue(),sub=file.get("subproject_id")==null?null:((Number)file.get("subproject_id")).longValue();
    String folder=paths.annotation(((Number)file.get("project_id")).longValue(),sub,task,((Number)file.get("sop_id")).longValue(),((Number)file.get("revision_no")).intValue());
    var stored=files.save(new Photo((String)file.get("original_name"),files.read(file)),folder,"step-"+file.get("step_id")+"_");
    try(var update=db.prepareStatement("UPDATE sop_file SET storage_key=? WHERE id=? AND storage_key=?")){update.setString(1,stored.key());update.setObject(2,file.get("id"));update.setString(3,previous);if(update.executeUpdate()!=1){files.rollbackDelete(stored.key());throw new IllegalStateException("Concurrent file update; stop and retry after verification");}migrated++;}
    // On an ambiguous database failure retain the copied object. Never risk deleting a committed reference.
   }
   System.out.println("Migrated "+migrated+" referenced image objects. Legacy originals retained; unreferenced files are untouched.");
  }
 }
 record Photo(String name,byte[] bytes) implements MultipartFile {
  public String getName(){return "file";}public String getOriginalFilename(){return name;}public String getContentType(){return "application/octet-stream";}public boolean isEmpty(){return bytes.length==0;}public long getSize(){return bytes.length;}public byte[] getBytes(){return bytes;}public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}public void transferTo(File destination)throws IOException{Files.write(destination.toPath(),bytes,StandardOpenOption.CREATE_NEW);}
 }
}
