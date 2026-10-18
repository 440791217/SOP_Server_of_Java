package org.jsut.modules.resource.controller;
import org.jsut.modules.resource.service.TaskService;
import org.jsut.modules.resource.dto.ResourceRequests.Task;
import org.jsut.modules.user.service.AuthService;
import org.jsut.modules.sop.controller.SopImageResponse;
import org.jsut.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.*;
@RestController @RequestMapping("/project/{pid:[0-9]+}/subproject/{sid:[0-9]+}/task") @Tag(name="子项目SOP任务") @SecurityRequirement(name="bearerAuth")
public class TaskController {
 private final TaskService service;public TaskController(TaskService service){this.service=service;}
 private String token(HttpServletRequest r){return AuthService.tokenFrom(r.getHeader("Authorization"));}
 @GetMapping("/owner/options") @Operation(summary="查询任务负责人人选，仅当前项目有效编辑成员") public ApiResponse<List<Map<String,Object>>> owners(HttpServletRequest r,@PathVariable long pid,@PathVariable long sid,@RequestParam(required=false) String keyword){return ApiResponse.success(service.owners(token(r),pid,sid,keyword));}
 @GetMapping @Operation(summary="查询子项目任务及配置缺项") public ApiResponse<Map<String,Object>> list(HttpServletRequest r,@PathVariable long pid,@PathVariable long sid){return ApiResponse.success(service.list(token(r),pid,sid));}
 @PostMapping @Operation(summary="新建任务，绑定子项目和工位，发布状态由内容自动决定") public ApiResponse<Map<String,Object>> create(HttpServletRequest r,@PathVariable long pid,@PathVariable long sid,@Valid @RequestBody Task body){return ApiResponse.success(service.save(token(r),pid,sid,null,body));}
 @PutMapping("/{id:[0-9]+}") @Operation(summary="编辑任务与显式切换固定标准，校验version并保留版本选择历史") public ApiResponse<Map<String,Object>> edit(HttpServletRequest r,@PathVariable long pid,@PathVariable long sid,@PathVariable long id,@Valid @RequestBody Task body){return ApiResponse.success(service.save(token(r),pid,sid,id,body));}
 @PostMapping("/{id:[0-9]+}/content") @Operation(summary="编写当前任务内容，自动建立独立内容或继续已有草稿，旧内容按任务复制保留") public ApiResponse<Map<String,Object>> content(HttpServletRequest r,@PathVariable long pid,@PathVariable long sid,@PathVariable long id,@Valid @RequestBody org.jsut.modules.resource.dto.ResourceRequests.TaskContent body){return ApiResponse.success(service.editContent(token(r),pid,sid,id,body));}
 @PutMapping("/{id:[0-9]+}/publish") @Operation(summary="发布草稿并用于当前任务，同一事务校验任务版本与工位硬件，失败全部回滚") public ApiResponse<Map<String,Object>> publish(HttpServletRequest r,@PathVariable long pid,@PathVariable long sid,@PathVariable long id,@Valid @RequestBody org.jsut.modules.resource.dto.ResourceRequests.PublishTask body){return ApiResponse.success(service.publishAndUse(token(r),pid,sid,id,body));}
 @DeleteMapping("/{id:[0-9]+}") @Operation(summary="仅超级用户逻辑删除SOP任务，拦截未结束作业并保留执行历史") public ApiResponse<Void> delete(HttpServletRequest r,@PathVariable long pid,@PathVariable long sid,@PathVariable long id,@Valid @RequestBody org.jsut.modules.resource.dto.ResourceRequests.Version body){service.delete(token(r),pid,sid,id,body.version());return ApiResponse.success(null);}
 @GetMapping("/{id:[0-9]+}/preview") @Operation(summary="项目权限范围内查阅任务固定SOP版本") public ApiResponse<Map<String,Object>> preview(HttpServletRequest r,@PathVariable long pid,@PathVariable long sid,@PathVariable long id){return ApiResponse.success(service.preview(token(r),pid,sid,id));}
 @GetMapping("/{id:[0-9]+}/image/{fid:[0-9]+}") @Operation(summary="查阅任务版本图片，核对图片属于当前固定版本") public ResponseEntity<ByteArrayResource> image(HttpServletRequest r,@PathVariable long pid,@PathVariable long sid,@PathVariable long id,@PathVariable long fid){return SopImageResponse.of(service.image(token(r),pid,sid,id,fid));}
}
