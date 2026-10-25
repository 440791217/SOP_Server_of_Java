package org.jsut.modules.execution.service;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.*;
import javax.imageio.ImageIO;
import org.jsut.common.storage.StoragePaths;
import org.jsut.modules.execution.dto.ExecutionRequests.*;
import org.jsut.modules.sop.service.SopImageStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import static org.jsut.modules.resource.service.ResourceSupport.*;

@Service
public class ExecutionEvidenceStorage {
 private final SopImageStorage files;private final StoragePaths paths;private final ObjectMapper json;
 public ExecutionEvidenceStorage(SopImageStorage files,StoragePaths paths,ObjectMapper json){this.files=files;this.paths=paths;this.json=json;}
 @SuppressWarnings("unchecked") public Map<String,Object> save(Map<String,Object> execution,int position,Map<String,Object> step,Evidence r,MultipartFile file){
  Map<String,Object> config=(Map<String,Object>)step.get("detection");
  if(config==null||config.get("camera_id")==null||!String.valueOf(config.get("camera_id")).equals(String.valueOf(r.camera_id())))throw bad("照片摄像头与开工时的步骤配置不一致");
  boolean enabled=Boolean.TRUE.equals(config.get("enabled"));
  if(enabled&&!r.automatic())throw bad("视觉检测步骤必须提交通过检测的现场照片");
  for(var detection:r.detections()){var box=detection.bbox_xyxy();if(box.stream().anyMatch(v->!Double.isFinite(v))||box.get(0)<0||box.get(1)<0||box.get(2)>r.image_width()||box.get(3)>r.image_height()||box.get(2)<=box.get(0)||box.get(3)<=box.get(1))throw bad("现场检测框超出图片或尺寸无效");}
  if(r.automatic())validateAutomatic(step,config,r);
  String folder=paths.execution(n(execution,"project_id"),n(execution,"subproject_id"),n(execution,"task_id"),LocalDate.parse(execution.get("started_at").toString().substring(0,10)),String.valueOf(execution.get("product_code")),n(execution,"id"));
  var stored=files.save(file,folder,String.format(Locale.ROOT,"step-%03d_",position));
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)files.rollbackDelete(stored.key());}});
  try{var image=ImageIO.read(new ByteArrayInputStream(stored.bytes()));if(image.getWidth()!=r.image_width()||image.getHeight()!=r.image_height())throw bad("现场照片尺寸与检测帧不一致");}catch(java.io.IOException e){throw bad("现场照片无法读取");}
  var data=new HashMap<String,Object>();data.put("execution_id",execution.get("id"));data.put("position",position);data.put("request_key",r.request_key());data.put("storage_key",stored.key());data.put("original_name",stored.name());data.put("mime_type",stored.mime());data.put("byte_size",stored.bytes().length);data.put("sha256",stored.sha());data.put("camera_id",r.camera_id());data.put("model_id",r.model_id());data.put("frame_id",r.frame_id());data.put("image_width",r.image_width());data.put("image_height",r.image_height());data.put("captured_at",java.sql.Timestamp.from(r.captured_at()));data.put("automatic",r.automatic());data.put("detection_result",json.writeValueAsString(r.detections()));return data;
 }
 @SuppressWarnings("unchecked") private void validateAutomatic(Map<String,Object> step,Map<String,Object> config,Evidence r){
  if(!Boolean.TRUE.equals(config.get("enabled"))||!Boolean.TRUE.equals(config.get("confirmed"))||r.model_id()==null||r.model_id().isBlank()||r.frame_id()==null||r.frame_id().isBlank())throw bad("检测留存信息不完整");
  Map<String,Object> image=(Map<String,Object>)step.get("image");
  if(image==null&&step.get("attachments") instanceof List<?> old)image=(Map<String,Object>)old.stream().filter(a->String.valueOf(((Map<?,?>)a).get("id")).equals(String.valueOf(config.get("attachment_id")))).findFirst().orElse(null);
  if(image==null||!String.valueOf(image.get("id")).equals(String.valueOf(config.get("attachment_id"))))throw bad("步骤缺少检测基准图");
  var visual=(Map<String,Object>)image.get("visual");if(visual==null||!(visual.get("detection_regions") instanceof List<?> all))throw bad("步骤缺少检测区域");
  var regions=all.stream().map(v->(Map<String,Object>)v).filter(v->!Boolean.FALSE.equals(v.get("enabled"))).toList();if(regions.isEmpty())throw bad("步骤缺少参与检测的区域");
  for(var region:regions){String type=String.valueOf(region.get("detect_type"));if(!List.of("object","count").contains(type))throw bad("当前模型不支持此检测类型");
   long count=r.detections().stream().filter(d->matches(region,d,r)).count();boolean ok=type.equals("count")?count==((Number)region.get("expected_count")).longValue():count>0;
   if(!ok)throw bad("当前步骤检测条件未全部满足，未保存确认结果");
  }
 }
 private boolean matches(Map<String,Object> region,Detection item,Evidence r){
  if(!String.valueOf(region.get("class_name")).trim().equalsIgnoreCase(item.class_name().trim())||!Double.isFinite(item.confidence())||item.confidence()<value(region,"confidence")/100)return false;
  var b=item.bbox_xyxy();if(b.size()!=4||b.stream().anyMatch(v->v==null||!Double.isFinite(v))||b.get(2)<=b.get(0)||b.get(3)<=b.get(1))return false;
  double x=(b.get(0)+b.get(2))/2/r.image_width(),y=(b.get(1)+b.get(3))/2/r.image_height();
  return x>=value(region,"x")&&x<=value(region,"x")+value(region,"width")&&y>=value(region,"y")&&y<=value(region,"y")+value(region,"height");
 }
 private double value(Map<String,Object> r,String key){return ((Number)r.get(key)).doubleValue();}
 public Map<String,Object> image(Map<String,Object> row){return Map.of("bytes",files.read(row),"mime",row.get("mime_type"),"name",row.get("original_name"));}
}
