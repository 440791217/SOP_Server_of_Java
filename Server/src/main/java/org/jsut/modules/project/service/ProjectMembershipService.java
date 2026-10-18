package org.jsut.modules.project.service;

import org.jsut.common.BusinessException;
import org.jsut.modules.project.dto.ProjectRequests.*;
import org.jsut.modules.project.mapper.ProjectMapper;
import org.jsut.modules.user.dto.UserInfo;
import org.jsut.modules.user.entity.UserCredentials;
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
public class ProjectMembershipService {
    private final ProjectMapper db;
    private final AuthMapper users;
    private final AuthService auth;
    private final Clock clock;
    private final SopReferenceGuard sopReferences;
    public ProjectMembershipService(ProjectMapper db,AuthMapper users,AuthService auth,Clock clock,SopReferenceGuard sopReferences) {
        this.db=db;this.users=users;this.auth=auth;this.clock=clock;this.sopReferences=sopReferences;
    }
    @Transactional(readOnly=true)
    public Map<String,Object> members(String token,long pid,int page,int limit,String keyword,Integer status) {
        var a=actor(token);var p=project(pid,false);read(a,p);var q=query(pid,page,limit);
        if(status!=null&&status!=0&&status!=1)throw bad("成员状态不正确");
        q.put("keyword",keyword(keyword));q.put("status",status);
        var items=db.members(q).stream().map(m->{
            var v=view(m);long userId=num(m,"user_id");boolean isOwner=userId==num(p,"owner_user_id");
            v.put("is_owner",isOwner);
            boolean active=number(m,"member_status")==1;
            long ownedSops=sopReferences.owned(pid,userId);v.put("owned_sops",ownedSops);
            v.put("can_change",manage(a,p)&&active&&!isOwner&&num(m,"owned_subprojects")==0&&ownedSops==0);
            v.put("can_remove",manage(a,p)&&active&&!isOwner&&num(m,"owned_subprojects")==0&&ownedSops==0);
            var expected=new ArrayList<>(List.of("project:list","project:read"));
            if(number(m,"access_level")==1)expected.addAll(List.of("project:update","subproject:create","subproject:update"));
            var effective=users.permissions(userId);
            // Root and the current project owner use their scoped management capability.
            boolean bypass=isOwner||number(m,"is_superuser")==1;
            v.put("missing_permissions",bypass?List.of():expected.stream().filter(code->!effective.contains(code)).toList());
            return v;
        }).toList();
        return Map.of("items",items,"total",db.memberCount(q),"can_manage",manage(a,p),"project_version",number(p,"version"));
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> options(String token,long pid,String keyword,String purpose) {
        var a=actor(token);var p=project(pid,false);if(!manage(a,p))throw denied();
        var q=new HashMap<String,Object>();q.put("projectId",pid);q.put("keyword",keyword(keyword));
        if("subproject_owner".equals(purpose))return db.users(q).stream().map(this::view).toList();
        if(!"member".equals(purpose)&&!"project_owner".equals(purpose))throw bad("候选人用途不正确");
        q.put("includeMembers","project_owner".equals(purpose));
        return db.memberCandidates(q).stream().map(this::view).toList();
    }
    public void add(String token,long pid,MemberAdd r) {
        var w=write(token,r.user_id());var p=project(pid,true);if(!manage(w.actor,p))throw denied();version(p,r.version());validUser(w.target);
        var old=db.memberRow(pid,r.user_id());if(old!=null&&number(old,"member_status")==1)throw conflict("用户已是有效项目成员");
        saveMember(pid,r.user_id(),r.access_level(),old,w,"MEMBER_ADDED");bump(p,w.actor);
    }
    public void access(String token,long pid,long userId,MemberEdit r) {
        var w=write(token,userId);var p=project(pid,true);if(!manage(w.actor,p))throw denied();var m=member(pid,userId);version(m,r.version());
        if(r.access_level()==number(m,"access_level"))throw conflict("成员权限未发生变化");
        if(r.access_level()==0)protectOwner(p,userId);
        saveMember(pid,userId,r.access_level(),m,w,"MEMBER_ACCESS_CHANGED");bump(p,w.actor);
    }
    public void remove(String token,long pid,long userId,Version r) {
        var w=write(token,userId);var p=project(pid,true);if(!manage(w.actor,p))throw denied();var m=member(pid,userId);version(m,r.version());protectOwner(p,userId);
        var d=memberData(pid,userId,number(m,"access_level"),number(m,"version"),w.actor);d.put("memberStatus",0);d.put("removedAt",now());changed(db.updateMember(d));
        log(pid,null,"MEMBER_REMOVED",w,"有效成员","已移除");bump(p,w.actor);
    }
    public void owner(String token,long pid,Owner r) {
        var w=write(token,r.owner_user_id());var p=project(pid,true);if(!manage(w.actor,p))throw denied();version(p,r.version());validUser(w.target);
        long before=num(p,"owner_user_id");if(before==r.owner_user_id())throw conflict("该用户已是项目负责人");
        var previous=db.project(pid);
        String beforeName=previous.get("owner_name")+"（"+previous.get("owner_username")+"）";
        // Preserve the old owner's active editor membership, and add/promote the new owner atomically.
        var old=db.memberRow(pid,r.owner_user_id());
        if(old==null||number(old,"member_status")!=1||number(old,"access_level")!=1)saveMember(pid,r.owner_user_id(),1,old,w,"MEMBER_ACCESS_CHANGED");
        var d=mutation(pid,r.version(),w.actor);d.put("owner",r.owner_user_id());changed(db.projectOwner(d));
        log(pid,null,"PROJECT_OWNER_CHANGED",w,beforeName,w.target.realName()+"（"+w.target.username()+"）");
    }
    public void subOwner(String token,long pid,long sid,Owner r) {
        var w=write(token,r.owner_user_id());var p=project(pid,true);if(!manage(w.actor,p))throw denied();validUser(w.target);
        var s=db.sub(pid,sid);if(s==null)throw missing();version(s,r.version());
        if(!Integer.valueOf(1).equals(db.member(pid,r.owner_user_id())))throw bad("新负责人必须是项目有效编辑成员");
        long before=num(s,"owner_user_id");if(before==r.owner_user_id())throw conflict("该用户已是子项目负责人");
        var d=mutation(sid,r.version(),w.actor);d.put("projectId",pid);d.put("owner",r.owner_user_id());changed(db.subOwner(d));bump(p,w.actor);
        log(pid,sid,"SUBPROJECT_OWNER_CHANGED",w,s.get("owner_name")+"（"+s.get("owner_username")+"）",w.target.realName()+"（"+w.target.username()+"）");
    }
    @Transactional(readOnly=true)
    public Map<String,Object> activities(String token,long pid,int page,int limit) {
        var a=actor(token);var p=project(pid,false);read(a,p);var q=query(pid,page,limit);
        return Map.of("items",db.activities(q).stream().map(this::view).toList(),"total",db.activityCount(pid));
    }
    private void saveMember(long pid,long userId,int access,Map<String,Object> old,Writer w,String action) {
        var d=memberData(pid,userId,access,old==null?0:number(old,"version"),w.actor);
        if(old==null)changed(db.insertMember(d));else changed(db.updateMember(d));
        String before=old==null?"非成员":number(old,"member_status")==0?"已移除":number(old,"access_level")==1?"编辑成员":"查看成员";
        log(pid,null,old!=null&&number(old,"member_status")==0?"MEMBER_RESTORED":action,w,before,access==1?"编辑成员":"查看成员");
    }
    private void protectOwner(Map<String,Object> p,long userId) {
        if(num(p,"owner_user_id")==userId)throw conflict("请先转移项目负责人，再移除或降级该成员");
        if(db.ownedChildren(num(p,"id"),userId)>0)throw conflict("请先转移该成员负责的子项目，包括已归档子项目");
        sopReferences.memberChange(num(p,"id"),userId);
    }
    private Map<String,Object> member(long pid,long userId) {
        var m=db.memberRow(pid,userId);if(m==null||number(m,"member_status")!=1)throw missing();return m;
    }
    private record Writer(UserInfo actor,UserCredentials target) {}
    private Writer write(String token,long target) {
        if(target<=0)throw bad("用户ID不正确");var initial=actor(token);var ids=new TreeSet<Long>(List.of(uid(initial),target));UserCredentials selected=null;
        for(long id:ids){var u=users.lockById(id);if(id==target)selected=u;}
        if(selected==null)throw bad("用户不存在");auth.requireCurrentSession(token,uid(initial));return new Writer(actor(token),selected);
    }
    private void validUser(UserCredentials u){if(u.deletedAt()!=null||!Integer.valueOf(1).equals(u.status()))throw bad("用户已停用、离职或删除");}
    private UserInfo actor(String token){var a=auth.authenticate(token);if(a.must_change_password())throw denied();return a;}
    private Map<String,Object> project(long pid,boolean lock){if(pid<=0)throw bad("项目ID不正确");var p=lock?db.lockProject(pid):db.project(pid);if(p==null)throw missing();return p;}
    private boolean manage(UserInfo a,Map<String,Object> p){return a.is_superuser()||uid(a)==num(p,"owner_user_id");}
    private void read(UserInfo a,Map<String,Object> p){if(!manage(a,p)&&(db.member(num(p,"id"),uid(a))==null||!a.permissions().contains("project:read")))throw denied();}
    private void version(Map<String,Object> m,int version){if(number(m,"version")!=version)throw conflict("数据已更新，请刷新后重试");}
    private void bump(Map<String,Object> p,UserInfo a){changed(db.bump(mutation(num(p,"id"),number(p,"version"),a)));}
    private Map<String,Object> mutation(long id,int version,UserInfo a){var d=new HashMap<String,Object>();d.put("id",id);d.put("version",version);d.put("actor",uid(a));return d;}
    private Map<String,Object> memberData(long pid,long userId,int access,int version,UserInfo a){var d=mutation(pid,version,a);d.put("projectId",pid);d.put("userId",userId);d.put("access",access);d.put("memberStatus",1);d.put("removedAt",null);return d;}
    private void log(long pid,Long sid,String action,Writer w,String before,String after){
        var d=new HashMap<String,Object>();d.put("projectId",pid);d.put("subprojectId",sid);d.put("action",action);d.put("actor",uid(w.actor));d.put("actorName",w.actor.real_name());d.put("target",w.target.id());d.put("targetName",w.target.realName());d.put("before",before);d.put("after",after);d.put("now",now());changed(db.insertActivity(d));
    }
    private Map<String,Object> query(long pid,int page,int limit){if(page<1||limit<1||limit>100)throw bad("分页参数不正确");var q=new HashMap<String,Object>();q.put("projectId",pid);q.put("limit",limit);q.put("offset",(long)(page-1)*limit);return q;}
    private String keyword(String s){if(s==null||s.isBlank())return null;if(s.trim().length()>128)throw bad("搜索内容过长");return s.trim();}
    private Map<String,Object> view(Map<String,Object> row){var v=new HashMap<String,Object>();row.forEach((k,x)->{if(x!=null&&(k.equals("id")||k.endsWith("_id")||k.equals("created_by")||k.equals("updated_by")))x=x.toString();if(x instanceof LocalDateTime t)x=t.toInstant(ZoneOffset.UTC).toString();if(x instanceof java.sql.Timestamp t)x=t.toLocalDateTime().toInstant(ZoneOffset.UTC).toString();v.put(k,x);});return v;}
    private LocalDateTime now(){return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);}
    private long uid(UserInfo a){return Long.parseLong(a.id());}
    private long num(Map<String,Object> row,String k){return ((Number)row.get(k)).longValue();}
    private int number(Map<String,Object> row,String k){return ((Number)row.get(k)).intValue();}
    private void changed(int n){if(n!=1)throw conflict("数据已更新，请刷新后重试");}
    private BusinessException bad(String m){return new BusinessException(HttpStatus.BAD_REQUEST,m);}
    private BusinessException conflict(String m){return new BusinessException(HttpStatus.CONFLICT,m);}
    private BusinessException denied(){return new BusinessException(HttpStatus.FORBIDDEN,"无此项目的成员管理或负责人转移权限");}
    private BusinessException missing(){return new BusinessException(HttpStatus.NOT_FOUND,"项目、子项目或有效成员不存在");}
}
