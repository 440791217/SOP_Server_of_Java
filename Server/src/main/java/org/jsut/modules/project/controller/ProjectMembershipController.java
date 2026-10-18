package org.jsut.modules.project.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.jsut.common.ApiResponse;
import org.jsut.modules.project.dto.ProjectRequests.*;
import org.jsut.modules.project.service.ProjectMembershipService;
import org.jsut.modules.user.service.AuthService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/project/{projectId:[0-9]+}")
@Tag(name="项目成员与交接",description="写操作仅root或当前主项目负责人；完成或归档项目仍允许交接；移除保留历史关联")
@SecurityRequirement(name="bearerAuth")
public class ProjectMembershipController {
    private final ProjectMembershipService service;
    public ProjectMembershipController(ProjectMembershipService service){this.service=service;}
    private String token(HttpServletRequest r){return AuthService.tokenFrom(r.getHeader("Authorization"));}
    @GetMapping("/member/list") @Operation(summary="分页查看项目成员",description="status为1有效、0移除，不传包含全部；返回缺少的常用系统功能权限")
    public ApiResponse<Map<String,Object>> list(HttpServletRequest r,@PathVariable long projectId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int limit,@RequestParam(required=false) String keyword,@RequestParam(required=false) Integer status){return ApiResponse.success(service.members(token(r),projectId,page,limit,keyword,status));}
    @GetMapping("/member/options") @Operation(summary="搜索成员或负责人候选",description="purpose: member(可加入人员)、project_owner(正常用户，可自动加入)、subproject_owner(有效编辑成员)；最多50条")
    public ApiResponse<List<Map<String,Object>>> options(HttpServletRequest r,@PathVariable long projectId,@RequestParam(required=false) String keyword,@RequestParam(defaultValue="member") String purpose){return ApiResponse.success(service.options(token(r),projectId,keyword,purpose));}
    @PostMapping("/member") @Operation(summary="添加或重新加入项目成员",description="version为父项目版本；重复有效成员409；移除成员恢复原关联并递增成员版本")
    public ApiResponse<Void> add(HttpServletRequest r,@PathVariable long projectId,@Valid @RequestBody MemberAdd body){service.add(token(r),projectId,body);return ApiResponse.success(null);}
    @PutMapping("/member/{userId:[0-9]+}") @Operation(summary="修改成员查看或编辑权限",description="version为成员版本；负责人及承担有效子项目负责人者不可降级")
    public ApiResponse<Void> edit(HttpServletRequest r,@PathVariable long projectId,@PathVariable long userId,@Valid @RequestBody MemberEdit body){service.access(token(r),projectId,userId,body);return ApiResponse.success(null);}
    @DeleteMapping("/member/{userId:[0-9]+}") @Operation(summary="移除成员但保留历史记录",description="JSON体成员version；移除立即失去项目访问；先交接有效子项目；禁止移除项目负责人")
    public ApiResponse<Void> remove(HttpServletRequest r,@PathVariable long projectId,@PathVariable long userId,@Valid @RequestBody Version body){service.remove(token(r),projectId,userId,body);return ApiResponse.success(null);}
    @PutMapping("/owner") @Operation(summary="转移主项目负责人",description="JSON体owner_user_id和项目version；新负责人自动加入/恢复/提升为编辑成员；旧负责人保留编辑身份")
    public ApiResponse<Void> owner(HttpServletRequest r,@PathVariable long projectId,@Valid @RequestBody Owner body){service.owner(token(r),projectId,body);return ApiResponse.success(null);}
    @PutMapping("/subproject/{subprojectId:[0-9]+}/owner") @Operation(summary="转移子项目负责人",description="仅root或主项目负责人；新负责人必须为有效正常编辑成员，version为子项目版本；归档内容保持只读")
    public ApiResponse<Void> subOwner(HttpServletRequest r,@PathVariable long projectId,@PathVariable long subprojectId,@Valid @RequestBody Owner body){service.subOwner(token(r),projectId,subprojectId,body);return ApiResponse.success(null);}
    @GetMapping("/activity/list") @Operation(summary="查看成员和负责人变更记录",description="需要项目查看权限，按ID倒序；记录操作者和目标姓名快照、变更前后值；不提供修改或删除接口")
    public ApiResponse<Map<String,Object>> activities(HttpServletRequest r,@PathVariable long projectId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int limit){return ApiResponse.success(service.activities(token(r),projectId,page,limit));}
}
