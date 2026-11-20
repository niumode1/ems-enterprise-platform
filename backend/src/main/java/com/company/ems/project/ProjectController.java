package com.company.ems.project;

import com.company.ems.common.ApiResponse;
import com.company.ems.common.PageResult;
import com.company.ems.security.LoginUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('project:read')")
    public ApiResponse<PageResult<ProjectDtos.ProjectView>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(projectService.page(page, size, keyword, status, user));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('project:create')")
    public ApiResponse<ProjectDtos.ProjectView> create(
            @Valid @RequestBody ProjectDtos.ProjectRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(projectService.create(request, user));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('project:read')")
    public ApiResponse<ProjectDtos.ProjectView> get(@PathVariable Long id,
                                                    @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(projectService.get(id, user));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('project:update')")
    public ApiResponse<ProjectDtos.ProjectView> update(
            @PathVariable Long id,
            @Valid @RequestBody ProjectDtos.ProjectRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(projectService.update(id, request, user));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('project:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @AuthenticationPrincipal LoginUser user) {
        projectService.delete(id, user);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("hasAuthority('project:read')")
    public ApiResponse<List<ProjectDtos.MemberView>> members(@PathVariable Long id,
                                                             @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(projectService.members(id, user));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAuthority('project:member:manage')")
    public ApiResponse<ProjectDtos.MemberView> addMember(
            @PathVariable Long id,
            @Valid @RequestBody ProjectDtos.MemberRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(projectService.addMember(id, request, user));
    }

    @DeleteMapping("/{id}/members/{memberId}")
    @PreAuthorize("hasAuthority('project:member:manage')")
    public ApiResponse<Void> removeMember(@PathVariable Long id,
                                          @PathVariable Long memberId,
                                          @AuthenticationPrincipal LoginUser user) {
        projectService.removeMember(id, memberId, user);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/folders")
    @PreAuthorize("hasAuthority('project:read')")
    public ApiResponse<List<ProjectDtos.FolderView>> folders(@PathVariable Long id,
                                                             @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(projectService.folders(id, user));
    }

    @PostMapping("/{id}/folders")
    @PreAuthorize("hasAuthority('doc:upload')")
    public ApiResponse<ProjectDtos.FolderView> createFolder(
            @PathVariable Long id,
            @Valid @RequestBody ProjectDtos.FolderRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(projectService.createFolder(id, request, user));
    }
}
