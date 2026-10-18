package org.jsut.modules.execution.service;
import org.jsut.modules.execution.mapper.ExecutionMapper;
import org.jsut.modules.execution.dto.ExecutionRequests.*;
import org.jsut.modules.resource.mapper.ResourceMapper;
import org.jsut.modules.resource.service.TaskService;
import org.jsut.modules.project.mapper.ProjectMapper;
import org.jsut.modules.sop.service.SopService;
import org.jsut.modules.user.mapper.AuthMapper;
import org.jsut.modules.user.service.AuthService;
import org.jsut.modules.user.dto.UserInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.dao.DuplicateKeyException;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.*;
import static org.jsut.modules.resource.service.ResourceSupport.*;
@Service @Transactional(isolation=Isolation.READ_COMMITTED)
public class ExecutionService {
 private final ExecutionMapper db;private final ResourceMapper resources;private final ProjectMapper projects;private final TaskService tasks;private final SopService content;private final AuthService auth;private final AuthMapper users;private final ObjectMapper json;
 public ExecutionService(ExecutionMapper db,ResourceMapper resources,ProjectMapper projects,TaskService tasks,SopService content,AuthService auth,AuthMapper users,ObjectMapper json){this.db=db;this.resources=resources;this.projects=projects;this.tasks=tasks;this.content=content;this.auth=auth;this.users=users;this.json=json;}
 private UserInfo actor(String token){var a=auth.authenticate(token);if(a.must_change_password()||!has(a,"resource:read"))throw denied();return a;}
 private void read(UserInfo a,long pid){var p=db.project(pid);if(p==null)throw missing();if(!a.is_superuser()&&n(p,"owner_user_id")!=uid(a)&&(!has(a,"project:read")||projects.member(pid,uid(a))==null))throw denied();}
 private Map<String,Object> record(UserInfo a,long id){var e=db.get(id);if(e==null)throw missing();read(a,n(e,"project_id"));return e;}
 @SuppressWarnings("unchecked") private Map<String,Object> snapshot(Map<String,Object> e){return json.readValue((String)e.get("content_snapshot"),Map.class);}
 @SuppressWarnings("unchecked") private List<Map<String,Object>> snapshotSteps(Map<String,Object> e){return (List<Map<String,Object>>)snapshot(e).get("steps");}
 private Map<String,Object> detailView(UserInfo a,Map<String,Object> e){var out=view(e);out.remove("content_snapshot");out.remove("request_key");out.put("content",snapshot(e));out.put("steps",db.steps(n(e,"id")).stream().map(org.jsut.modules.resource.service.ResourceSupport::view).toList());out.put("events",db.events(n(e,"id")).stream().map(org.jsut.modules.resource.service.ResourceSupport::view).toList());out.put("can_abort",n(e,"status")<2&&(a.is_superuser()||has(a,"resource:use")&&uid(a)==n(e,"operator_id")));out.put("can_operate",has(a,"resource:use")&&uid(a)==n(e,"operator_id")&&n(e,"status")<2);return out;}
 @Transactional(readOnly=true) public Map<String,Object> detail(String token,long id){var a=actor(token);return detailView(a,record(a,id));}
 @Transactional(readOnly=true) public Map<String,Object> list(String token,int page,int limit,Long station,Long project,Long operator,String product,Integer status,boolean active,LocalDate from,LocalDate to,String projectName,String stationName,String operatorName){var a=actor(token);if(page<1||limit<1||limit>100||status!=null&&(status<0||status>3)||product!=null&&product.length()>128||from!=null&&to!=null&&from.isAfter(to))throw bad("查询参数不正确");var q=new HashMap<String,Object>();for(var value:new String[]{projectName,stationName,operatorName})if(value!=null&&value.length()>128)throw bad("搜索内容过长");q.put("projectName",projectName==null||projectName.isBlank()?null:projectName.trim());q.put("stationName",stationName==null||stationName.isBlank()?null:stationName.trim());q.put("operatorName",operatorName==null||operatorName.isBlank()?null:operatorName.trim());q.put("uid",uid(a));q.put("superuser",a.is_superuser());q.put("projectRead",has(a,"project:read"));q.put("station",station);q.put("project",project);q.put("operator",operator);q.put("product",product==null||product.isBlank()?null:product.trim());q.put("status",status);q.put("active",active);q.put("from",from==null?null:from.atStartOfDay());q.put("until",to==null?null:to.plusDays(1).atStartOfDay());q.put("limit",limit);q.put("offset",(long)(page-1)*limit);return Map.of("items",db.list(q).stream().map(org.jsut.modules.resource.service.ResourceSupport::view).toList(),"total",db.count(q),"can_start",has(a,"resource:use"));}
 public Map<String,Object> start(String token,Start r){
  var initial=actor(token);if(!has(initial,"resource:use"))throw denied();users.lockById(uid(initial));auth.requireCurrentSession(token,uid(initial));var a=actor(token);if(!has(a,"resource:use"))throw denied();
  var prior=db.request(uid(a),r.request_key());if(prior!=null){if(n(prior,"task_id")!=r.task_id()||n(prior,"station_id")!=r.station_id()||!prior.get("product_code").equals(r.product_code().trim()))throw conflict("开工请求编号已用于其他作业");read(a,n(prior,"project_id"));return detailView(a,prior);}
  if(projects.lockProject(r.project_id())==null)throw missing();read(a,r.project_id());var sub=projects.sub(r.project_id(),r.subproject_id());if(sub==null)throw missing();
  var station=resources.lock(q("station",r.station_id()));if(station==null)throw missing();
  var task=resources.task(r.task_id());if(task==null||n(task,"subproject_id")!=r.subproject_id())throw missing();
  if(n(task,"version")!=r.task_version()||task.get("revision_id")==null||n(task,"revision_id")!=r.revision_id())throw conflict("任务配置或发布版本已更新，请刷新后重新开工");
  if(task.get("station_id")==null||n(task,"station_id")!=r.station_id())throw conflict("任务已不属于当前工位");
  @SuppressWarnings("unchecked") var available=(List<Map<String,Object>>)tasks.stationTasks(token,r.station_id()).get("items");
  var selected=available.stream().filter(t->Long.toString(r.task_id()).equals(t.get("id"))).findFirst().orElseThrow(()->denied());
  if(!Boolean.TRUE.equals(selected.get("onsite_available")))throw conflict("当前任务不能开工："+selected.get("onsite_unavailable_reasons"));
  String product=r.product_code().trim();if(product.isEmpty()||product.codePoints().anyMatch(Character::isISOControl))throw bad("产品码不能为空或包含控制字符");
  var revision=content.taskRevision(n(task,"sop_id"),n(task,"revision_id"));
  @SuppressWarnings("unchecked") var steps=(List<Map<String,Object>>)revision.get("steps");if(steps==null||steps.isEmpty())throw conflict("任务没有可执行的发布步骤");
  var project=projects.project(r.project_id());var d=new HashMap<String,Object>();d.put("request_key",r.request_key());d.put("task_id",r.task_id());d.put("project_id",r.project_id());d.put("subproject_id",r.subproject_id());d.put("station_id",r.station_id());d.put("sop_id",task.get("sop_id"));d.put("revision_id",r.revision_id());d.put("operator_id",uid(a));d.put("product_code",product);d.put("task_name",task.get("name"));d.put("project_name",project.get("name"));d.put("subproject_name",sub.get("name"));d.put("station_name",station.get("name"));d.put("operator_name",a.real_name()==null?a.username():a.real_name());d.put("revision_no",revision.get("revision_no"));d.put("content_snapshot",json.writeValueAsString(revision));
  try{db.insert(d);}catch(DuplicateKeyException e){throw conflict("此工位已有未结束作业，或该产品码已在本任务下开工；请先查询执行记录");}
  int position=0;for(var step:steps){var x=new HashMap<String,Object>();x.put("id",d.get("id"));x.put("position",++position);x.put("step_id",step.get("id"));db.insertStep(x);}
  event(n(d,"id"),"start",null,uid(a));return detailView(a,db.get(n(d,"id")));
 }
 public Map<String,Object> action(String token,long id,String action,Integer position,Action r){
  var initial=actor(token);if(!has(initial,"resource:use"))throw denied();users.lockById(uid(initial));auth.requireCurrentSession(token,uid(initial));var a=actor(token);if(!has(a,"resource:use"))throw denied();var before=record(a,id);
  // Lock in the same order as task configuration: actor, project, station, execution.
  projects.lockProject(n(before,"project_id"));resources.lock(q("station",n(before,"station_id")));var e=db.lock(id);read(a,n(e,"project_id"));
  if(uid(a)!=n(e,"operator_id")&&!(a.is_superuser()&&action.equals("abort")))throw denied();version(e,r.version());int status=(int)n(e,"status");if(status>=2)throw conflict("作业已结束，不能继续操作");
  String reason=r.reason()==null?null:r.reason().trim();
  if(action.equals("pause")||action.equals("abort")){if(reason==null||reason.isEmpty())throw bad("请填写异常或中止原因");}
  if(action.equals("confirm")){
   if(status!=0)throw conflict("请先恢复作业");var pending=db.steps(id).stream().filter(s->s.get("confirmed_at")==null).findFirst().orElseThrow(()->conflict("所有步骤已确认，请完成作业"));
   if(position==null||n(pending,"position")!=position)throw conflict("请按顺序确认当前未完成步骤");
   var x=new HashMap<String,Object>();x.put("id",id);x.put("position",position);x.put("actor",uid(a));changed(db.confirm(x));
  }else if(action.equals("pause")){if(status!=0)throw conflict("只有进行中的作业可以暂停");status=1;
  }else if(action.equals("resume")){if(status!=1)throw conflict("只有暂停的作业可以恢复");status=0;
  }else if(action.equals("complete")){if(status!=0||db.steps(id).stream().anyMatch(s->s.get("confirmed_at")==null))throw conflict("请先恢复作业并确认全部步骤");status=2;
  }else if(action.equals("abort")){status=3;}else throw bad("不支持的作业操作");
  var d=new HashMap<String,Object>();d.put("id",id);d.put("status",status);d.put("active_station_id",status<2?e.get("station_id"):null);d.put("ended",status>=2);d.put("version",r.version());changed(db.update(d));event(id,action,action.equals("confirm")?"步骤 "+position:reason,uid(a));return detailView(a,db.get(id));
 }
 private void event(long id,String action,String reason,long actor){var q=new HashMap<String,Object>();q.put("id",id);q.put("action",action);q.put("reason",reason);q.put("actor",actor);db.event(q);}
 @Transactional(readOnly=true) public Map<String,Object> image(String token,long id,long fid){var a=actor(token);var e=record(a,id);boolean allowed=false;for(var s:snapshotSteps(e)){Object attachments=s.get("attachments");if(attachments instanceof List<?> list)for(var item:list)if(item instanceof Map<?,?> m&&Long.toString(fid).equals(String.valueOf(m.get("file_id"))))allowed=true;}if(!allowed)throw missing();return content.taskImage(n(e,"revision_id"),fid);}
}
