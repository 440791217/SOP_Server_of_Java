package org.jsut.modules.execution.controller;
import org.jsut.modules.execution.service.ExecutionService;
import org.jsut.modules.execution.dto.ExecutionRequests.*;
import org.jsut.modules.user.service.AuthService;
import org.jsut.modules.sop.controller.SopImageResponse;
import org.jsut.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.LocalDate;
import java.util.*;
@RestController @RequestMapping("/execution") @Tag(name="现场SOP执行") @SecurityRequirement(name="bearerAuth")
public class ExecutionController {
 private final ExecutionService service;public ExecutionController(ExecutionService service){this.service=service;}
 private String token(HttpServletRequest r){return AuthService.tokenFrom(r.getHeader("Authorization"));}
 @GetMapping @Operation(summary="分页查询权限范围内的执行记录，支持工位、项目、操作人、产品码、状态和开工日期")
 public ApiResponse<Map<String,Object>> list(HttpServletRequest r,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int limit,@RequestParam(required=false) Long station_id,@RequestParam(required=false) Long project_id,@RequestParam(required=false) Long operator_id,@RequestParam(required=false) String product_code,@RequestParam(required=false) Integer status,@RequestParam(defaultValue="false") boolean active,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,@RequestParam(required=false) String project_name,@RequestParam(required=false) String station_name,@RequestParam(required=false) String operator_name){return ApiResponse.success(service.list(token(r),page,limit,station_id,project_id,operator_id,product_code,status,active,from,to,project_name,station_name,operator_name));}
 @PostMapping @Operation(summary="扫码开工，固定版本和步骤快照；request_key支持同一次开工请求重试，工位和任务产品码防重复")
 public ApiResponse<Map<String,Object>> start(HttpServletRequest r,@Valid @RequestBody Start body){return ApiResponse.success(service.start(token(r),body));}
 @GetMapping("/{id:[0-9]+}") @Operation(summary="查看执行快照、步骤确认及事件记录") public ApiResponse<Map<String,Object>> detail(HttpServletRequest r,@PathVariable long id){return ApiResponse.success(service.detail(token(r),id));}
 @PutMapping("/{id:[0-9]+}/step/{position:[0-9]+}/confirm") @Operation(summary="由原操作人按顺序确认步骤，校验version") public ApiResponse<Map<String,Object>> confirm(HttpServletRequest r,@PathVariable long id,@PathVariable int position,@Valid @RequestBody Action body){return ApiResponse.success(service.action(token(r),id,"confirm",position,body));}
 @PutMapping("/{id:[0-9]+}/{action:pause|resume|complete|abort}") @Operation(summary="原操作人暂停、恢复、完成或中止；暂停中止必填reason，所有写入校验version") public ApiResponse<Map<String,Object>> action(HttpServletRequest r,@PathVariable long id,@PathVariable String action,@Valid @RequestBody Action body){return ApiResponse.success(service.action(token(r),id,action,null,body));}
 @GetMapping("/{id:[0-9]+}/image/{fid:[0-9]+}") @Operation(summary="按执行记录固定版本授权读取历史图片") public ResponseEntity<ByteArrayResource> image(HttpServletRequest r,@PathVariable long id,@PathVariable long fid){return SopImageResponse.of(service.image(token(r),id,fid));}
}
