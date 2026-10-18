package org.jsut.modules.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.jsut.common.ApiResponse;
import org.jsut.modules.user.dto.*;
import org.jsut.modules.user.service.AuthService;
import org.jsut.modules.user.service.UserManagementService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping(value="/user",produces=MediaType.APPLICATION_JSON_VALUE)
@Tag(name="用户管理",description="分页查询、新建、编辑、状态、软删除、密码重置和角色分配；需要对应user:*权限")
@SecurityRequirement(name="bearerAuth")
public class UserManagementController {
    private final UserManagementService service;
    public UserManagementController(UserManagementService service){this.service=service;}
    private String token(HttpServletRequest request){return AuthService.tokenFrom(request.getHeader("Authorization"));}

    @GetMapping("/list")
    @Operation(summary="分页查询用户",description="需要user:list；只返回未删除用户，按ID倒序；工卡精确匹配，账号/姓名包含匹配。limit为1至100。状态0不可当成未传。")
    public ApiResponse<UserPage> list(HttpServletRequest request,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int limit,
            @RequestParam(required=false) String username,@RequestParam(required=false) String real_name,
            @RequestParam(required=false) String card_number,@RequestParam(required=false) String team_id,
            @RequestParam(required=false) Integer status){
        return ApiResponse.success(service.list(token(request),page,limit,username,real_name,card_number,team_id,status));
    }
    @GetMapping("/{id:[0-9]+}")
    @Operation(summary="查询用户详情",description="需要user:list；不返回密码或会话摘要。")
    public ApiResponse<UserManagementView> detail(HttpServletRequest request,@PathVariable long id){return ApiResponse.success(service.detail(token(request),id));}

    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="新建用户",description="需要user:create；未指定角色默认member。显式分配角色另需user:assign-role。新账号首次登录须改密。账号/工卡重复409。")
    public ApiResponse<UserManagementView> create(HttpServletRequest request,@Valid @RequestBody UserCreateRequest body){return ApiResponse.success(service.create(token(request),body));}

    @PutMapping(value="/{id:[0-9]+}",consumes=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="编辑用户资料",description="需要user:update；账号不可改，状态使用专用接口。角色变更额外校验权限，同一事务保存。版本冲突409。root不可编辑。")
    public ApiResponse<UserManagementView> update(HttpServletRequest request,@PathVariable long id,@Valid @RequestBody UserUpdateRequest body){return ApiResponse.success(service.update(token(request),id,body));}

    @PutMapping(value="/{id:[0-9]+}/status",consumes=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="启用、禁用或设为离职",description="需要user:status；禁止操作root和本人。禁用/离职立即撤销所有会话。")
    public ApiResponse<Void> status(HttpServletRequest request,@PathVariable long id,@Valid @RequestBody UserStatusRequest body){service.status(token(request),id,body);return ApiResponse.success(null);}

    @DeleteMapping(value="/{id:[0-9]+}",consumes=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="软删除用户",description="需要user:delete；JSON请求体传version。禁止删除root和本人；保留历史关联并撤销会话，用户名不复用，工卡可重新绑定。")
    public ApiResponse<Void> delete(HttpServletRequest request,@PathVariable long id,@Valid @RequestBody UserVersionRequest body){service.delete(token(request),id,body);return ApiResponse.success(null);}

    @PutMapping(value="/{id:[0-9]+}/password",consumes=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="重置用户密码",description="需要user:reset-password；密码8至128字符。撤销目标全部会话，并要求下次登录改密。不能重置root或本人。")
    public ApiResponse<Void> resetPassword(HttpServletRequest request,@PathVariable long id,@Valid @RequestBody UserPasswordResetRequest body){service.resetPassword(token(request),id,body);return ApiResponse.success(null);}

    @PutMapping(value="/{id:[0-9]+}/roles",consumes=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="替换用户角色",description="需要user:assign-role；普通管理员只能分配本人持有的启用角色。不能操作root或自己的角色。空数组清除角色。")
    public ApiResponse<Void> roles(HttpServletRequest request,@PathVariable long id,@Valid @RequestBody UserRoleRequest body){service.roles(token(request),id,body);return ApiResponse.success(null);}

    @GetMapping("/team/options")
    @Operation(summary="查询班组选项",description="需user:list/create/update任一权限；含禁用班组以显示历史归属，新分配只允许启用班组。当前允许不选班组。")
    public ApiResponse<List<UserOption>> teams(HttpServletRequest request){return ApiResponse.success(service.teams(token(request)));}

    @GetMapping("/role/options")
    @Operation(summary="查询可分配角色",description="需user:create或user:assign-role；root可见全部启用角色，普通管理员只看到其本人持有的启用角色。")
    public ApiResponse<List<UserOption>> roles(HttpServletRequest request){return ApiResponse.success(service.roles(token(request)));}
}
