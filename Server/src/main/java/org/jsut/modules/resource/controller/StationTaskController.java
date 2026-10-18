package org.jsut.modules.resource.controller;
import org.jsut.modules.resource.service.TaskService;
import org.jsut.modules.user.service.AuthService;
import org.jsut.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.*;
@RestController @RequestMapping("/resource/station") @Tag(name="工位SOP任务") @SecurityRequirement(name="bearerAuth")
public class StationTaskController {
 private final TaskService service;
 public StationTaskController(TaskService service){this.service=service;}
 @GetMapping("/{id:[0-9]+}/task") @Operation(summary="查询工位任务，仅返回有项目查阅权限的任务")
 public ApiResponse<Map<String,Object>> list(HttpServletRequest request,@PathVariable long id){return ApiResponse.success(service.stationTasks(AuthService.tokenFrom(request.getHeader("Authorization")),id));}
}
