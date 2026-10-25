package org.jsut.modules.project.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.jsut.common.ApiResponse;
import org.jsut.modules.project.dto.ProjectRequests.*;
import org.jsut.modules.project.service.ProjectService;
import org.jsut.modules.user.service.AuthService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/project")
@Tag(name="项目与子项目",description="项目数据范围受成员关系限制；写操作校验version；root可管理全部项目")
@SecurityRequirement(name="bearerAuth")
public class ProjectController {
    private final ProjectService service;
    public ProjectController(ProjectService service){this.service=service;}
    private String token(HttpServletRequest r){return AuthService.tokenFrom(r.getHeader("Authorization"));}
    @GetMapping("/{id:[0-9]+}/station/list")
    public ApiResponse<Map<String,Object>> stations(HttpServletRequest r,@PathVariable long id,@RequestParam(required=false) String keyword,@RequestParam(defaultValue="false") boolean available){return ApiResponse.success(service.stations(token(r),id,keyword,available));}
    @PostMapping("/{id:[0-9]+}/station")
    public ApiResponse<Void> linkStation(HttpServletRequest r,@PathVariable long id,@Valid @RequestBody StationLink body){service.linkStation(token(r),id,body);return ApiResponse.success(null);}
    @DeleteMapping("/{id:[0-9]+}/station/{stationId:[0-9]+}")
    public ApiResponse<Void> unlinkStation(HttpServletRequest r,@PathVariable long id,@PathVariable long stationId,@Valid @RequestBody Version body){service.unlinkStation(token(r),id,stationId,body);return ApiResponse.success(null);}
    @GetMapping("/list") @Operation(summary="分页查询项目",description="page>=1，limit 1至100；keyword匹配编号或名称；status为0至3")
    public ApiResponse<Map<String,Object>> list(HttpServletRequest r,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int limit,@RequestParam(required=false) String keyword,@RequestParam(required=false) Integer status){return ApiResponse.success(service.list(token(r),page,limit,keyword,status));}
    @GetMapping("/owner/options") @Operation(summary="查询新项目负责人人选",description="需要project:create；仅返回正常用户的ID、姓名、账号；keyword搜索，最多50条")
    public ApiResponse<List<Map<String,Object>>> options(HttpServletRequest r,@RequestParam(required=false) String keyword){return ApiResponse.success(service.options(token(r),null,keyword));}
    @GetMapping("/{id:[0-9]+}") @Operation(summary="项目详情")
    public ApiResponse<Map<String,Object>> detail(HttpServletRequest r,@PathVariable long id){return ApiResponse.success(service.detail(token(r),id));}
    @PostMapping @Operation(summary="新建项目",description="初始未开始；事务内加入创建人和负责人为编辑成员；前端提供code：PRJ-12位年月日小时分钟-6位大写十六进制随机码；创建后不可变")
    public ApiResponse<Map<String,Object>> create(HttpServletRequest r,@Valid @RequestBody Create body){return ApiResponse.success(service.create(token(r),body));}
    @PutMapping("/{id:[0-9]+}") @Operation(summary="编辑项目基本信息",description="不接受编号、负责人、状态修改；version冲突409")
    public ApiResponse<Map<String,Object>> edit(HttpServletRequest r,@PathVariable long id,@Valid @RequestBody Edit body){return ApiResponse.success(service.edit(token(r),id,body));}
    @PutMapping("/{id:[0-9]+}/status") @Operation(summary="项目状态变更及归档",description="0未开始→1进行中→2已完成；2可重新开启为1；任意未归档状态可归档为3；归档子项目同事务处理")
    public ApiResponse<Void> status(HttpServletRequest r,@PathVariable long id,@Valid @RequestBody Status body){service.status(token(r),id,body);return ApiResponse.success(null);}
    @DeleteMapping("/{id:[0-9]+}") @Operation(summary="仅超级用户逻辑删除项目及其子项目、任务",description="JSON体传version；存在未结束作业时拒绝；执行历史保留，编号不复用")
    public ApiResponse<Void> delete(HttpServletRequest r,@PathVariable long id,@Valid @RequestBody Version body){service.delete(token(r),id,body);return ApiResponse.success(null);}
    @PutMapping("/{id:[0-9]+}/restore") @Operation(summary="恢复已归档项目",description="仅root或项目负责人；JSON体version；恢复归档前状态，并仅恢复本次随主项目归档的子项目；旧档无历史状态时恢复未开始")
    public ApiResponse<Void> restore(HttpServletRequest r,@PathVariable long id,@Valid @RequestBody Version body){service.restore(token(r),id,body);return ApiResponse.success(null);}
    @GetMapping("/{projectId:[0-9]+}/subproject/list") @Operation(summary="查询项目下全部有效子项目",description="按sort_order和ID排序")
    public ApiResponse<List<Map<String,Object>>> children(HttpServletRequest r,@PathVariable long projectId){return ApiResponse.success(service.children(token(r),projectId));}
    @GetMapping("/{projectId:[0-9]+}/subproject/owner/options") @Operation(summary="查询子项目负责人人选",description="限本项目正常编辑成员；当前批次未开放成员维护，可选创建人及主项目负责人")
    public ApiResponse<List<Map<String,Object>>> members(HttpServletRequest r,@PathVariable long projectId,@RequestParam(required=false) String keyword){return ApiResponse.success(service.options(token(r),projectId,keyword));}
    @GetMapping("/{projectId:[0-9]+}/subproject/{id:[0-9]+}") @Operation(summary="子项目详情",description="核对子项目确实属于指定项目")
    public ApiResponse<Map<String,Object>> child(HttpServletRequest r,@PathVariable long projectId,@PathVariable long id){return ApiResponse.success(service.child(token(r),projectId,id));}
    @PostMapping("/{projectId:[0-9]+}/subproject") @Operation(summary="新建子项目",description="前端提供code：SUB-12位年月日小时分钟-6位大写十六进制随机码，负责人必须为编辑成员，父项目版本递增")
    public ApiResponse<Map<String,Object>> createSub(HttpServletRequest r,@PathVariable long projectId,@Valid @RequestBody SubCreate body){return ApiResponse.success(service.createSub(token(r),projectId,body));}
    @PutMapping("/{projectId:[0-9]+}/subproject/{id:[0-9]+}") @Operation(summary="编辑子项目")
    public ApiResponse<Map<String,Object>> editSub(HttpServletRequest r,@PathVariable long projectId,@PathVariable long id,@Valid @RequestBody SubEdit body){return ApiResponse.success(service.editSub(token(r),projectId,id,body));}
    @PutMapping("/{projectId:[0-9]+}/subproject/{id:[0-9]+}/status") @Operation(summary="子项目状态变更及归档")
    public ApiResponse<Void> subStatus(HttpServletRequest r,@PathVariable long projectId,@PathVariable long id,@Valid @RequestBody Status body){service.subStatus(token(r),projectId,id,body);return ApiResponse.success(null);}
    @DeleteMapping("/{projectId:[0-9]+}/subproject/{id:[0-9]+}") @Operation(summary="仅超级用户逻辑删除子项目及其任务，拦截未结束作业并保留历史")
    public ApiResponse<Void> deleteSub(HttpServletRequest r,@PathVariable long projectId,@PathVariable long id,@Valid @RequestBody Version body){service.deleteSub(token(r),projectId,id,body);return ApiResponse.success(null);}
    @PutMapping("/{projectId:[0-9]+}/subproject/{id:[0-9]+}/restore") @Operation(summary="恢复已归档子项目",description="root、主项目负责人，或拥有subproject:restore的本子项目负责人；父项目须可编辑；JSON体version")
    public ApiResponse<Void> restoreSub(HttpServletRequest r,@PathVariable long projectId,@PathVariable long id,@Valid @RequestBody Version body){service.restoreSub(token(r),projectId,id,body);return ApiResponse.success(null);}
    @PutMapping("/{projectId:[0-9]+}/subproject/sort") @Operation(summary="批量排序子项目",description="提交父项目version与全部有效子项目ids；重复、遗漏或跨项目ID均拒绝")
    public ApiResponse<Void> sort(HttpServletRequest r,@PathVariable long projectId,@Valid @RequestBody Sort body){service.sort(token(r),projectId,body);return ApiResponse.success(null);}
}
