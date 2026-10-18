package org.jsut.modules.resource.service;
import org.jsut.modules.resource.mapper.ResourceMapper;
import org.jsut.modules.resource.dto.ResourceRequests.Requirements;
import org.springframework.stereotype.Service;
import java.util.*;
import static org.jsut.modules.resource.service.ResourceSupport.*;
@Service public class StepResourceService {
 private final ResourceMapper db;
 public StepResourceService(ResourceMapper db){this.db=db;}
 public List<Map<String,Object>> read(long sid){return db.requirements(sid).stream().map(ResourceSupport::view).toList();}
 public void save(long sid,Requirements r){
  if(r==null)return;
  var old=db.requirements(sid);var ids=new HashSet<Long>();var rows=new ArrayList<Map<String,Object>>();
  for(var item:r.items()){
   if(!ids.add(item.model_id()))throw bad("同一步骤的规格不能重复");
   var model=db.lock(q("model",item.model_id()));if(model==null||n(model,"status")!=0)throw conflict("工具设备规格不存在或已停用");
   var row=new HashMap<>(old.stream().filter(x->n(x,"model_id")==item.model_id()).findFirst().orElse(model));
   row.put("step_id",sid);row.put("model_id",item.model_id());row.put("quantity",item.quantity());row.put("description",item.description());row.put("sort_order",rows.size()+1);rows.add(row);
  }
  db.clearRequirements(sid);for(var row:rows)db.insertRequirement(row);db.environment(sid,r.workplace_requirements());
 }
 public void copy(long source,long target,String environment){for(var item:db.requirements(source)){var row=new HashMap<>(item);row.put("step_id",target);db.insertRequirement(row);}db.environment(target,environment);}
 public void validate(long sid){for(var item:db.requirements(sid)){var model=db.lock(q("model",n(item,"model_id")));if(model==null||n(model,"status")!=0)throw conflict("步骤引用的工具设备规格已停用，请调整草稿要求");}}
}
