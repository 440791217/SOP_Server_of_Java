package org.jsut.modules.resource.service;
import org.jsut.modules.resource.mapper.ResourceMapper;
import org.jsut.modules.resource.dto.ResourceRequests.Task;
import org.jsut.modules.project.mapper.ProjectMapper;
import org.jsut.modules.sop.mapper.SopMapper;
import org.jsut.modules.sop.service.SopService;
import org.jsut.modules.user.mapper.AuthMapper;
import org.jsut.modules.user.service.AuthService;
import org.jsut.modules.user.dto.UserInfo;
import org.jsut.common.BusinessCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.dao.DuplicateKeyException;
import java.util.*;
import static org.jsut.modules.resource.service.ResourceSupport.*;
@Service @Transactional(isolation=Isolation.READ_COMMITTED)
public class TaskService {
 private final org.jsut.modules.project.service.RootDeletionService deletion;
 private final ResourceMapper db;private final ProjectMapper projects;private final SopMapper sops;private final SopService content;private final AuthService auth;private final AuthMapper users;
 public TaskService(ResourceMapper db,ProjectMapper projects,SopMapper sops,SopService content,AuthService auth,AuthMapper users,org.jsut.modules.project.service.RootDeletionService deletion){this.deletion=deletion;this.db=db;this.projects=projects;this.sops=sops;this.content=content;this.auth=auth;this.users=users;}
 private UserInfo actor(String token){var a=auth.authenticate(token);if(a.must_change_password())throw denied();return a;}
 private boolean owner(UserInfo a,Map<String,Object> p){return a.is_superuser()||uid(a)==n(p,"owner_user_id");}
 private void read(UserInfo a,Map<String,Object> p){if(!owner(a,p)&&(!has(a,"project:read")||projects.member(n(p,"id"),uid(a))==null))throw denied();}
 private boolean manage(UserInfo a,Map<String,Object> p,Map<String,Object> s){return n(p,"status")<2&&n(s,"status")<2&&(owner(a,p)||has(a,"task:manage")&&has(a,"project:read")&&Integer.valueOf(1).equals(projects.member(n(p,"id"),uid(a))));}
 private Map<String,Object> project(long pid){var p=projects.project(pid);if(p==null)throw missing();return p;}
 private Map<String,Object> sub(long pid,long sid){var s=projects.sub(pid,sid);if(s==null)throw missing();return s;}
 @Transactional(readOnly=true) public List<Map<String,Object>> owners(String token,long pid,long sid,String keyword){var a=actor(token);var p=project(pid);var s=sub(pid,sid);if(!manage(a,p,s))throw denied();if(keyword!=null&&keyword.length()>128)throw bad("搜索内容过长");var q=new HashMap<String,Object>();q.put("projectId",pid);q.put("keyword",keyword==null||keyword.isBlank()?null:keyword.trim());return projects.users(q).stream().map(ResourceSupport::view).toList();}
 @Transactional(readOnly=true) public Map<String,Object> list(String token,long pid,long sid){var a=actor(token);var p=project(pid);var s=sub(pid,sid);read(a,p);return Map.of("items",db.tasks(sid).stream().map(t->taskView(t)).toList(),"can_manage",manage(a,p,s),"can_delete",a.is_superuser());}
 @Transactional(readOnly=true) public Map<String,Object> stationTasks(String token,long stationId){
  var a=actor(token);if(!has(a,"resource:read"))throw denied();if(db.get(q("station",stationId))==null)throw missing();
  var items=new ArrayList<Map<String,Object>>();
  for(var t:db.stationTasks(stationId)){var p=project(n(t,"project_id"));if(!owner(a,p)&&(!has(a,"project:read")||projects.member(n(p,"id"),uid(a))==null))continue;
   var out=taskView(t);out.put("can_manage",manage(a,p,sub(n(p,"id"),n(t,"subproject_id"))));var reasons=new ArrayList<String>(issues(t));
   if(n(t,"status")==2)reasons.add("任务已取消");
   if(n(t,"project_status")>=2)reasons.add("项目已完成或归档");
   if(n(t,"subproject_status")>=2)reasons.add("子项目已完成或归档");
   out.put("onsite_unavailable_reasons",reasons);out.put("onsite_available",reasons.isEmpty());items.add(out);}
  return Map.of("items",items);
 }
 public void delete(String token,long pid,long sid,long id,int selectedVersion){
  var initial=actor(token);if(!initial.is_superuser())throw denied();users.lockById(uid(initial));auth.requireCurrentSession(token,uid(initial));var a=actor(token);if(!a.is_superuser())throw denied();
  if(projects.lockProject(pid)==null)throw missing();sub(pid,sid);var task=db.task(id);if(task==null||n(task,"subproject_id")!=sid)throw missing();version(task,selectedVersion);
  deletion.remove(a,pid,sid,id,(String)task.get("name"));
 }
 private Map<String,Object> taskView(Map<String,Object> t){var out=view(t);out.put("status",n(t,"status")==2?2:t.get("revision_id")==null?0:1);out.put("issues",issues(t));return out;}
 private List<String> issues(Map<String,Object> task){
  var issues=new ArrayList<String>();Long station=task.get("station_id")==null?null:n(task,"station_id");Long rid=task.get("revision_id")==null?null:n(task,"revision_id");
  if(station==null)issues.add("未绑定工位");else {var s=db.get(q("station",station));if(s==null||n(s,"status")!=0)issues.add("工位不可用");else if(s.get("location_id")==null||s.get("ip_address")==null)issues.add("工位尚未补齐位置或IP");}
  if(rid==null)issues.add("任务内容尚未发布");else {var r=sops.revision(n(task,"sop_id"),rid);if(r==null||n(r,"status")!=1)issues.add("已发布内容不可用");
   if(station!=null){var devices=db.devices(station);for(var step:sops.steps(rid)){for(var req:db.requirements(n(step,"id"))){long count=devices.stream().filter(d->n(d,"model_id")==n(req,"model_id")&&n(d,"status")==0).count();if(count<n(req,"quantity"))issues.add("步骤「"+step.get("title")+"」缺少："+req.get("name")+" × "+req.get("quantity"));}}}
  }return issues;
 }
 public Map<String,Object> save(String token,long pid,long sid,Long id,Task r){
  boolean cancelled=Integer.valueOf(2).equals(r.status());
  if(id==null&&cancelled)throw bad("新任务不能直接取消");
  var initial=actor(token);var selected=new TreeSet<Long>(List.of(uid(initial),r.owner_user_id()));for(long u:selected){var user=users.lockById(u);if(user==null||user.deletedAt()!=null||!Integer.valueOf(1).equals(user.status()))throw bad("负责人不存在或已停用");}auth.requireCurrentSession(token,uid(initial));var a=actor(token);
  if((r.sop_id()==null)!=(r.revision_id()==null))throw bad("SOP和版本必须一起选择");
  var before=id==null?null:db.task(id);if(id!=null&&(before==null||n(before,"subproject_id")!=sid))throw missing();
  boolean changedStandard=before==null?r.sop_id()!=null:!Objects.equals(before.get("revision_id")==null?null:n(before,"revision_id"),r.revision_id())||!Objects.equals(before.get("sop_id")==null?null:n(before,"sop_id"),r.sop_id());
  Long source=null;if(r.sop_id()!=null){var doc=sops.document(r.sop_id());if(doc==null)throw missing();source=n(doc,"managing_project_id");if(changedStandard)content.detail(token,r.sop_id());}
  var pids=new TreeSet<Long>();pids.add(pid);if(source!=null)pids.add(source);for(long p:pids)if(projects.lockProject(p)==null)throw missing();
  var p=project(pid);var s=sub(pid,sid);read(a,p);if(!manage(a,p,s))throw denied();
  if(!cancelled&&r.station_id()==null)throw bad("请选择工位；历史任务需补齐工位后保存");
  if(!Integer.valueOf(1).equals(projects.member(pid,r.owner_user_id())))throw bad("负责人必须是项目有效编辑成员");
  if(id!=null){before=db.task(id);version(before,r.version());if(n(before,"status")==2)throw conflict("已取消任务只读，请另建任务");}
  if(!cancelled&&r.station_id()!=null){if(!has(a,"resource:read")&&changedStation(before,r.station_id()))throw denied();var station=db.lock(q("station",r.station_id()));if(station==null||n(station,"status")!=0)throw bad("工位不可用");}
  if(!cancelled&&r.sop_id()!=null){var doc=sops.lockDocument(r.sop_id());if(doc==null||n(doc,"managing_project_id")!=source)throw conflict("内容归属已变化，请刷新");var rev=sops.revision(r.sop_id(),r.revision_id());if(rev==null||n(rev,"status")!=1)throw bad("请选择已发布且未停用的SOP版本");if(changedStandard)content.detail(token,r.sop_id());}
  var d=new HashMap<String,Object>();d.put("id",id);d.put("sid",sid);d.put("name",r.name().trim());if(r.name().isBlank())throw bad("任务名称不能为空");d.put("station_id",r.station_id());d.put("owner_user_id",r.owner_user_id());d.put("sort_order",r.sort_order());d.put("status",cancelled?2:r.revision_id()==null?0:1);d.put("description",r.description());d.put("version",r.version());d.put("actor",uid(a));d.put("sop_id",r.sop_id());d.put("revision_id",r.revision_id());
  if(!cancelled&&r.revision_id()!=null){var issues=issues(d);if(!issues.isEmpty())throw conflict("任务发布内容无法用于当前工位："+String.join("；",issues));}
  if(id==null){d.put("code",BusinessCode.validate(r.code(),"TSK"));try{db.insertTask(d);}catch(DuplicateKeyException e){throw conflict("任务编号已存在，请确认是否已创建成功");}}
  else {if(r.code()!=null&&!r.code().equals(before.get("code")))throw bad("任务编号不可修改");changed(db.updateTask(d));}
  if(cancelled&&changedStandard)throw bad("取消任务不能同时切换标准");
  if(changedStandard){db.deactivate(n(d,"id"));if(r.sop_id()!=null)db.insertStandard(d);}
  return taskView(db.task(n(d,"id")));
 }
 public Map<String,Object> editContent(String token,long pid,long sid,long id,org.jsut.modules.resource.dto.ResourceRequests.TaskContent request){
  var initial=actor(token);var p=project(pid);var subproject=sub(pid,sid);read(initial,p);if(!manage(initial,p,subproject))throw denied();
  var task=db.task(id);if(task==null||n(task,"subproject_id")!=sid)throw missing();
  var owned=db.taskContent(id);var document=owned==null?null:sops.document(n(owned,"sop_id"));
  var actors=new TreeSet<Long>(List.of(uid(initial),n(task,"owner_user_id")));if(document!=null)actors.add(n(document,"owner_user_id"));
  for(long userId:actors){var user=users.lockById(userId);if(user==null||user.deletedAt()!=null||!Integer.valueOf(1).equals(user.status()))throw bad("关联用户已停用，请刷新");}
  auth.requireCurrentSession(token,uid(initial));if(projects.lockProject(pid)==null)throw missing();var a=actor(token);p=project(pid);subproject=sub(pid,sid);if(!manage(a,p,subproject))throw denied();
  task=db.task(id);if(task==null||n(task,"subproject_id")!=sid)throw missing();version(task,request.version());if(n(task,"status")==2)throw conflict("已取消任务只读");if(task.get("station_id")==null)throw bad("请先绑定工位");
  owned=db.taskContent(id);long sopId;Map<String,Object> draft;boolean created;
  if(owned==null){
   var result=content.createTaskContent(token,new org.jsut.modules.sop.dto.SopRequests.Create(request.code(),(String)task.get("name"),pid,n(task,"owner_user_id")),task.get("sop_id")==null?null:n(task,"sop_id"),task.get("revision_id")==null?null:n(task,"revision_id"));
   sopId=Long.parseLong(result.get("id").toString());if(!Boolean.TRUE.equals(content.detail(token,sopId).get("can_edit")))throw denied();draft=(Map<String,Object>)result.get("draft");db.insertTaskContent(Map.of("id",id,"sopId",sopId,"actor",uid(a)));created=true;
  }else{
   sopId=n(owned,"sop_id");var doc=sops.document(sopId);if(doc==null||n(doc,"managing_project_id")!=pid)throw conflict("任务内容归属异常，请核对");
   if(document==null||n(doc,"owner_user_id")!=n(document,"owner_user_id"))throw conflict("任务内容负责人已变化，请刷新");
   content.syncTaskIdentity(token,sopId,(String)task.get("name"),n(task,"owner_user_id"));
   var result=content.taskDraft(token,sopId);draft=(Map<String,Object>)result.get("revision");created=Boolean.TRUE.equals(result.get("created"));
  }
  if(created)changed(db.bumpTaskContent(Map.of("id",id,"version",request.version(),"actor",uid(a))));
  return Map.of("sop_id",Long.toString(sopId),"revision_id",draft.get("id"),"task",taskView(db.task(id)));
 }
 public Map<String,Object> publishAndUse(String token,long pid,long sid,long id,org.jsut.modules.resource.dto.ResourceRequests.PublishTask request){
  var a=actor(token);var p=project(pid);var subproject=sub(pid,sid);read(a,p);if(!manage(a,p,subproject))throw denied();
  var task=db.task(id);if(task==null||n(task,"subproject_id")!=sid)throw missing();version(task,request.task_version());if(n(task,"status")==2)throw conflict("已取消任务只读");
  var owned=db.taskContent(id);if(owned!=null&&n(owned,"sop_id")!=request.sop_id())throw bad("只能发布当前任务自己的内容");
  var document=sops.document(request.sop_id());if(document==null)throw missing();
  var actors=new TreeSet<Long>(List.of(uid(a),n(task,"owner_user_id"),n(document,"owner_user_id")));
  for(long userId:actors){var user=users.lockById(userId);if(user==null||user.deletedAt()!=null||!Integer.valueOf(1).equals(user.status()))throw bad("关联用户已停用，请刷新");}
  auth.requireCurrentSession(token,uid(a));
  var projectIds=new TreeSet<Long>(List.of(pid,n(document,"managing_project_id")));for(long projectId:projectIds)if(projects.lockProject(projectId)==null)throw missing();
  var latest=sops.document(request.sop_id());if(latest==null||n(latest,"managing_project_id")!=n(document,"managing_project_id")||n(latest,"owner_user_id")!=n(document,"owner_user_id"))throw conflict("内容归属已变化，请刷新");
  task=db.task(id);if(task==null||n(task,"subproject_id")!=sid)throw missing();version(task,request.task_version());
  var revision=content.publish(token,request.sop_id(),request.revision_id(),new org.jsut.modules.sop.dto.SopRequests.Version(request.revision_version()));
  var updated=save(token,pid,sid,id,new Task((String)task.get("code"),(String)task.get("name"),task.get("station_id")==null?null:n(task,"station_id"),n(task,"owner_user_id"),(int)n(task,"sort_order"),1,(String)task.get("description"),request.sop_id(),request.revision_id(),request.task_version()));
  return Map.of("task",updated,"revision",revision);
 }
 private boolean changedStation(Map<String,Object> before,Long id){return before==null||!Objects.equals(before.get("station_id")==null?null:n(before,"station_id"),id);}
 @Transactional(readOnly=true) public Map<String,Object> preview(String token,long pid,long sid,long id){var a=actor(token);var p=project(pid);sub(pid,sid);read(a,p);var t=db.task(id);if(t==null||n(t,"subproject_id")!=sid||t.get("revision_id")==null)throw missing();return content.taskRevision(n(t,"sop_id"),n(t,"revision_id"));}
 @Transactional(readOnly=true) public Map<String,Object> image(String token,long pid,long sid,long id,long fid){preview(token,pid,sid,id);var t=db.task(id);return content.taskImage(n(t,"revision_id"),fid);}
}
