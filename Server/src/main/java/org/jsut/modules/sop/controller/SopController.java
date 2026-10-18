package org.jsut.modules.sop.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.jsut.common.ApiResponse;
import org.jsut.modules.sop.dto.SopRequests.*;
import org.jsut.modules.sop.service.SopService;
import org.jsut.modules.user.service.AuthService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/sop")
@Tag(name="SOP草稿与版本",description="图文SOP草稿与发布历史；按管理项目授权；已发布内容不可改")
@SecurityRequirement(name="bearerAuth")
public class SopController {
    @PutMapping("/{id:[0-9]+}/version/{revisionId:[0-9]+}/step/{stepId:[0-9]+}/authoring")
    @Operation(summary="保存当前步骤制作配置与图片标注",description="step携带步骤version和revision_version；visuals为需更新的图片引用配置，全部同事务提交；检测区域为配置，未接入识别服务；摄像头截图复用步骤图片上传接口")
    public ApiResponse<Map<String,Object>> authoring(HttpServletRequest r,@PathVariable long id,@PathVariable long revisionId,@PathVariable long stepId,@Valid @RequestBody Authoring b){return ApiResponse.success(service.saveAuthoring(token(r),id,revisionId,stepId,b));}
    @PostMapping(value="/{id:[0-9]+}/version/{revisionId:[0-9]+}/step/{stepId:[0-9]+}/image",consumes="multipart/form-data")
    @Operation(summary="上传步骤图片",description="仅草稿；multipart file和草稿version；PNG/JPEG最多5MiB，事务内关联，返回最新版本")
    public ApiResponse<Map<String,Object>> upload(HttpServletRequest r,@PathVariable long id,@PathVariable long revisionId,@PathVariable long stepId,@RequestParam int version,@RequestPart org.springframework.web.multipart.MultipartFile file){return ApiResponse.success(service.uploadImage(token(r),id,revisionId,stepId,version,file));}
    @DeleteMapping("/{id:[0-9]+}/version/{revisionId:[0-9]+}/step/{stepId:[0-9]+}/image/{attachmentId:[0-9]+}") @Operation(summary="移除草稿图片引用",description="JSON草稿version；不删除文件对象，不影响其他版本")
    public ApiResponse<Map<String,Object>> removeImage(HttpServletRequest r,@PathVariable long id,@PathVariable long revisionId,@PathVariable long stepId,@PathVariable long attachmentId,@Valid @RequestBody Version b){return ApiResponse.success(service.removeImage(token(r),id,revisionId,stepId,attachmentId,b.version()));}
    @GetMapping("/{id:[0-9]+}/version/{revisionId:[0-9]+}/image/{fileId:[0-9]+}") @Operation(summary="授权读取版本图片",description="每次校验管理项目权限及图片引用，私有文件不走静态目录")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.ByteArrayResource> image(HttpServletRequest r,@PathVariable long id,@PathVariable long revisionId,@PathVariable long fileId){return SopImageResponse.of(service.image(token(r),id,revisionId,fileId));}
    private final SopService service;
    public SopController(SopService service){this.service=service;}
    private String token(HttpServletRequest r){return AuthService.tokenFrom(r.getHeader("Authorization"));}
    @GetMapping("/list") @Operation(summary="分页查询有管理项目访问权的SOP",description="keyword匹配编号/名称；project_id可选；limit最多100；本批不通过其他项目的关联扩大访问权")
    public ApiResponse<Map<String,Object>> list(HttpServletRequest r,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int limit,@RequestParam(required=false) String keyword,@RequestParam(required=false) Long project_id){return ApiResponse.success(service.list(token(r),page,limit,keyword,project_id));}
    @GetMapping("/project/options") @Operation(summary="搜索可创建SOP的管理项目",description="仅未开始/进行中；keyword可选；最多50条")
    public ApiResponse<List<Map<String,Object>>> projects(HttpServletRequest r,@RequestParam(required=false) String keyword){return ApiResponse.success(service.projectOptions(token(r),keyword));}
    @GetMapping("/project/{projectId:[0-9]+}/owner/options") @Operation(summary="搜索SOP负责人候选",description="限管理项目正常有效编辑成员；需要创建SOP能力或项目管理身份")
    public ApiResponse<List<Map<String,Object>>> owners(HttpServletRequest r,@PathVariable long projectId,@RequestParam(required=false) String keyword){return ApiResponse.success(service.ownerOptions(token(r),projectId,keyword));}
    @PostMapping @Operation(summary="新建SOP并创建V1草稿",description="前端提供code：SOP-12位年月日小时分钟-6位大写十六进制随机码，不复用；管理项目不可移动；初始草稿没有步骤")
    public ApiResponse<Map<String,Object>> create(HttpServletRequest r,@Valid @RequestBody Create b){return ApiResponse.success(service.create(token(r),b));}
    @GetMapping("/{id:[0-9]+}") @Operation(summary="查询SOP基本信息",description="返回基本资料version、当前草稿数、最新可用发布版本号及操作标志")
    public ApiResponse<Map<String,Object>> detail(HttpServletRequest r,@PathVariable long id){return ApiResponse.success(service.detail(token(r),id));}
    @PutMapping("/{id:[0-9]+}") @Operation(summary="编辑SOP目录名称",description="JSON name和文档version；不改变历史版本标题、编号、管理项目或负责人")
    public ApiResponse<Map<String,Object>> edit(HttpServletRequest r,@PathVariable long id,@Valid @RequestBody DocumentEdit b){return ApiResponse.success(service.editDocument(token(r),id,b));}
    @PutMapping("/{id:[0-9]+}/owner") @Operation(summary="转移SOP负责人",description="root或管理项目负责人；新负责人须为正常有效编辑成员；管理项目归档/完成时仍可交接，写项目操作记录")
    public ApiResponse<Void> owner(HttpServletRequest r,@PathVariable long id,@Valid @RequestBody Owner b){service.owner(token(r),id,b);return ApiResponse.success(null);}
    @DeleteMapping("/{id:[0-9]+}") @Operation(summary="软删除从未发布且无关联记录的SOP",description="JSON文档version；保留编号及历史草稿记录；已发布/停用SOP不能删除")
    public ApiResponse<Void> delete(HttpServletRequest r,@PathVariable long id,@Valid @RequestBody Version b){service.delete(token(r),id,b);return ApiResponse.success(null);}
    @GetMapping("/{id:[0-9]+}/version/list") @Operation(summary="查询SOP版本历史",description="不含已放弃草稿，按版本号倒序；不返回内部draft_slot")
    public ApiResponse<List<Map<String,Object>>> versions(HttpServletRequest r,@PathVariable long id){return ApiResponse.success(service.revisions(token(r),id));}
    @GetMapping("/{id:[0-9]+}/version/{revisionId:[0-9]+}") @Operation(summary="预览版本完整内容",description="包含步骤及附件元数据；图片通过授权接口读取，不返回存储路径及校验摘要")
    public ApiResponse<Map<String,Object>> version(HttpServletRequest r,@PathVariable long id,@PathVariable long revisionId){return ApiResponse.success(service.revisionDetail(token(r),id,revisionId));}
    @PostMapping("/{id:[0-9]+}/draft") @Operation(summary="从历史发布或停用版本复制新草稿",description="JSON source_revision_id及文档version；最多一个有效草稿，生成新步骤ID，原内容不变")
    public ApiResponse<Map<String,Object>> draft(HttpServletRequest r,@PathVariable long id,@Valid @RequestBody Draft b){return ApiResponse.success(service.newDraft(token(r),id,b));}
    @PutMapping("/{id:[0-9]+}/version/{revisionId:[0-9]+}") @Operation(summary="保存草稿标题、适用范围及修订说明",description="JSON version指版本version；已发布/已停用内容只读")
    public ApiResponse<Map<String,Object>> editVersion(HttpServletRequest r,@PathVariable long id,@PathVariable long revisionId,@Valid @RequestBody RevisionEdit b){return ApiResponse.success(service.editRevision(token(r),id,revisionId,b));}
    @DeleteMapping("/{id:[0-9]+}/version/{revisionId:[0-9]+}") @Operation(summary="放弃草稿",description="JSON版本version；保留版本号，不复用，不删除旧文件对象")
    public ApiResponse<Void> discard(HttpServletRequest r,@PathVariable long id,@PathVariable long revisionId,@Valid @RequestBody Version b){service.discard(token(r),id,revisionId,b);return ApiResponse.success(null);}
    @PostMapping("/{id:[0-9]+}/version/{revisionId:[0-9]+}/step") @Operation(summary="在草稿末尾新增步骤",description="JSON version为草稿version，最多1000步骤；返回最新完整版本及步骤版本")
    public ApiResponse<Map<String,Object>> addStep(HttpServletRequest r,@PathVariable long id,@PathVariable long revisionId,@Valid @RequestBody StepCreate b){return ApiResponse.success(service.addStep(token(r),id,revisionId,b));}
    @PutMapping("/{id:[0-9]+}/version/{revisionId:[0-9]+}/step/{stepId:[0-9]+}") @Operation(summary="编辑草稿步骤",description="JSON version为步骤version，revision_version为草稿version；每次写入递增草稿和文档version")
    public ApiResponse<Map<String,Object>> editStep(HttpServletRequest r,@PathVariable long id,@PathVariable long revisionId,@PathVariable long stepId,@Valid @RequestBody StepEdit b){return ApiResponse.success(service.editStep(token(r),id,revisionId,stepId,b));}
    @DeleteMapping("/{id:[0-9]+}/version/{revisionId:[0-9]+}/step/{stepId:[0-9]+}") @Operation(summary="删除草稿步骤并整理剩余顺序",description="JSON version、revision_version；删除附件引用但不删除文件对象")
    public ApiResponse<Map<String,Object>> deleteStep(HttpServletRequest r,@PathVariable long id,@PathVariable long revisionId,@PathVariable long stepId,@Valid @RequestBody StepDelete b){return ApiResponse.success(service.deleteStep(token(r),id,revisionId,stepId,b));}
    @PutMapping("/{id:[0-9]+}/version/{revisionId:[0-9]+}/step/sort") @Operation(summary="保存草稿全部步骤顺序",description="JSON ids和版本version；不能重复、遗漏或混入别的版本步骤；步骤version也会递增")
    public ApiResponse<Map<String,Object>> sort(HttpServletRequest r,@PathVariable long id,@PathVariable long revisionId,@Valid @RequestBody Sort b){return ApiResponse.success(service.sort(token(r),id,revisionId,b));}
    @PutMapping("/{id:[0-9]+}/version/{revisionId:[0-9]+}/publish") @Operation(summary="发布图文SOP草稿",description="JSON版本version；至少一个步骤；V2起必填修订说明；发布前校验图片对象完整性；不自动更新任何关联")
    public ApiResponse<Map<String,Object>> publish(HttpServletRequest r,@PathVariable long id,@PathVariable long revisionId,@Valid @RequestBody Version b){return ApiResponse.success(service.publish(token(r),id,revisionId,b));}
    @PutMapping("/{id:[0-9]+}/version/{revisionId:[0-9]+}/retire") @Operation(summary="停用已发布版本",description="JSON版本version；未完成项目仍在使用时拒绝；历史版本仍可预览，不提供原地恢复")
    public ApiResponse<Map<String,Object>> retire(HttpServletRequest r,@PathVariable long id,@PathVariable long revisionId,@Valid @RequestBody Version b){return ApiResponse.success(service.retire(token(r),id,revisionId,b));}
}

