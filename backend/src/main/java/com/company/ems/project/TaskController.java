package com.company.ems.project;

import com.company.ems.common.ApiResponse;
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
@RequestMapping("/api/v1/projects/{projectId}")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('task:read')")
    public ApiResponse<List<TaskDtos.TaskView>> list(
            @PathVariable Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long assigneeEmployeeId,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(taskService.list(projectId, status, assigneeEmployeeId, keyword, user));
    }

    @PostMapping("/tasks")
    @PreAuthorize("hasAuthority('task:create')")
    public ApiResponse<TaskDtos.TaskView> create(
            @PathVariable Long projectId,
            @Valid @RequestBody TaskDtos.TaskRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(taskService.create(projectId, request, user));
    }

    @GetMapping("/tasks/{taskId}")
    @PreAuthorize("hasAuthority('task:read')")
    public ApiResponse<TaskDtos.TaskView> get(@PathVariable Long projectId,
                                              @PathVariable Long taskId,
                                              @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(taskService.get(projectId, taskId, user));
    }

    @PutMapping("/tasks/{taskId}")
    @PreAuthorize("hasAuthority('task:update')")
    public ApiResponse<TaskDtos.TaskView> update(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @Valid @RequestBody TaskDtos.TaskRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(taskService.update(projectId, taskId, request, user));
    }

    @PutMapping("/tasks/{taskId}/status")
    @PreAuthorize("hasAuthority('task:update')")
    public ApiResponse<TaskDtos.TaskView> updateStatus(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @Valid @RequestBody TaskDtos.TaskStatusRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(taskService.updateStatus(projectId, taskId, request, user));
    }

    @DeleteMapping("/tasks/{taskId}")
    @PreAuthorize("hasAuthority('task:delete')")
    public ApiResponse<Void> delete(@PathVariable Long projectId,
                                    @PathVariable Long taskId,
                                    @AuthenticationPrincipal LoginUser user) {
        taskService.delete(projectId, taskId, user);
        return ApiResponse.ok();
    }

    @GetMapping("/tasks/{taskId}/comments")
    @PreAuthorize("hasAuthority('task:read')")
    public ApiResponse<List<TaskDtos.CommentView>> comments(@PathVariable Long projectId,
                                                            @PathVariable Long taskId,
                                                            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(taskService.comments(projectId, taskId, user));
    }

    @PostMapping("/tasks/{taskId}/comments")
    @PreAuthorize("hasAuthority('task:comment')")
    public ApiResponse<TaskDtos.CommentView> comment(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @Valid @RequestBody TaskDtos.TaskCommentRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(taskService.comment(projectId, taskId, request, user));
    }

    @GetMapping("/milestones")
    @PreAuthorize("hasAuthority('milestone:read')")
    public ApiResponse<List<TaskDtos.MilestoneView>> milestones(
            @PathVariable Long projectId,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(taskService.milestones(projectId, user));
    }

    @PostMapping("/milestones")
    @PreAuthorize("hasAuthority('milestone:create')")
    public ApiResponse<TaskDtos.MilestoneView> createMilestone(
            @PathVariable Long projectId,
            @Valid @RequestBody TaskDtos.MilestoneRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(taskService.createMilestone(projectId, request, user));
    }

    @PutMapping("/milestones/{milestoneId}")
    @PreAuthorize("hasAuthority('milestone:update')")
    public ApiResponse<TaskDtos.MilestoneView> updateMilestone(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @Valid @RequestBody TaskDtos.MilestoneRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(taskService.updateMilestone(projectId, milestoneId, request, user));
    }

    @DeleteMapping("/milestones/{milestoneId}")
    @PreAuthorize("hasAuthority('milestone:delete')")
    public ApiResponse<Void> deleteMilestone(@PathVariable Long projectId,
                                             @PathVariable Long milestoneId,
                                             @AuthenticationPrincipal LoginUser user) {
        taskService.deleteMilestone(projectId, milestoneId, user);
        return ApiResponse.ok();
    }

    @GetMapping("/progress")
    @PreAuthorize("hasAuthority('task:read')")
    public ApiResponse<TaskDtos.ProgressView> progress(@PathVariable Long projectId,
                                                       @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(taskService.progress(projectId, user));
    }

    @PutMapping("/progress")
    @PreAuthorize("hasAuthority('project:progress:update')")
    public ApiResponse<TaskDtos.ProgressView> updateProgress(
            @PathVariable Long projectId,
            @Valid @RequestBody TaskDtos.ProgressRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(taskService.updateProgress(projectId, request, user));
    }

    @GetMapping("/progress/snapshots")
    @PreAuthorize("hasAuthority('task:read')")
    public ApiResponse<List<TaskDtos.ProgressSnapshotView>> snapshots(
            @PathVariable Long projectId,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(taskService.snapshots(projectId, user));
    }
}
