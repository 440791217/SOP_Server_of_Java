package org.jsut.modules.sop.service;

import org.jsut.common.BusinessException;
import org.jsut.common.BusinessCode;
import org.jsut.modules.sop.dto.SopRequests.*;
import org.jsut.modules.sop.mapper.SopMapper;
import org.jsut.modules.project.mapper.ProjectMapper;
import org.jsut.modules.user.dto.UserInfo;
import org.jsut.modules.user.entity.UserCredentials;
import org.jsut.modules.user.mapper.AuthMapper;
import org.jsut.modules.user.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class SopService {
    private final SopMapper db;
    private final ProjectMapper projects;
    private final AuthMapper users;
    private final AuthService auth;
    private final Clock clock;
    private final SopImageStorage images;
    private final SopVisualService visuals;
    private final org.jsut.modules.resource.service.StepResourceService resources;
    private final org.jsut.modules.resource.mapper.ResourceMapper resourceDb;
    public SopService(SopMapper db,ProjectMapper projects,AuthMapper users,AuthService auth,Clock clock,SopImageStorage images,SopVisualService visuals,org.jsut.modules.resource.service.StepResourceService resources,org.jsut.modules.resource.mapper.ResourceMapper resourceDb){this.resources=resources;this.resourceDb=resourceDb;this.visuals=visuals;this.db=db;this.projects=projects;this.users=users;this.auth=auth;this.clock=clock;this.images=images;}

    @Transactional(readOnly=true)
    public Map<String,Object> list(String token,int page,int limit,String keyword,Long projectId){
        var a=actor(token);var q=page(page,limit);q.put("actor",uid(a));q.put("root",a.is_superuser());
        q.put("includeMembers",has(a,"sop:list")&&has(a,"sop:read")&&has(a,"project:read"));
        if(projectId!=null)positive(projectId);q.put("projectId",projectId);q.put("keyword",text(keyword,128,false));
        return Map.of("items",db.list(q).stream().map(d->documentView(d,project(num(d,"managing_project_id"),false),a)).toList(),"total",db.count(q));
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> projectOptions(String token,String keyword){
        var a=actor(token);var q=new HashMap<String,Object>();q.put("actor",uid(a));q.put("root",a.is_superuser());
        q.put("includeMembers",has(a,"sop:create")&&has(a,"project:read"));q.put("keyword",text(keyword,128,false));return db.projectOptions(q).stream().map(this::view).toList();
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> ownerOptions(String token,long pid,String keyword){
        var a=actor(token);var p=project(pid,false);if(!edit(a,p,"sop:create")&&!manage(a,p))throw denied();
        var q=new HashMap<String,Object>();q.put("projectId",pid);q.put("keyword",text(keyword,128,false));return projects.users(q).stream().map(this::view).toList();
    }
    @Transactional(readOnly=true)
    public Map<String,Object> detail(String token,long id){var a=actor(token);var d=document(id);var p=project(num(d,"managing_project_id"),false);read(a,p);return documentView(d,p,a);}
    @Transactional(readOnly=true)
    public List<Map<String,Object>> revisions(String token,long id){var a=actor(token);var d=document(id);read(a,project(num(d,"managing_project_id"),false));return db.revisions(id).stream().map(this::view).toList();}
    @Transactional(readOnly=true)
    public Map<String,Object> revisionDetail(String token,long id,long rid){var a=actor(token);var d=document(id);read(a,project(num(d,"managing_project_id"),false));return revisionView(revision(id,rid));}

    public Map<String,Object> create(String token,Create r){
        var initial=actor(token);var locked=lockUsers(initial,r.owner_user_id());auth.requireCurrentSession(token,uid(initial));var a=actor(token);
        var p=project(r.managing_project_id(),true);open(p);if(!edit(a,p,"sop:create"))throw denied();validOwner(r.managing_project_id(),r.owner_user_id(),locked);
        var d=mutation(0,0,a);d.put("code",BusinessCode.validate(r.code(),"SOP"));d.put("name",text(r.name(),128,true));d.put("projectId",r.managing_project_id());d.put("owner",r.owner_user_id());try{db.insertDocument(d);}catch(org.springframework.dao.DuplicateKeyException e){throw conflict("编号已存在，请先确认是否已创建成功；如需另建，请重新打开新建窗口");}long id=num(d,"id");
        var v=revisionData(id,1,r.name(),null,null,null,null,a);db.insertRevision(v);
        var result=documentView(document(id),p,a);result.put("draft",revisionView(revision(id,num(v,"id"))));return result;
    }
    public Map<String,Object> editDocument(String token,long id,DocumentEdit r){
        var c=write(token,id,null);open(c.project);requireEdit(c,"sop:update");version(c.document,r.version());
        var d=mutation(id,r.version(),c.actor);d.put("name",text(r.name(),128,true));changed(db.editDocument(d));return documentView(document(id),c.project,c.actor);
    }
    public void owner(String token,long id,Owner r){
        var c=write(token,id,r.owner_user_id());if(!manage(c.actor,c.project))throw denied();version(c.document,r.version());
        if(num(c.document,"owner_user_id")==r.owner_user_id())throw conflict("该用户已是SOP负责人");
        long pid=num(c.project,"id");validOwner(pid,r.owner_user_id(),c.users);
        var d=mutation(id,r.version(),c.actor);d.put("owner",r.owner_user_id());changed(db.owner(d));
        var before=c.users.get(num(c.document,"owner_user_id"));var after=c.users.get(r.owner_user_id());
        var log=new HashMap<String,Object>();log.put("projectId",pid);log.put("subprojectId",null);log.put("action","SOP_OWNER_CHANGED");log.put("actor",uid(c.actor));log.put("actorName",c.actor.real_name());log.put("target",r.owner_user_id());log.put("targetName",after.realName());
        log.put("before",c.document.get("code")+": "+before.realName()+"（"+before.username()+"）");log.put("after",c.document.get("code")+": "+after.realName()+"（"+after.username()+"）");log.put("now",now());changed(projects.insertActivity(log));
    }
    public Map<String,Object> editRevision(String token,long id,long rid,RevisionEdit r){
        var c=write(token,id,null);open(c.project);requireEdit(c,"sop:update");var v=draft(id,rid,r.version());
        var d=revisionData(id,num(v,"revision_no"),r.title(),r.applicable_scope(),r.purpose(),r.precautions(),r.revision_note(),c.actor);d.put("id",rid);d.put("version",r.version());changed(db.editRevision(d));bump(c);return revisionView(revision(id,rid));
    }
    public Map<String,Object> newDraft(String token,long id,Draft r){
        var c=write(token,id,null);open(c.project);requireEdit(c,"sop:update");version(c.document,r.version());
        if(db.drafts(id)>0)throw conflict("已有草稿，请继续编辑或先放弃已有草稿");
        var source=revision(id,r.source_revision_id());if(number(source,"status")==0)throw bad("只能从已发布或已停用版本复制草稿");
        long next=db.nextRevision(id);if(next>Integer.MAX_VALUE)throw conflict("版本编号已达上限");
        var data=revisionData(id,next,(String)source.get("title"),(String)source.get("applicable_scope"),(String)source.get("purpose"),(String)source.get("precautions"),null,c.actor);db.insertRevision(data);long rid=num(data,"id");
        copyTaskSteps(num(source,"id"),rid,uid(c.actor));
        bump(c);return revisionView(revision(id,rid));
    }
    private void copyTaskSteps(long sourceRevisionId,long rid,long actorId){
        for(var s:db.steps(sourceRevisionId)){
            var copied=stepData(rid,(String)s.get("title"),(String)s.get("instruction"),(String)s.get("precautions"),(String)s.get("check_requirements"),(BigDecimal)s.get("expected_duration_seconds"));settings(copied,(BigDecimal)s.get("detection_wait_seconds"),number(s,"voice_enabled")==1,(String)s.get("voice_text"));copied.put("position",number(s,"sort_order"));db.insertStep(copied);resources.copy(num(s,"id"),num(copied,"id"),(String)s.get("workplace_requirements"));
            for(var attachment:db.rawAttachments(num(s,"id"))){var link=new HashMap<String,Object>();link.put("stepId",num(copied,"id"));link.put("fileId",attachment.get("file_id"));link.put("caption",attachment.get("caption"));link.put("position",attachment.get("sort_order"));db.copyAttachment(link);visuals.copy(num(attachment,"id"),num(link,"id"),actorId);}
        }
    }
    // Source is a published snapshot already authorized through the calling task.
    public Map<String,Object> createTaskContent(String token,Create request,Long sourceId,Long sourceRevisionId){
        Map<String,Object> source=sourceId==null?null:revision(sourceId,sourceRevisionId);
        if(source!=null&&number(source,"status")==0)throw bad("任务不能复制其他文档的草稿");
        var result=create(token,request);long id=Long.parseLong(result.get("id").toString());
        var draft=(Map<String,Object>)result.get("draft");long rid=Long.parseLong(draft.get("id").toString());
        var a=actor(token);
        if(source!=null){
            var data=revisionData(id,1,request.name(),(String)source.get("applicable_scope"),(String)source.get("purpose"),(String)source.get("precautions"),null,a);data.put("id",rid);data.put("version",0);changed(db.editRevision(data));copyTaskSteps(sourceRevisionId,rid,uid(a));
        }
        result.put("draft",revisionView(revision(id,rid)));return result;
    }
    // Called only after TaskService has authorized and locked the owning task.
    public void syncTaskIdentity(String token,long id,String name,long ownerId){
        var c=write(token,id,ownerId);open(c.project);requireEdit(c,"task:manage");if(resourceDb.contentReferences(id)==0)throw bad("内容不属于任务");validOwner(num(c.project,"id"),ownerId,c.users);
        if(!Objects.equals(c.document.get("name"),name)){var d=mutation(id,number(c.document,"version"),c.actor);d.put("name",text(name,128,true));changed(db.editDocument(d));}
        var latest=document(id);if(num(latest,"owner_user_id")!=ownerId){var d=mutation(id,number(latest,"version"),c.actor);d.put("owner",ownerId);changed(db.owner(d));}
    }
    public Map<String,Object> taskDraft(String token,long id){
        var c=write(token,id,null);open(c.project);requireEdit(c,"sop:update");
        var revisions=db.revisions(id);var existing=revisions.stream().filter(r->number(r,"status")==0).findFirst();
        if(existing.isPresent())return Map.of("revision",revisionView(existing.get()),"created",false);
        var source=revisions.stream().filter(r->number(r,"status")!=0).findFirst().orElse(null);
        long next=db.nextRevision(id);if(next>Integer.MAX_VALUE)throw conflict("版本编号已达上限");
        var data=revisionData(id,next,(String)c.document.get("name"),source==null?null:(String)source.get("applicable_scope"),source==null?null:(String)source.get("purpose"),source==null?null:(String)source.get("precautions"),next>1?"任务内容更新":null,c.actor);db.insertRevision(data);long rid=num(data,"id");
        if(source!=null)copyTaskSteps(num(source,"id"),rid,uid(c.actor));bump(c);return Map.of("revision",revisionView(revision(id,rid)),"created",true);
    }
    public void discard(String token,long id,long rid,Version r){
        var c=write(token,id,null);open(c.project);requireEdit(c,"sop:update");draft(id,rid,r.version());changed(db.discard(revisionMutation(id,rid,r.version(),c.actor)));bump(c);
    }
    public void delete(String token,long id,Version r){
        var c=write(token,id,null);open(c.project);if(!power(c,"sop:delete"))throw denied();version(c.document,r.version());
        if(resourceDb.contentReferences(id)>0||db.published(id)>0||db.bindings(id)>0)throw conflict("已有发布历史或关联记录，不能删除SOP");
        var d=mutation(id,r.version(),c.actor);db.discardAll(d);changed(db.deleteDocument(d));
    }
    public Map<String,Object> addStep(String token,long id,long rid,StepCreate r){
        var c=write(token,id,null);open(c.project);requireEdit(c,"sop:update");var v=draft(id,rid,r.version());var steps=db.steps(rid);if(steps.size()>=1000)throw bad("单个版本最多1000个步骤");
        var d=stepData(rid,r.title(),r.instruction(),r.precautions(),r.check_requirements(),r.expected_duration_seconds());settings(d,r.detection_wait_seconds(),r.voice_enabled(),r.voice_text());d.put("position",steps.size()+1);db.insertStep(d);bumpRevision(c,v);return revisionView(revision(id,rid));
    }
    public Map<String,Object> editStep(String token,long id,long rid,long sid,StepEdit r){
        var c=write(token,id,null);open(c.project);requireEdit(c,"sop:update");var v=draft(id,rid,r.revision_version());var s=step(rid,sid);version(s,r.version());
        var d=stepData(rid,r.title(),r.instruction(),r.precautions(),r.check_requirements(),r.expected_duration_seconds());settings(d,r.detection_wait_seconds()==null?(BigDecimal)s.get("detection_wait_seconds"):r.detection_wait_seconds(),r.voice_enabled()==null?number(s,"voice_enabled")==1:r.voice_enabled(),r.voice_text()==null?(String)s.get("voice_text"):r.voice_text());d.put("id",sid);d.put("version",r.version());changed(db.editStep(d));bumpRevision(c,v);return revisionView(revision(id,rid));
    }
    public Map<String,Object> saveAuthoring(String token,long id,long rid,long sid,Authoring r){
        var c=write(token,id,null);open(c.project);requireEdit(c,"sop:update");var rstep=r.step();var v=draft(id,rid,rstep.revision_version());var previous=step(rid,sid);version(previous,rstep.version());
        var attachments=db.rawAttachments(sid);var ids=new HashSet<Long>();
        for(var visual:r.visuals()){
            if(!ids.add(visual.attachment_id()))throw bad("图片引用不能重复");
            var attachment=attachments.stream().filter(a->num(a,"id")==visual.attachment_id()).findFirst().orElseThrow(this::missing);
            visuals.save(visual,num(attachment,"file_id"),uid(c.actor));
        }
        var d=stepData(rid,rstep.title(),rstep.instruction(),rstep.precautions(),rstep.check_requirements(),rstep.expected_duration_seconds());
        settings(d,rstep.detection_wait_seconds()==null?(BigDecimal)previous.get("detection_wait_seconds"):rstep.detection_wait_seconds(),rstep.voice_enabled()==null?number(previous,"voice_enabled")==1:rstep.voice_enabled(),rstep.voice_text()==null?(String)previous.get("voice_text"):rstep.voice_text());
        d.put("id",sid);d.put("version",rstep.version());changed(db.editStep(d));
        resources.save(sid,r.resources());
        if(r.revision()!=null){var header=r.revision();version(v,header.version());var rd=revisionData(id,num(v,"revision_no"),header.title(),header.applicable_scope(),header.purpose(),header.precautions(),header.revision_note(),c.actor);rd.put("id",rid);rd.put("version",header.version());changed(db.editRevision(rd));bump(c);}else bumpRevision(c,v);
        return revisionView(revision(id,rid));
    }
    public Map<String,Object> deleteStep(String token,long id,long rid,long sid,StepDelete r){
        var c=write(token,id,null);open(c.project);requireEdit(c,"sop:update");var v=draft(id,rid,r.revision_version());version(step(rid,sid),r.version());
        db.deleteAttachments(sid);changed(db.deleteStep(rid,sid,r.version()));int i=1;for(var s:db.steps(rid))changed(db.sortStep(rid,num(s,"id"),i++));bumpRevision(c,v);return revisionView(revision(id,rid));
    }
    public Map<String,Object> sort(String token,long id,long rid,Sort r){
        var c=write(token,id,null);open(c.project);requireEdit(c,"sop:update");var v=draft(id,rid,r.version());var expected=db.steps(rid).stream().map(s->num(s,"id")).toList();
        if(r.ids().size()!=new HashSet<>(r.ids()).size()||!new HashSet<>(r.ids()).equals(new HashSet<>(expected)))throw bad("排序必须包含本版本全部步骤且不能重复");
        for(int i=0;i<r.ids().size();i++)changed(db.sortStep(rid,r.ids().get(i),i+1));bumpRevision(c,v);return revisionView(revision(id,rid));
    }
    public Map<String,Object> publish(String token,long id,long rid,Version r){
        var c=write(token,id,null);open(c.project);if(!power(c,"sop:publish"))throw denied();var v=draft(id,rid,r.version());validOwner(num(c.project,"id"),num(c.document,"owner_user_id"),c.users);
        if(number(v,"revision_no")>1&&text((String)v.get("revision_note"),1000,false)==null)throw bad("新版本发布前请填写修订说明");
        text((String)v.get("title"),128,true);var steps=db.steps(rid);if(steps.isEmpty())throw bad("至少添加一个步骤才能发布");
        for(int i=0;i<steps.size();i++){
            var s=steps.get(i);resources.validate(num(s,"id"));if(number(s,"sort_order")!=i+1)throw bad("步骤顺序不连续，请重新排序");text((String)s.get("title"),128,true);text((String)s.get("instruction"),10000,true);settings(new HashMap<>(),(BigDecimal)s.get("detection_wait_seconds"),number(s,"voice_enabled")==1,(String)s.get("voice_text"));
            for(var a:db.rawAttachments(num(s,"id"))){visuals.validateStored(num(a,"id"));try{images.validateStored(db.file(num(a,"file_id")));}catch(BusinessException e){throw conflict("步骤图片缺失或损坏，请重新上传后发布");}}
        }
        changed(db.publish(revisionMutation(id,rid,r.version(),c.actor)));bump(c);return revisionView(revision(id,rid));
    }
    public Map<String,Object> retire(String token,long id,long rid,Version r){
        var c=write(token,id,null);open(c.project);if(!power(c,"sop:retire"))throw denied();var v=revision(id,rid);version(v,r.version());if(number(v,"status")!=1)throw conflict("仅已发布版本可以停用");
        if(db.activeBindings(rid)>0||resourceDb.revisionTasks(rid)>0)throw conflict("仍有未完成项目或子项目使用本版本，请先切换或解绑");
        changed(db.retire(revisionMutation(id,rid,r.version(),c.actor)));bump(c);return revisionView(revision(id,rid));
    }
    public Map<String,Object> uploadImage(String token,long id,long rid,long sid,int version,org.springframework.web.multipart.MultipartFile file){
        var c=write(token,id,null);open(c.project);requireEdit(c,"sop:update");var v=draft(id,rid,version);step(rid,sid);
        var previous=db.rawAttachments(sid);if(previous.size()>=20)throw bad("每个步骤最多20张图片");
        var stored=images.save(file);
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){
            @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)images.rollbackDelete(stored.key());}
        });
        var data=new HashMap<String,Object>();data.put("key",stored.key());data.put("name",stored.name());data.put("mime",stored.mime());data.put("size",stored.bytes().length);data.put("sha",stored.sha());data.put("actor",uid(c.actor));db.insertFile(data);
        var link=new HashMap<String,Object>();link.put("stepId",sid);link.put("fileId",num(data,"id"));link.put("caption",null);link.put("position",previous.stream().mapToInt(a->number(a,"sort_order")).max().orElse(0)+1);db.copyAttachment(link);bumpRevision(c,v);return revisionView(revision(id,rid));
    }
    public Map<String,Object> removeImage(String token,long id,long rid,long sid,long attachmentId,int version){
        var c=write(token,id,null);open(c.project);requireEdit(c,"sop:update");var v=draft(id,rid,version);step(rid,sid);positive(attachmentId);if(db.deleteAttachment(sid,attachmentId)!=1)throw missing();bumpRevision(c,v);return revisionView(revision(id,rid));
    }
    @Transactional(readOnly=true)
    public Map<String,Object> image(String token,long id,long rid,long fid){var a=actor(token);var d=document(id);read(a,project(num(d,"managing_project_id"),false));revision(id,rid);return imageForRevision(rid,fid);}
    Map<String,Object> imageForRevision(long rid,long fid){positive(fid);var f=db.revisionFile(rid,fid);if(f==null)throw missing();images.validateStored(f);return Map.of("bytes",images.read(f),"mime",f.get("mime_type"),"name",f.get("original_name"));}
    Map<String,Object> boundRevision(long id,long rid){var r=revision(id,rid);if(number(r,"status")==0)throw denied();return revisionView(r);}

    public Map<String,Object> taskRevision(long id,long rid){return boundRevision(id,rid);}
    public Map<String,Object> taskImage(long rid,long fid){return imageForRevision(rid,fid);}

    private record Context(UserInfo actor,Map<String,Object> project,Map<String,Object> document,Map<Long,UserCredentials> users) {}
    private Context write(String token,long id,Long selected){
        var initialActor=actor(token);var before=document(id);var beforeProject=project(num(before,"managing_project_id"),false);read(initialActor,beforeProject);
        var ids=new ArrayList<Long>();ids.add(num(before,"owner_user_id"));if(selected!=null)ids.add(selected);
        var locked=lockUsers(initialActor,ids.toArray(Long[]::new));auth.requireCurrentSession(token,uid(initialActor));var a=actor(token);
        var p=project(num(before,"managing_project_id"),true);var d=db.lockDocument(id);if(d==null)throw missing();
        // Never acquire another user/project lock after the SOP lock. Ownership races retry.
        if(num(d,"managing_project_id")!=num(p,"id")||num(d,"owner_user_id")!=num(before,"owner_user_id"))throw conflict("SOP管理归属已变化，请刷新后重试");
        read(a,p);if(!taskContentEditable(a,p,id))throw denied();return new Context(a,p,d,locked);
    }
    private boolean taskContentEditable(UserInfo a,Map<String,Object> p,long id){
        var task=resourceDb.contentOwnerTask(id);if(task==null)return true;
        return num(task,"project_id")==num(p,"id")&&task.get("task_deleted_at")==null&&task.get("subproject_deleted_at")==null&&number(task,"task_status")!=2&&number(task,"subproject_status")<2&&number(p,"status")<2&&(manage(a,p)||has(a,"task:manage")&&has(a,"project:read")&&Integer.valueOf(1).equals(projects.member(num(p,"id"),uid(a))));
    }
    private Map<Long,UserCredentials> lockUsers(UserInfo a,Long... extra){
        var ids=new TreeSet<Long>();ids.add(uid(a));for(Long id:extra){positive(id);ids.add(id);}var result=new HashMap<Long,UserCredentials>();
        for(long id:ids){var user=users.lockById(id);if(user==null)throw bad("用户不存在");result.put(id,user);}return result;
    }
    private void validOwner(long pid,long owner,Map<Long,UserCredentials> locked){var u=locked.get(owner);if(u==null||u.deletedAt()!=null||!Integer.valueOf(1).equals(u.status())||!Integer.valueOf(1).equals(projects.member(pid,owner)))throw bad("SOP负责人必须是管理项目的正常有效编辑成员，请先办理交接");}
    private UserInfo actor(String token){var a=auth.authenticate(token);if(a.must_change_password())throw new BusinessException(HttpStatus.FORBIDDEN,"请先修改初始密码");return a;}
    private boolean has(UserInfo a,String code){return a.is_superuser()||a.permissions().contains(code);}
    private boolean manage(UserInfo a,Map<String,Object> p){return a.is_superuser()||uid(a)==num(p,"owner_user_id");}
    private boolean edit(UserInfo a,Map<String,Object> p,String permission){return manage(a,p)||has(a,permission)&&has(a,"project:read")&&Integer.valueOf(1).equals(projects.member(num(p,"id"),uid(a)));}
    private void read(UserInfo a,Map<String,Object> p){if(!manage(a,p)&&(projects.member(num(p,"id"),uid(a))==null||!has(a,"project:read")||!has(a,"sop:read")))throw denied();}
    private void requireEdit(Context c,String permission){if(!edit(c.actor,c.project,permission))throw denied();}
    private boolean power(Context c,String permission){return manage(c.actor,c.project)||uid(c.actor)==num(c.document,"owner_user_id")&&edit(c.actor,c.project,permission);}
    private Map<String,Object> document(long id){positive(id);var d=db.document(id);if(d==null)throw missing();return d;}
    private Map<String,Object> project(long id,boolean lock){positive(id);var p=lock?projects.lockProject(id):projects.project(id);if(p==null)throw missing();return p;}
    private Map<String,Object> revision(long id,long rid){positive(rid);var v=db.revision(id,rid);if(v==null)throw missing();return v;}
    private Map<String,Object> draft(long id,long rid,int version){var v=revision(id,rid);version(v,version);if(number(v,"status")!=0)throw conflict("已发布或停用版本内容只读，请复制为新草稿");return v;}
    private Map<String,Object> step(long rid,long sid){positive(sid);var s=db.step(rid,sid);if(s==null)throw missing();return s;}
    private void open(Map<String,Object> p){if(number(p,"status")>=2)throw conflict("管理项目已完成或归档，业务内容只读；可先办理恢复或重新开启");}
    private void version(Map<String,Object> row,int v){if(number(row,"version")!=v)throw conflict("数据已更新，请刷新后重试");}
    private void bump(Context c){changed(db.bumpDocument(mutation(num(c.document,"id"),number(c.document,"version"),c.actor)));}
    private void bumpRevision(Context c,Map<String,Object> v){changed(db.bumpRevision(revisionMutation(num(c.document,"id"),num(v,"id"),number(v,"version"),c.actor)));bump(c);}
    private Map<String,Object> mutation(long id,int version,UserInfo a){var d=new HashMap<String,Object>();d.put("id",id);d.put("version",version);d.put("actor",uid(a));d.put("now",now());return d;}
    private Map<String,Object> revisionMutation(long id,long rid,int version,UserInfo a){var d=mutation(rid,version,a);d.put("sopId",id);return d;}
    private Map<String,Object> revisionData(long id,long no,String title,String scope,String purpose,String precautions,String note,UserInfo a){var d=mutation(0,0,a);d.put("sopId",id);d.put("number",no);d.put("title",text(title,128,true));d.put("scope",text(scope,1000,false));d.put("purpose",text(purpose,5000,false));d.put("precautions",text(precautions,5000,false));d.put("note",text(note,1000,false));return d;}
    private Map<String,Object> stepData(long rid,String title,String instruction,String precautions,String checks,BigDecimal duration){var d=new HashMap<String,Object>();d.put("revisionId",rid);d.put("title",text(title,128,true));d.put("instruction",text(instruction,10000,true));d.put("precautions",text(precautions,5000,false));d.put("checks",text(checks,5000,false));d.put("duration",duration);settings(d,null,false,null);return d;}
    private void settings(Map<String,Object> d,BigDecimal wait,Boolean voice,String text){
        wait=wait==null?BigDecimal.ZERO:wait;if(wait.signum()<0||wait.compareTo(new BigDecimal("3600"))>0||wait.stripTrailingZeros().scale()>1)throw bad("检测等待时间须为0至3600秒，最多一位小数");
        String spoken=text(text,1000,false);boolean enabled=Boolean.TRUE.equals(voice);if(enabled&&spoken==null)throw bad("启用语音时请填写提示内容");
        d.put("wait",wait);d.put("voice",enabled?1:0);d.put("voiceText",spoken);
    }
    private Map<String,Object> documentView(Map<String,Object> d,Map<String,Object> p,UserInfo a){var v=view(d);boolean open=number(p,"status")<2&&taskContentEditable(a,p,num(d,"id"));var c=new Context(a,p,d,Map.of());v.put("can_edit",open&&edit(a,p,"sop:update"));v.put("can_transfer_owner",manage(a,p));v.put("can_publish",open&&power(c,"sop:publish"));v.put("can_retire",open&&power(c,"sop:retire") );v.put("can_delete",open&&power(c,"sop:delete")&&db.published(num(d,"id"))==0&&db.bindings(num(d,"id"))==0&&resourceDb.contentReferences(num(d,"id"))==0);return v;}
    private Map<String,Object> revisionView(Map<String,Object> r){var v=view(r);v.put("steps",db.steps(num(r,"id")).stream().map(s->{var step=view(s);step.put("resource_requirements",resources.read(num(s,"id")));step.put("attachments",db.attachments(num(s,"id")).stream().map(a->{var item=view(a);item.put("visual",visuals.read(num(a,"id")));return item;}).toList());return step;}).toList());return v;}
    private Map<String,Object> view(Map<String,Object> row){var v=new HashMap<String,Object>();row.forEach((k,x)->{if(k.equals("deleted_at")||k.equals("draft_slot"))return;if(x!=null&&(k.equals("id")||k.endsWith("_id")||k.equals("created_by")||k.equals("updated_by")||k.equals("published_by")||k.equals("retired_by")))x=x.toString();if(x instanceof LocalDateTime t)x=t.toInstant(ZoneOffset.UTC).toString();if(x instanceof java.sql.Timestamp t)x=t.toLocalDateTime().toInstant(ZoneOffset.UTC).toString();v.put(k,x);});return v;}
    private Map<String,Object> page(int p,int limit){if(p<1||limit<1||limit>100)throw bad("分页参数不正确");var q=new HashMap<String,Object>();q.put("limit",limit);q.put("offset",(long)(p-1)*limit);return q;}
    private String text(String s,int max,boolean required){s=s==null?null:s.trim();if(s!=null&&s.isEmpty())s=null;if(required&&s==null||s!=null&&s.length()>max)throw bad("内容为空或超过字段长度限制");return s;}
    private LocalDateTime now(){return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);}
    private long uid(UserInfo a){return Long.parseLong(a.id());}
    private long num(Map<String,Object> row,String k){return ((Number)row.get(k)).longValue();}
    private int number(Map<String,Object> row,String k){return ((Number)row.get(k)).intValue();}
    private void positive(long id){if(id<=0)throw bad("ID必须为正整数");}
    private void changed(int n){if(n!=1)throw conflict("数据已更新，请刷新后重试");}
    private BusinessException bad(String m){return new BusinessException(HttpStatus.BAD_REQUEST,m);}
    private BusinessException conflict(String m){return new BusinessException(HttpStatus.CONFLICT,m);}
    private BusinessException denied(){return new BusinessException(HttpStatus.FORBIDDEN,"无此SOP的操作权限");}
    private BusinessException missing(){return new BusinessException(HttpStatus.NOT_FOUND,"SOP、版本、步骤或管理项目不存在");}
}
