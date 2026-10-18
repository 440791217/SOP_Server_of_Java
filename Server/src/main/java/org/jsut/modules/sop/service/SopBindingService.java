package org.jsut.modules.sop.service;

import org.jsut.common.BusinessException;
import org.jsut.modules.sop.dto.SopRequests.*;
import org.jsut.modules.sop.mapper.*;
import org.jsut.modules.project.mapper.ProjectMapper;
import org.jsut.modules.user.dto.UserInfo;
import org.jsut.modules.user.mapper.AuthMapper;
import org.jsut.modules.user.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class SopBindingService {
    private final SopBindingMapper db;private final SopMapper sops;private final ProjectMapper projects;private final AuthMapper users;private final AuthService auth;private final SopService content;
    public SopBindingService(SopBindingMapper db,SopMapper sops,ProjectMapper projects,AuthMapper users,AuthService auth,SopService content){this.db=db;this.sops=sops;this.projects=projects;this.users=users;this.auth=auth;this.content=content;}
    private record Target(Map<String,Object> project,Map<String,Object> entity){}
    @Transactional(readOnly=true)
    public Map<String,Object> list(String token,long pid,Long sid){var a=actor(token);var t=target(pid,sid,false);read(a,t.project);return listing(a,pid,sid,t);}
    @Transactional(readOnly=true)
    public List<Map<String,Object>> options(String token,long pid,Long sid,String keyword){var a=actor(token);var t=target(pid,sid,false);if(!canManage(a,t))throw denied();var q=new HashMap<String,Object>();q.put("actor",uid(a));q.put("root",a.is_superuser());q.put("includeMembers",a.permissions().contains("sop:read")&&a.permissions().contains("project:read"));q.put("projectId",null);if(keyword!=null&&keyword.trim().length()>128)throw bad("搜索内容过长");q.put("keyword",keyword==null||keyword.isBlank()?null:keyword.trim());return sops.publishedOptions(q).stream().map(this::view).toList();}
    public Map<String,Object> save(String token,long pid,Long sid,Long bid,Binding r){
        var initial=actor(token);var before=sops.document(r.sop_id());if(before==null)throw missing();
        var a=writeActor(token,initial);var t=lockProjects(pid,sid,number(before,"managing_project_id"));requireManage(a,t);version(t.entity,r.target_version());
        var d=sops.lockDocument(r.sop_id());if(d==null||number(d,"managing_project_id")!=number(before,"managing_project_id"))throw missing();
        var source=projects.project(number(d,"managing_project_id"));if(source==null)throw missing();sourceRead(a,source);
        var rev=sops.revision(r.sop_id(),r.revision_id());if(rev==null)throw missing();if(value(rev,"status")!=1)throw conflict("只能关联已发布且未停用的版本");
        var q=query(pid,sid);q.put("sopId",r.sop_id());q.put("revisionId",r.revision_id());q.put("actor",uid(a));
        if(bid==null){if(db.bySop(q)!=null)throw conflict("已有该SOP关联，请在原关联中切换版本或重新关联");var rows=db.list(q);if(rows.size()>=1000)throw bad("同一范围最多关联1000份SOP");q.put("position",rows.stream().mapToInt(row->value(row,"sort_order")).max().orElse(0)+1);db.insert(q);}
        else{q.put("id",bid);var existing=required(q);if(number(existing,"sop_id")!=r.sop_id())throw bad("关联的SOP不可修改，请另建关联");if(r.version()==null)throw bad("缺少关联版本");version(existing,r.version());q.put("version",r.version());changed(db.update(q));}
        bump(t,pid,sid,a);return listing(a,pid,sid,target(pid,sid,false));
    }
    public Map<String,Object> unbind(String token,long pid,Long sid,long bid,BindingVersion r){
        var initial=actor(token);var q=query(pid,sid);q.put("id",bid);var before=required(q);var doc=sops.document(number(before,"sop_id"));if(doc==null)throw missing();
        var a=writeActor(token,initial);var t=lockProjects(pid,sid,number(doc,"managing_project_id"));requireManage(a,t);version(t.entity,r.target_version());
        if(sops.lockDocument(number(doc,"id"))==null)throw missing();var row=required(q);version(row,r.version());q.put("version",r.version());q.put("actor",uid(a));changed(db.unbind(q));bump(t,pid,sid,a);return listing(a,pid,sid,target(pid,sid,false));
    }
    public Map<String,Object> sort(String token,long pid,Long sid,BindingSort r){
        var a=writeActor(token,actor(token));var t=target(pid,sid,true);requireManage(a,t);version(t.entity,r.target_version());var q=query(pid,sid);
        var expected=db.list(q).stream().filter(b->value(b,"status")==1).map(b->number(b,"id")).toList();if(new HashSet<>(r.ids()).size()!=r.ids().size()||!new HashSet<>(r.ids()).equals(new HashSet<>(expected)))throw bad("排序需包含全部有效关联且不能重复");
        q.put("actor",uid(a));for(int i=0;i<r.ids().size();i++){q.put("id",r.ids().get(i));q.put("position",i+1);changed(db.sort(q));}bump(t,pid,sid,a);return listing(a,pid,sid,target(pid,sid,false));
    }
    @Transactional(readOnly=true)
    public Map<String,Object> preview(String token,long pid,Long sid,long bid){var a=actor(token);var t=target(pid,sid,false);read(a,t.project);var row=readableBinding(pid,sid,bid);var v=content.boundRevision(number(row,"sop_id"),number(row,"revision_id"));v.put("sop_code",sops.document(number(row,"sop_id")).get("code"));return v;}
    @Transactional(readOnly=true)
    public Map<String,Object> image(String token,long pid,Long sid,long bid,long fid){var a=actor(token);var t=target(pid,sid,false);read(a,t.project);var row=readableBinding(pid,sid,bid);content.boundRevision(number(row,"sop_id"),number(row,"revision_id"));return content.imageForRevision(number(row,"revision_id"),fid);}
    private Map<String,Object> readableBinding(long pid,Long sid,long bid){var q=query(pid,sid);q.put("id",bid);var b=required(q);if(value(b,"status")!=1||sops.document(number(b,"sop_id"))==null)throw missing();return b;}
    private Map<String,Object> listing(UserInfo a,long pid,Long sid,Target t){return Map.of("items",db.list(query(pid,sid)).stream().map(this::view).toList(),"target_version",value(t.entity,"version"),"can_manage",canManage(a,t));}
    private Target lockProjects(long pid,Long sid,long source){var ids=new TreeSet<Long>(List.of(pid,source));Map<String,Object> p=null;for(long id:ids){var row=projects.lockProject(id);if(row==null)throw missing();if(id==pid)p=row;}return entity(p,pid,sid);}
    private Target target(long pid,Long sid,boolean lock){if(pid<=0)throw bad("项目ID不正确");var p=lock?projects.lockProject(pid):projects.project(pid);if(p==null)throw missing();return entity(p,pid,sid);}
    private Target entity(Map<String,Object> p,long pid,Long sid){if(sid==null)return new Target(p,p);if(sid<=0)throw bad("子项目ID不正确");var sub=projects.sub(pid,sid);if(sub==null)throw missing();return new Target(p,sub);}
    private boolean manage(UserInfo a,Map<String,Object> p){return a.is_superuser()||uid(a)==number(p,"owner_user_id");}
    private boolean canManage(UserInfo a,Target t){return value(t.project,"status")<2&&value(t.entity,"status")<2&&(manage(a,t.project)||a.permissions().contains("sop:bind")&&a.permissions().contains("project:read")&&Integer.valueOf(1).equals(projects.member(number(t.project,"id"),uid(a))));}
    private void requireManage(UserInfo a,Target t){if(value(t.project,"status")>=2||value(t.entity,"status")>=2)throw conflict("已完成或归档范围的SOP关联只读");if(!canManage(a,t))throw denied();}
    private void read(UserInfo a,Map<String,Object> p){if(!manage(a,p)&&(projects.member(number(p,"id"),uid(a))==null||!a.permissions().contains("project:read")))throw denied();}
    private void sourceRead(UserInfo a,Map<String,Object> p){read(a,p);if(!manage(a,p)&&!a.permissions().contains("sop:read"))throw denied();}
    private UserInfo actor(String token){var a=auth.authenticate(token);if(a.must_change_password())throw denied();return a;}
    private UserInfo writeActor(String token,UserInfo a){users.lockById(uid(a));auth.requireCurrentSession(token,uid(a));return actor(token);}
    private void bump(Target t,long pid,Long sid,UserInfo a){var d=new HashMap<String,Object>();d.put("actor",uid(a));d.put("id",pid);d.put("version",value(t.project,"version"));changed(projects.bump(d));if(sid!=null){d.put("id",sid);d.put("projectId",pid);d.put("version",value(t.entity,"version"));changed(projects.bumpSub(d));}}
    private Map<String,Object> query(long pid,Long sid){var q=new HashMap<String,Object>();q.put("projectId",pid);q.put("subId",sid);q.put("targetId",sid==null?pid:sid);return q;}
    private Map<String,Object> required(Map<String,Object> q){var b=db.get(q);if(b==null)throw missing();return b;}
    private Map<String,Object> view(Map<String,Object> row){var v=new HashMap<String,Object>();row.forEach((k,x)->{if(x!=null&&(k.equals("id")||k.endsWith("_id")||k.endsWith("_by")))x=x.toString();if(x instanceof LocalDateTime t)x=t.toInstant(ZoneOffset.UTC).toString();if(x instanceof java.sql.Timestamp t)x=t.toLocalDateTime().toInstant(ZoneOffset.UTC).toString();v.put(k,x);});return v;}
    private void version(Map<String,Object> m,int version){if(value(m,"version")!=version)throw conflict("数据已变化，请刷新后重试");}
    private long uid(UserInfo a){return Long.parseLong(a.id());}private long number(Map<String,Object> m,String k){return ((Number)m.get(k)).longValue();}private int value(Map<String,Object> m,String k){return ((Number)m.get(k)).intValue();}
    private void changed(int n){if(n!=1)throw conflict("数据已变化，请刷新后重试");}
    private BusinessException bad(String m){return new BusinessException(HttpStatus.BAD_REQUEST,m);}private BusinessException conflict(String m){return new BusinessException(HttpStatus.CONFLICT,m);}private BusinessException denied(){return new BusinessException(HttpStatus.FORBIDDEN,"无此SOP关联的操作权限");}private BusinessException missing(){return new BusinessException(HttpStatus.NOT_FOUND,"项目、子项目、SOP或关联不存在");}
}
