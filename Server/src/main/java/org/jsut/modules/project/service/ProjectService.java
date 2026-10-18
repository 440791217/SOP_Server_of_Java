package org.jsut.modules.project.service;

import org.jsut.common.BusinessException;
import org.jsut.common.BusinessCode;
import org.jsut.modules.project.dto.ProjectRequests.*;
import org.jsut.modules.project.mapper.ProjectMapper;
import org.jsut.modules.user.dto.UserInfo;
import org.jsut.modules.user.mapper.AuthMapper;
import org.jsut.modules.user.service.AuthService;
import org.jsut.modules.sop.service.SopReferenceGuard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class ProjectService {
    private final ProjectMapper db;
    private final AuthService auth;
    private final AuthMapper users;
    private final Clock clock;
    private final SopReferenceGuard sopReferences;
    private final RootDeletionService deletion;
    public ProjectService(ProjectMapper db, AuthService auth, AuthMapper users, Clock clock,SopReferenceGuard sopReferences,RootDeletionService deletion) {this.deletion=deletion;this.db=db;this.auth=auth;this.users=users;this.clock=clock;this.sopReferences=sopReferences;}

    @Transactional(readOnly=true)
    public Map<String,Object> list(String token,int page,int limit,String keyword,Integer status) {
        var a=actor(token);
        if(page<1 || limit<1 || limit>100 || status!=null && (status<0 || status>3)) throw bad("分页或状态参数不正确");
        var q=new HashMap<String,Object>();q.put("actor",uid(a));q.put("root",a.is_superuser());
        q.put("keyword",text(keyword,128,false));q.put("status",status);q.put("limit",limit);q.put("offset",(long)(page-1)*limit);
        // Query scope only contains owned/member projects; function permissions are checked per row.
        if(!a.is_superuser() && !has(a,"project:list")) {
            // Owners can manage their own projects without a global function grant.
            // Mapper narrows member access below through a separate owner-only flag.
            q.put("ownerOnly",true);
        }
        return Map.of("items",db.list(q).stream().map(r->projectView(r,a)).toList(),"total",db.count(q),
                "can_create",has(a,"project:create"));
    }
    @Transactional(readOnly=true)
    public Map<String,Object> detail(String token,long id) {var a=actor(token);var p=required(id);read(a,p);return projectView(p,a);}
    @Transactional(readOnly=true)
    public List<Map<String,Object>> children(String token,long id) {
        var a=actor(token);var p=required(id);read(a,p);return db.children(id).stream().map(s->subView(s,p,a)).toList();
    }
    @Transactional(readOnly=true)
    public Map<String,Object> child(String token,long pid,long id) {var a=actor(token);var p=required(pid);read(a,p);return subView(sub(pid,id),p,a);}
    @Transactional(readOnly=true)
    public List<Map<String,Object>> options(String token,Long pid,String keyword) {
        var a=actor(token);
        if(pid==null) {if(!has(a,"project:create")) throw denied();}
        else {var p=required(pid);read(a,p);if(!owner(a,p)&&!editable(a,p,"subproject:create"))throw denied();}
        var q=new HashMap<String,Object>();q.put("projectId",pid);q.put("keyword",text(keyword,128,false));
        return db.users(q).stream().map(this::view).toList();
    }
    public Map<String,Object> create(String token,Create r) {
        var a=writeActor(token,r.owner_user_id());if(!has(a,"project:create"))throw denied();
        var d=profile(r.name(),r.description(),a);d.put("code",BusinessCode.validate(r.code(),"PRJ"));d.put("customer",text(r.customer_name(),128,false));d.put("owner",r.owner_user_id());
        try{db.insertProject(d);}catch(org.springframework.dao.DuplicateKeyException e){throw conflict("编号已存在，请先确认是否已创建成功；如需另建，请重新打开新建窗口");}long id=num(d,"id");db.addMember(id,uid(a),uid(a));
        if(uid(a)!=r.owner_user_id())db.addMember(id,r.owner_user_id(),uid(a));
        return projectView(required(id),a);
    }
    public Map<String,Object> edit(String token,long id,Edit r) {
        var a=writeActor(token,null);var p=lock(id);checkVersion(p,r.version());writable(p);
        if(!editable(a,p,"project:update"))throw denied();
        var d=mutation(id,r.version(),a);d.putAll(profile(r.name(),r.description(),a));d.put("customer",text(r.customer_name(),128,false));
        changed(db.updateProject(d));return projectView(required(id),a);
    }
    public void status(String token,long id,Status r) {
        var a=writeActor(token,null);var p=lock(id);checkVersion(p,r.version());if(!owner(a,p))throw denied();
        transition(integer(p,"status"),r.status());
        if(r.status()==2 && db.children(id).stream().anyMatch(s->integer(s,"status")<2))throw conflict("请先完成或归档所有子项目");
        var d=mutation(id,r.version(),a);d.put("status",r.status());changed(db.projectStatus(d));
        if(r.status()==3)db.archiveChildren(d);
    }
    public void delete(String token,long id,Version r) {
        var a=writeActor(token,null);if(!a.is_superuser())throw denied();var p=lock(id);checkVersion(p,r.version());
        deletion.remove(a,id,null,null,(String)p.get("name"));changed(db.deleteProject(mutation(id,r.version(),a)));
    }
    public void restore(String token,long id,Version r) {
        var a=writeActor(token,null);var p=lock(id);checkVersion(p,r.version());if(!owner(a,p))throw denied();
        if(integer(p,"status")!=3)throw conflict("仅已归档项目可以恢复");
        var d=mutation(id,r.version(),a);changed(db.restoreProject(d));db.restoreChildren(d);
    }
    public void restoreSub(String token,long pid,long id,Version r) {
        var a=writeActor(token,null);var p=lock(pid);writable(p);var s=sub(pid,id);checkVersion(s,r.version());
        if(!subManager(a,p,s,"subproject:restore"))throw denied();
        if(integer(s,"status")!=3)throw conflict("仅已归档子项目可以恢复");
        if(s.get("archived_from_status") instanceof Number previous && previous.intValue()==1 && integer(p,"status")!=1)throw conflict("请先将主项目设为进行中，再恢复此子项目");
        var d=mutation(id,r.version(),a);d.put("projectId",pid);changed(db.restoreSub(d));bump(p,a);
    }
    public Map<String,Object> createSub(String token,long pid,SubCreate r) {
        var a=writeActor(token,r.owner_user_id());var p=lock(pid);writable(p);if(!editable(a,p,"subproject:create"))throw denied();memberOwner(pid,r.owner_user_id());
        var d=profile(r.name(),r.description(),a);d.put("projectId",pid);d.put("owner",r.owner_user_id());d.put("code",BusinessCode.validate(r.code(),"SUB"));
        int last=db.children(pid).stream().mapToInt(s->integer(s,"sort_order")).max().orElse(-1);
        if(last==Integer.MAX_VALUE)throw conflict("排序值已达上限，请重新排序");d.put("position",last+1);try{db.insertSub(d);}catch(org.springframework.dao.DuplicateKeyException e){throw conflict("编号已存在，请先确认是否已创建成功；如需另建，请重新打开新建窗口");}bump(p,a);
        return subView(sub(pid,num(d,"id")),p,a);
    }
    public Map<String,Object> editSub(String token,long pid,long id,SubEdit r) {
        var a=writeActor(token,null);var p=lock(pid);writable(p);var s=sub(pid,id);checkVersion(s,r.version());writable(s);
        if(!editable(a,p,"subproject:update"))throw denied();
        if(r.owner_user_id()!=null && r.owner_user_id()!=num(s,"owner_user_id"))throw bad("负责人变更请使用独立转移接口");
        var d=mutation(id,r.version(),a);d.putAll(profile(r.name(),r.description(),a));d.put("projectId",pid);changed(db.updateSub(d));bump(p,a);
        return subView(sub(pid,id),p,a);
    }
    public void subStatus(String token,long pid,long id,Status r) {
        var a=writeActor(token,null);var p=lock(pid);writable(p);var s=sub(pid,id);checkVersion(s,r.version());
        if(!subManager(a,p,s,"subproject:status"))throw denied();transition(integer(s,"status"),r.status());
        if(r.status()==1 && integer(p,"status")!=1)throw conflict("请先将主项目设为进行中");
        var d=mutation(id,r.version(),a);d.put("projectId",pid);d.put("status",r.status());changed(db.subStatus(d));bump(p,a);
    }
    public void deleteSub(String token,long pid,long id,Version r) {
        var a=writeActor(token,null);if(!a.is_superuser())throw denied();var p=lock(pid);var s=sub(pid,id);checkVersion(s,r.version());
        deletion.remove(a,pid,id,null,(String)s.get("name"));var d=mutation(id,r.version(),a);d.put("projectId",pid);changed(db.deleteSub(d));bump(p,a);
    }
    public void sort(String token,long pid,Sort r) {
        var a=writeActor(token,null);var p=lock(pid);checkVersion(p,r.version());writable(p);if(!editable(a,p,"subproject:update"))throw denied();
        var expected=db.children(pid).stream().map(s->num(s,"id")).toList();
        if(r.ids().size()!=new HashSet<>(r.ids()).size() || !new HashSet<>(expected).equals(new HashSet<>(r.ids())))throw bad("排序必须包含全部有效子项目且不能重复或跨项目");
        for(int i=0;i<r.ids().size();i++)changed(db.sort(pid,r.ids().get(i),i,uid(a)));bump(p,a);
    }
    private UserInfo actor(String token) {var a=auth.authenticate(token);if(a.must_change_password())throw new BusinessException(HttpStatus.FORBIDDEN,"请先修改初始密码");return a;}
    private UserInfo writeActor(String token,Long selected) {
        var a=actor(token);var ids=new TreeSet<Long>();ids.add(uid(a));if(selected!=null){if(selected<=0)throw bad("负责人ID不正确");ids.add(selected);}
        for(long id:ids){var u=users.lockById(id);if(u==null||u.deletedAt()!=null||!Integer.valueOf(1).equals(u.status()))throw bad("用户不存在或已停用");}
        auth.requireCurrentSession(token,uid(a));return actor(token);
    }
    private void read(UserInfo a,Map<String,Object> p) {if(owner(a,p))return;if(db.member(num(p,"id"),uid(a))==null||!has(a,"project:read"))throw denied();}
    private boolean owner(UserInfo a,Map<String,Object> p) {return a.is_superuser()||uid(a)==num(p,"owner_user_id");}
    private boolean has(UserInfo a,String code) {return a.is_superuser()||a.permissions().contains(code);}
    private boolean editable(UserInfo a,Map<String,Object> p,String permission) {return owner(a,p)||Integer.valueOf(1).equals(db.member(num(p,"id"),uid(a)))&&has(a,permission);}
    private boolean subManager(UserInfo a,Map<String,Object> p,Map<String,Object> s,String permission) {return owner(a,p)||uid(a)==num(s,"owner_user_id")&&Integer.valueOf(1).equals(db.member(num(p,"id"),uid(a)))&&has(a,permission);}
    private void memberOwner(long pid,long id) {if(!Integer.valueOf(1).equals(db.member(pid,id)))throw bad("子项目负责人必须是项目的编辑成员");}
    private Map<String,Object> required(long id) {if(id<=0)throw bad("项目ID不正确");var p=db.project(id);if(p==null)throw missing();return p;}
    private Map<String,Object> lock(long id) {if(id<=0)throw bad("项目ID不正确");var p=db.lockProject(id);if(p==null)throw missing();return p;}
    private Map<String,Object> sub(long pid,long id) {if(id<=0)throw bad("子项目ID不正确");var s=db.sub(pid,id);if(s==null)throw missing();return s;}
    private void writable(Map<String,Object> p) {if(integer(p,"status")>=2)throw conflict("已完成或归档记录不可编辑，请先重新开启已完成项目");}
    private void checkVersion(Map<String,Object> p,int version) {if(integer(p,"version")!=version)throw conflict("数据已更新，请刷新后重试");}
    private void transition(int before,int after) {
        if(!(after==3&&before!=3||before==0&&after==1||before==1&&after==2||before==2&&after==1))throw conflict("不允许的状态变更；已归档记录请使用恢复归档操作");
    }
    private void bump(Map<String,Object> p,UserInfo a) {changed(db.bump(mutation(num(p,"id"),integer(p,"version"),a)));}
    private Map<String,Object> profile(String name,String description,UserInfo a) {var d=new HashMap<String,Object>();d.put("name",text(name,128,true));d.put("description",text(description,5000,false));d.put("actor",uid(a));return d;}
    private Map<String,Object> mutation(long id,int v,UserInfo a) {var d=new HashMap<String,Object>();d.put("id",id);d.put("version",v);d.put("actor",uid(a));d.put("deletedAt",LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC));return d;}
    private Map<String,Object> projectView(Map<String,Object> p,UserInfo a) {
        var v=view(p);boolean open=integer(p,"status")<2;
        v.put("can_delete",a.is_superuser());v.put("can_manage_members",owner(a,p));v.put("can_transfer_owner",owner(a,p));
        v.put("can_restore",integer(p,"status")==3&&owner(a,p));
        v.put("can_edit",open&&editable(a,p,"project:update"));v.put("can_manage",owner(a,p));
        v.put("can_create_subproject",open&&editable(a,p,"subproject:create"));v.put("can_sort",open&&editable(a,p,"subproject:update"));return v;
    }
    private Map<String,Object> subView(Map<String,Object> s,Map<String,Object> p,UserInfo a) {
        var v=view(s);boolean open=integer(p,"status")<2;
        boolean needsRunning=s.get("archived_from_status") instanceof Number n && n.intValue()==1;
        v.put("can_restore",open&&integer(s,"status")==3&&(!needsRunning||integer(p,"status")==1)&&subManager(a,p,s,"subproject:restore"));
        v.put("can_transfer_owner",owner(a,p));
        v.put("can_edit",open&&integer(s,"status")<2&&editable(a,p,"subproject:update"));
        v.put("can_status",open&&subManager(a,p,s,"subproject:status"));v.put("can_delete",a.is_superuser());return v;
    }
    private Map<String,Object> view(Map<String,Object> row) {
        var v=new HashMap<String,Object>();row.forEach((k,value)->{
            if(k.equals("deleted_at"))return;
            if(value!=null&&(k.equals("id")||k.endsWith("_id")||k.equals("created_by")||k.equals("updated_by")))value=value.toString();
            if(value instanceof LocalDateTime t)value=t.toInstant(ZoneOffset.UTC).toString();
            if(value instanceof java.sql.Timestamp t)value=t.toLocalDateTime().toInstant(ZoneOffset.UTC).toString();v.put(k,value);
        });return v;
    }
    private String text(String value,int max,boolean required) {String s=value==null?null:value.trim();if(s!=null&&s.isEmpty())s=null;if(required&&s==null||s!=null&&s.length()>max)throw bad("字段为空或超过长度限制");return s;}
    private long uid(UserInfo a){return Long.parseLong(a.id());}
    private long num(Map<String,Object> r,String k){return ((Number)r.get(k)).longValue();}
    private int integer(Map<String,Object> r,String k){return ((Number)r.get(k)).intValue();}
    private void changed(int n){if(n!=1)throw conflict("数据已更新，请刷新后重试");}
    private BusinessException bad(String m){return new BusinessException(HttpStatus.BAD_REQUEST,m);}
    private BusinessException conflict(String m){return new BusinessException(HttpStatus.CONFLICT,m);}
    private BusinessException denied(){return new BusinessException(HttpStatus.FORBIDDEN,"无此项目的操作权限");}
    private BusinessException missing(){return new BusinessException(HttpStatus.NOT_FOUND,"项目或子项目不存在");}
}
