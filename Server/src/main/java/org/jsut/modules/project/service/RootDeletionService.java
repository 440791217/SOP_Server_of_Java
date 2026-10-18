package org.jsut.modules.project.service;
import org.jsut.modules.project.mapper.DeletionMapper;
import org.jsut.modules.user.dto.UserInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static org.jsut.modules.resource.service.ResourceSupport.*;
/** Caller authenticates root and holds the actor and parent project write locks. */
@Service @Transactional(propagation=Propagation.MANDATORY)
public class RootDeletionService {
 private final DeletionMapper db;public RootDeletionService(DeletionMapper db){this.db=db;}
 public void remove(UserInfo actor,long pid,Long sid,Long tid,String name){
  if(!actor.is_superuser())throw denied();var q=new HashMap<String,Object>();q.put("pid",pid);q.put("sid",sid);q.put("tid",tid);q.put("actor",uid(actor));q.put("actorName",actor.real_name()==null?actor.username():actor.real_name());q.put("name",name);q.put("action",tid!=null?"TASK_DELETED":sid!=null?"SUBPROJECT_DELETED":"PROJECT_DELETED");
  if(db.active(q)>0)throw conflict("存在进行中或暂停的作业，请先在执行记录中完成或中止，再删除");
  int count=db.tasks(q);if(tid!=null&&count!=1)throw conflict("任务已变化，请刷新后重试");if(sid==null)db.children(q);db.audit(q);
 }
}
