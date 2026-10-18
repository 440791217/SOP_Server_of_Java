package org.jsut.modules.sop.controller;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.jsut.common.ApiResponse;
import org.jsut.modules.sop.dto.SopRequests.*;
import org.jsut.modules.sop.service.SopBindingService;
import org.jsut.modules.user.service.AuthService;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping({"/project/{projectId:[0-9]+}/sop","/project/{projectId:[0-9]+}/subproject/{subprojectId:[0-9]+}/sop"})
@Tag(name="项目SOP关联",description="固定已发布版本；使用方只可通过关联预览，不获得源草稿访问权")
@SecurityRequirement(name="bearerAuth")
public class SopBindingController {
    private final SopBindingService service;public SopBindingController(SopBindingService service){this.service=service;}
    private String token(HttpServletRequest r){return AuthService.tokenFrom(r.getHeader("Authorization"));}
    @GetMapping("/list") @Operation(summary="查询有效及已解绑SOP关联")
    public ApiResponse<Map<String,Object>> list(HttpServletRequest r,@PathVariable long projectId,@PathVariable(required=false) Long subprojectId){return ApiResponse.success(service.list(token(r),projectId,subprojectId));}
    @GetMapping("/options") @Operation(summary="搜索本人可访问且有已发布版本的SOP")
    public ApiResponse<List<Map<String,Object>>> options(HttpServletRequest r,@PathVariable long projectId,@PathVariable(required=false) Long subprojectId,@RequestParam(required=false) String keyword){return ApiResponse.success(service.options(token(r),projectId,subprojectId,keyword));}
    @PostMapping @Operation(summary="新建固定版本关联",description="sop_id/revision_id/target_version；已有解绑关联请更新原行")
    public ApiResponse<Map<String,Object>> create(HttpServletRequest r,@PathVariable long projectId,@PathVariable(required=false) Long subprojectId,@Valid @RequestBody Binding b){return ApiResponse.success(service.save(token(r),projectId,subprojectId,null,b));}
    @PutMapping("/{bindingId:[0-9]+}") @Operation(summary="切换版本或重新关联",description="sop_id不可改变；version为关联版本，target_version为项目或子项目版本")
    public ApiResponse<Map<String,Object>> update(HttpServletRequest r,@PathVariable long projectId,@PathVariable(required=false) Long subprojectId,@PathVariable long bindingId,@Valid @RequestBody Binding b){return ApiResponse.success(service.save(token(r),projectId,subprojectId,bindingId,b));}
    @DeleteMapping("/{bindingId:[0-9]+}") @Operation(summary="解绑，保留历史关联行")
    public ApiResponse<Map<String,Object>> remove(HttpServletRequest r,@PathVariable long projectId,@PathVariable(required=false) Long subprojectId,@PathVariable long bindingId,@Valid @RequestBody BindingVersion b){return ApiResponse.success(service.unbind(token(r),projectId,subprojectId,bindingId,b));}
    @PutMapping("/sort") @Operation(summary="排列全部有效关联")
    public ApiResponse<Map<String,Object>> sort(HttpServletRequest r,@PathVariable long projectId,@PathVariable(required=false) Long subprojectId,@Valid @RequestBody BindingSort b){return ApiResponse.success(service.sort(token(r),projectId,subprojectId,b));}
    @GetMapping("/{bindingId:[0-9]+}/preview") @Operation(summary="使用项目按关联查看固定版本")
    public ApiResponse<Map<String,Object>> preview(HttpServletRequest r,@PathVariable long projectId,@PathVariable(required=false) Long subprojectId,@PathVariable long bindingId){return ApiResponse.success(service.preview(token(r),projectId,subprojectId,bindingId));}
    @GetMapping("/{bindingId:[0-9]+}/image/{fileId:[0-9]+}") @Operation(summary="通过关联授权读取图片")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.ByteArrayResource> image(HttpServletRequest r,@PathVariable long projectId,@PathVariable(required=false) Long subprojectId,@PathVariable long bindingId,@PathVariable long fileId){return SopImageResponse.of(service.image(token(r),projectId,subprojectId,bindingId,fileId));}
}
