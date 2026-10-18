package org.jsut.modules.sop.service;

import org.jsut.common.BusinessException;
import org.jsut.modules.sop.dto.SopRequests.Visual;
import org.jsut.modules.sop.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class SopVisualService {
    private final SopVisualMapper db;private final SopMapper sops;private final SopImageStorage storage;private final ObjectMapper json;
    public SopVisualService(SopVisualMapper db,SopMapper sops,SopImageStorage storage,ObjectMapper json){this.db=db;this.sops=sops;this.storage=storage;this.json=json;}
    public Map<String,Object> read(long attachment){var row=db.get(attachment);if(row==null)return null;var result=new HashMap<String,Object>(row);result.put("guide_items",decode(row.get("guide_items")));result.put("detection_regions",decode(row.get("detection_regions")));return result;}
    private List<Map<String,Object>> decode(Object raw){String text=raw instanceof byte[] b?new String(b,StandardCharsets.UTF_8):String.valueOf(raw);return json.readValue(text,json.getTypeFactory().constructCollectionType(List.class,Map.class));}
    public void save(Visual r,long fileId,long actor){validate(r);var file=sops.file(fileId);storage.validateStored(file);
        try{var image=ImageIO.read(new ByteArrayInputStream(storage.read(file)));if(image==null||image.getWidth()!=r.image_width()||image.getHeight()!=r.image_height())throw bad("标注参考尺寸与原图不一致");}catch(IOException e){throw bad("图片读取失败");}
        store(r.attachment_id(),r.image_width(),r.image_height(),r.guide_items(),r.detection_regions(),actor);
    }
    private void store(long aid,int width,int height,Object guides,Object regions,long actor){var q=new HashMap<String,Object>();q.put("attachmentId",aid);q.put("width",width);q.put("height",height);q.put("guides",json.writeValueAsString(guides));q.put("regions",json.writeValueAsString(regions));q.put("actor",actor);db.save(q);}
    public void copy(long oldId,long newId,long actor){var r=read(oldId);if(r!=null)store(newId,((Number)r.get("image_width")).intValue(),((Number)r.get("image_height")).intValue(),r.get("guide_items"),r.get("detection_regions"),actor);}
    public void validateStored(long aid){var r=read(aid);if(r==null)return;validate(new Visual(aid,((Number)r.get("schema_version")).intValue(),((Number)r.get("image_width")).intValue(),((Number)r.get("image_height")).intValue(),items(r,"guide_items"),items(r,"detection_regions")));}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> items(Map<String,Object> r,String key){return (List<Map<String,Object>>)r.get(key);}
    private void validate(Visual r){
        if(r.schema_version()!=1||r.image_width()<1||r.image_height()<1||(long)r.image_width()*r.image_height()>16_000_000)throw bad("不支持的标注版本或尺寸");
        var ids=new HashSet<String>();for(var item:r.guide_items())item(item,false,ids);for(var item:r.detection_regions())item(item,true,ids);
    }
    private void item(Map<String,Object> m,boolean detection,Set<String> ids){
        if(m==null)throw bad("标注不能为空");String id=str(m,"id",64,true);if(!id.matches("[A-Za-z0-9_-]+")||!ids.add(id))throw bad("标注ID重复或无效");
        String kind=str(m,"kind",10,true);if(detection?!kind.equals("rect"):!Set.of("rect","arrow","text").contains(kind))throw bad("不支持的标注形状");
        var allowed=new HashSet<>(Set.of("id","kind","name","x","y","width","height","x2","y2","text","color"));
        if(detection)allowed.addAll(Set.of("enabled","detect_type","class_name","confidence","expected_count","expected_text","expected_state"));
        if(!allowed.containsAll(m.keySet()))throw bad("标注含不支持的字段");str(m,"name",128,true);String color=str(m,"color",7,true);if(!color.matches("#[0-9A-Fa-f]{6}"))throw bad("颜色格式不正确");
        double x=number(m,"x",0,1),y=number(m,"y",0,1);
        if(kind.equals("arrow")){double x2=number(m,"x2",0,1),y2=number(m,"y2",0,1);if(Math.abs(x-x2)+Math.abs(y-y2)<0.0001)throw bad("箭头长度不能为零");}
        else{double w=number(m,"width",0.0001,1),h=number(m,"height",0.0001,1);if(x+w>1.000001||y+h>1.000001)throw bad("标注不能超出原图");}
        str(m,"text",1000,kind.equals("text"));
        if(detection){if(!(m.get("enabled") instanceof Boolean))throw bad("缺少区域启用状态");String type=str(m,"detect_type",10,true);if(!Set.of("object","count","status","ocr").contains(type))throw bad("检测类型不支持");number(m,"confidence",0,100);if(type.equals("ocr"))str(m,"expected_text",1000,true);else str(m,"class_name",128,true);if(type.equals("count")){double count=number(m,"expected_count",1,100000);if(count!=Math.rint(count))throw bad("期望数量必须为整数");}if(type.equals("status"))str(m,"expected_state",128,true);}
    }
    private double number(Map<String,Object> m,String k,double min,double max){if(!(m.get(k) instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<min||n.doubleValue()>max)throw bad("标注数值不正确："+k);return n.doubleValue();}
    private String str(Map<String,Object> m,String k,int max,boolean required){Object o=m.get(k);if(o==null&&!required)return "";if(!(o instanceof String s)||s.length()>max||required&&s.isBlank())throw bad("标注文字不正确："+k);return s;}
    private BusinessException bad(String message){return new BusinessException(HttpStatus.BAD_REQUEST,message);}
}
