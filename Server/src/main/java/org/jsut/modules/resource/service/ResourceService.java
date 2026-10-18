package org.jsut.modules.resource.service;
import org.jsut.modules.resource.dto.ResourceRequests.*;
import org.jsut.modules.resource.mapper.ResourceMapper;
import org.jsut.modules.user.service.AuthService;
import org.jsut.modules.user.mapper.AuthMapper;
import org.jsut.modules.user.dto.UserInfo;
import org.jsut.common.BusinessCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.dao.DuplicateKeyException;
import java.util.*;
import static org.jsut.modules.resource.service.ResourceSupport.*;
@Service @Transactional(isolation=Isolation.READ_COMMITTED)
public class ResourceService {
 private final org.jsut.modules.project.mapper.ProjectMapper projects;
 private final ResourceMapper db;private final AuthService auth;private final AuthMapper users;
 public ResourceService(ResourceMapper db,AuthService auth,AuthMapper users,org.jsut.modules.project.mapper.ProjectMapper projects){this.projects=projects;this.db=db;this.auth=auth;this.users=users;}
 private UserInfo actor(String token,boolean write){var a=auth.authenticate(token);if(a.must_change_password()||!has(a,write?"resource:manage":"resource:read"))throw denied();if(write){users.lockById(uid(a));auth.requireCurrentSession(token,uid(a));a=auth.authenticate(token);if(a.must_change_password()||!has(a,"resource:manage"))throw denied();}return a;}
 @Transactional(readOnly=true) public List<Map<String,Object>> owners(String token,String keyword){actor(token,false);if(keyword!=null&&keyword.length()>128)throw bad("搜索内容过长");var q=new HashMap<String,Object>();q.put("projectId",null);q.put("keyword",keyword==null||keyword.isBlank()?null:keyword.trim());return projects.users(q).stream().map(ResourceSupport::view).toList();}
 private void kind(String k){if(!Set.of("location","station","model","device").contains(k))throw bad("资源类型不正确");}
 @Transactional(readOnly=true) public Map<String,Object> list(String token,String kind,int page,int limit,String keyword,Integer status){var a=actor(token,false);kind(kind);if(page<1||limit<1||limit>100||keyword!=null&&keyword.length()>128||status!=null&&(status<0||status>2))throw bad("查询参数不正确");var q=q(kind,0);q.put("offset",(long)(page-1)*limit);q.put("limit",limit);q.put("keyword",keyword==null||keyword.isBlank()?null:keyword.trim());q.put("status",status);return Map.of("items",db.list(q).stream().map(ResourceSupport::view).toList(),"total",db.count(q),"can_manage",has(a,"resource:manage"));}
 @Transactional(readOnly=true) public Map<String,Object> detail(String token,String kind,long id){var a=actor(token,false);kind(kind);var row=db.get(q(kind,id));if(row==null)throw missing();var out=view(row);if(kind.equals("station"))out.put("devices",db.devices(id).stream().map(ResourceSupport::view).toList());if(kind.equals("device")){var c=db.connection(id);if(c!=null&&c.get("rtsp_url")!=null&&!has(a,"resource:manage")){c=new HashMap<>(c);c.put("rtsp_url",RtspAddress.parse(c.get("rtsp_url").toString()).displayUrl());}out.put("connection",c==null?null:view(c));out.put("connection_state","未验证");}return out;}
 public Map<String,Object> save(String token,String kind,Long id,Item r){
  kind(kind);var a=actor(token,true);if(r.owner_user_id()!=null){var u=users.lockById(r.owner_user_id());if(u==null||u.deletedAt()!=null||!Integer.valueOf(1).equals(u.status()))throw bad("负责人不存在或已停用");}
  var d=q(kind,id==null?0:id);var old=id==null?null:db.lock(d);if(id!=null){if(old==null)throw missing();version(old,r.version());}
  d.put("name",r.name().trim());if(r.name().isBlank()||r.name().trim().length()>(kind.equals("location")?255:128))throw bad("名称不能为空或超过长度限制");d.put("status",r.status());d.put("description",r.description());d.put("actor",uid(a));d.put("version",r.version());
  d.put("owner_user_id",r.owner_user_id());d.put("location",r.location());d.put("type_code",r.type_code());d.put("model",r.model());d.put("specification",r.specification());d.put("unit",r.unit());d.put("model_id",r.model_id());d.put("station_id",r.station_id());d.put("serial_number",r.serial_number());
  if(kind.equals("station")){
   if(r.location_id()==null)throw bad("请选择工位位置");
   var location=db.lock(q("location",r.location_id()));if(location==null||n(location,"status")!=0)throw bad("所选位置不存在或已停用");
   d.put("location_id",r.location_id());d.put("location",location.get("name"));d.put("ip_address",StationIp.normalize(r.ip_address()));
  }
  if(kind.equals("model")&&(r.type_code()==null||!r.type_code().matches("[a-z][a-z0-9_]{0,63}")||r.unit()==null||r.unit().isBlank()))throw bad("请填写设备类型代码和单位");
  if(kind.equals("device")){
   if(r.model_id()==null)throw bad("请选择规格");var m=db.lock(q("model",r.model_id()));if(m==null||n(m,"status")!=0)throw bad("规格不可用");
   if(r.station_id()!=null){var s=db.lock(q("station",r.station_id()));if(s==null||n(s,"status")!=0)throw bad("工位不可用");}
  }
  if(kind.equals("station")&&r.status()!=0&&db.references(d)>0)throw conflict("请先移出设备并调整该工位的有效任务，再维护或停用工位");
  if(id==null){d.put("code",BusinessCode.validate(r.code(),kind.equals("location")?"LOC":kind.equals("station")?"STA":kind.equals("model")?"TMD":"TOL"));try{db.insert(d);}catch(DuplicateKeyException e){throw conflict("编号或工位IP已存在，请确认是否已经创建成功或IP被其他工位绑定");}}
  else {if(r.code()!=null&&!r.code().equals(old.get("code")))throw bad("编号不可修改");try{changed(db.update(d));}catch(DuplicateKeyException e){throw conflict("工位IP已被其他工位绑定，请检查后重试");}}
  if(kind.equals("device")&&r.connection()!=null)replaceConnection(n(d,"id"),r.connection(),uid(a));
  return detail(token,kind,n(d,"id"));
 }
 public void delete(String token,String kind,long id,int v){kind(kind);var a=actor(token,true);var q=q(kind,id);var row=db.lock(q);if(row==null)throw missing();version(row,v);if(db.references(q)>0)throw conflict("已有业务引用，请保留记录并调整关联");q.put("actor",uid(a));q.put("version",v);changed(db.delete(q));}
 public Map<String,Object> connection(String token,long id,Connection r){
  var a=actor(token,true);var d=db.lock(q("device",id));if(d==null)throw missing();version(d,r.device_version());
  replaceConnection(id,r.settings(),uid(a));
  var update=new HashMap<>(d);update.put("kind","device");update.put("actor",uid(a));changed(db.update(update));return detail(token,"device",id);
 }
 /** Reused by atomic device create/edit and the standalone connection endpoint. */
 private void replaceConnection(long id,ConnectionSettings r,long actor){
  if(!Set.of("network","usb","serial","none").contains(r.transport()))throw bad("连接方式不支持");
  var allowed=Map.of("network",Set.of("rtsp","tcp","http","vendor"),"usb",Set.of("hid_keyboard","serial","uvc","vendor"),"serial",Set.of("serial","vendor"),"none",Set.of("none"));
  if(!allowed.get(r.transport()).contains(r.protocol()))throw bad("协议与连接方式不匹配");
  boolean network=r.transport().equals("network"),local=Set.of("usb","serial").contains(r.transport());
  String host=r.host()==null?null:r.host().trim(); Integer port=r.port(); String path=r.path(); String rtspUrl=null;
  if(network&&r.protocol().equals("rtsp")){var address=r.rtsp_url()!=null?RtspAddress.parse(r.rtsp_url().trim()):RtspAddress.parse("rtsp://"+(host!=null&&host.contains(":")?"["+host+"]":host)+":"+port+(path==null?"":path));host=address.host();port=address.port();path=address.path();rtspUrl=r.rtsp_url()==null?address.url():r.rtsp_url().trim();}
  if(network){
   if(host==null||!host.matches("[A-Za-z0-9:.\\-]+")||port==null)throw bad("网络设备需填写IP/主机和端口");
   if(host.contains(":")||host.matches("[0-9.]+")){try{host=StationIp.normalize(host);}catch(org.jsut.common.BusinessException e){throw bad("设备IP地址格式不正确");}}
   if(!r.protocol().equals("rtsp")&&r.path()!=null&&(r.path().contains("@")||r.path().contains("://")||r.path().contains("?")||!r.path().isBlank()&&!r.path().startsWith("/")))throw bad("路径需以/开头，凭据和查询参数不得写入路径");
  }
  if(local&&(r.host_key()==null||r.host_key().isBlank()||r.device_key()==null||r.device_key().isBlank()))throw bad("请填写接入电脑和设备标识");
  var q=new HashMap<String,Object>();q.put("id",id);q.put("transport",r.transport());q.put("protocol",r.protocol());q.put("host",network?host:null);q.put("port",network?port:null);q.put("path",network?path:null);q.put("rtsp_url",rtspUrl);q.put("host_key",local?r.host_key().trim():null);q.put("device_key",local?r.device_key().trim():null);q.put("enabled",r.enabled()?1:0);q.put("actor",actor);db.removeConnection(id);db.insertConnection(q);
  if(rtspUrl!=null){var saved=db.connection(id);if(saved==null||!rtspUrl.equals(saved.get("rtsp_url")))throw new org.jsut.common.BusinessException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,"RTSP地址未写入数据库，请检查后端数据库映射并重启后端");}
 }
 @Transactional(readOnly=true) public String cameraAddress(String token,long id,boolean manage){
  var a=auth.authenticate(token);if(a.must_change_password()||!has(a,manage?"resource:manage":"resource:use"))throw denied();
  var d=db.get(q("device",id));if(d==null)throw missing();
  var m=db.get(q("model",n(d,"model_id")));if(m==null||!"camera".equals(m.get("type_code")))throw bad("所选设备不是摄像头");
  if(n(d,"status")!=0||n(m,"status")!=0)throw bad("摄像头或规格已维护/停用");
  if(d.get("station_id")!=null){var station=db.get(q("station",n(d,"station_id")));if(station==null||n(station,"status")!=0)throw bad("摄像头所属工位不可用");}
  var c=db.connection(id);if(c==null||n(c,"enabled")!=1)throw bad("请先配置并启用摄像头连接");
  return rtsp(c);
 }
 public String testCameraAddress(String token,ConnectionSettings settings){actor(token,true);if(!Boolean.TRUE.equals(settings.enabled()))throw bad("请先启用连接");var c=new HashMap<String,Object>();c.put("transport",settings.transport());c.put("protocol",settings.protocol());c.put("host",settings.host());c.put("port",settings.port());c.put("path",settings.path());c.put("rtsp_url",settings.rtsp_url());return rtsp(c);}
 private String rtsp(Map<String,Object> c){
  if(!"network".equals(c.get("transport"))||!"rtsp".equals(c.get("protocol")))throw bad("当前仅支持网络RTSP摄像头");
  if(c.get("rtsp_url")!=null)return RtspAddress.parse(c.get("rtsp_url").toString().trim()).url();
  var host=String.valueOf(c.get("host"));return RtspAddress.parse("rtsp://"+(host.contains(":")?"["+host+"]":host)+":"+c.get("port")+(c.get("path")==null?"":c.get("path"))).url();
 }
}

