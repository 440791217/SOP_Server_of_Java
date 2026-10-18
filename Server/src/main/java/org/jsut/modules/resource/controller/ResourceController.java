package org.jsut.modules.resource.controller;
import org.jsut.modules.resource.service.ResourceService;
import org.jsut.modules.resource.service.CameraMediaService;
import org.springframework.http.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.jsut.modules.resource.dto.ResourceRequests.*;
import org.jsut.modules.user.service.AuthService;
import org.jsut.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.*;
@RestController @RequestMapping("/resource") @Tag(name="工位与工具设备") @SecurityRequirement(name="bearerAuth")
public class ResourceController {
 private final ResourceService service;private final CameraMediaService media;public ResourceController(ResourceService service,CameraMediaService media){this.service=service;this.media=media;}
 private String token(HttpServletRequest r){return AuthService.tokenFrom(r.getHeader("Authorization"));}
 @GetMapping("/owner/options") @Operation(summary="查询资源负责人人选") public ApiResponse<List<Map<String,Object>>> owners(HttpServletRequest r,@RequestParam(required=false) String keyword){return ApiResponse.success(service.owners(token(r),keyword));}
 @GetMapping("/{kind:location|station|model|device}") @Operation(summary="分页查询工位、规格或设备")
 public ApiResponse<Map<String,Object>> list(HttpServletRequest r,@PathVariable String kind,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int limit,@RequestParam(required=false) String keyword,@RequestParam(required=false) Integer status){return ApiResponse.success(service.list(token(r),kind,page,limit,keyword,status));}
 @GetMapping("/{kind:location|station|model|device}/{id:[0-9]+}") @Operation(summary="资源详情，工位包含绑定设备")
 public ApiResponse<Map<String,Object>> get(HttpServletRequest r,@PathVariable String kind,@PathVariable long id){return ApiResponse.success(service.detail(token(r),kind,id));}
 @PostMapping("/{kind:location|station|model|device}") @Operation(summary="新建资源，编号由前端生成")
 public ApiResponse<Map<String,Object>> create(HttpServletRequest r,@PathVariable String kind,@Valid @RequestBody Item body){return ApiResponse.success(service.save(token(r),kind,null,body));}
 @PutMapping("/{kind:location|station|model|device}/{id:[0-9]+}") @Operation(summary="编辑资源，version用于并发保护")
 public ApiResponse<Map<String,Object>> edit(HttpServletRequest r,@PathVariable String kind,@PathVariable long id,@Valid @RequestBody Item body){return ApiResponse.success(service.save(token(r),kind,id,body));}
 @DeleteMapping("/{kind:location|station|model|device}/{id:[0-9]+}") @Operation(summary="逻辑删除无引用资源")
 public ApiResponse<Void> delete(HttpServletRequest r,@PathVariable String kind,@PathVariable long id,@Valid @RequestBody Version body){service.delete(token(r),kind,id,body.version());return ApiResponse.success(null);}
 @PutMapping("/device/{id:[0-9]+}/connection") @Operation(summary="保存设备连接配置，校验设备version")
 public ApiResponse<Map<String,Object>> connection(HttpServletRequest r,@PathVariable long id,@Valid @RequestBody Connection body){return ApiResponse.success(service.connection(token(r),id,body));}
 @PostMapping("/device/connection/test") @Operation(summary="保存前测试RTSP连接，返回实际截图")
 public ResponseEntity<byte[]> test(HttpServletRequest r,@Valid @RequestBody ConnectionSettings body){return image(media.snapshot(service.testCameraAddress(token(r),body)));}
 @GetMapping("/device/{id:[0-9]+}/preview") @Operation(summary="测试已保存的摄像头连接，返回实际截图")
 public ResponseEntity<byte[]> preview(HttpServletRequest r,@PathVariable long id){return image(media.snapshot(service.cameraAddress(token(r),id,true)));}
 @PostMapping("/device/{id:[0-9]+}/snapshot") @Operation(summary="采集摄像头原始尺寸截图")
 public ResponseEntity<byte[]> snapshot(HttpServletRequest r,@PathVariable long id){return image(media.snapshot(service.cameraAddress(token(r),id,false)));}
 @GetMapping("/device/{id:[0-9]+}/stream") @Operation(summary="摄像头连续JPEG预览，单次最长45秒，客户端续接")
 public ResponseEntity<StreamingResponseBody> stream(HttpServletRequest r,@PathVariable long id){
  var feed=media.preview(service.cameraAddress(token(r),id,false));
  return ResponseEntity.ok().header("Cache-Control","no-store").header("X-Accel-Buffering","no").contentType(MediaType.parseMediaType("multipart/x-mixed-replace; boundary=camera")).body(feed::writeTo);
 }
 private ResponseEntity<byte[]> image(byte[] data){return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).header("Cache-Control","no-store").body(data);}
}
