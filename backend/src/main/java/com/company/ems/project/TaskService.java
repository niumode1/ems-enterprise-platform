package com.company.ems.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.company.ems.audit.AuditService;
import com.company.ems.common.BusinessException;
import com.company.ems.config.ConfigService;
import com.company.ems.employee.entity.Employee;
import com.company.ems.employee.mapper.EmployeeMapper;
import com.company.ems.notification.NotificationService;
import com.company.ems.project.entity.Milestone;
import com.company.ems.project.entity.Project;
import com.company.ems.project.entity.ProjectMember;
import com.company.ems.project.entity.ProjectProgressSnapshot;
import com.company.ems.project.entity.Task;
import com.company.ems.project.entity.TaskComment;
import com.company.ems.project.entity.TaskDependency;
import com.company.ems.project.entity.TaskParticipant;
import com.company.ems.project.mapper.MilestoneMapper;
import com.company.ems.project.mapper.ProjectMapper;
import com.company.ems.project.mapper.ProjectProgressSnapshotMapper;
import com.company.ems.project.mapper.TaskCommentMapper;
import com.company.ems.project.mapper.TaskDependencyMapper;
import com.company.ems.project.mapper.TaskMapper;
import com.company.ems.project.mapper.TaskParticipantMapper;
import com.company.ems.security.LoginUser;
import com.company.ems.system.entity.SysUser;
import com.company.ems.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private static final Set<String> STATUSES = Set.of(
            "TODO", "IN_PROGRESS", "BLOCKED", "DONE", "CANCELLED");
    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");
    private static final String AUTO_PROGRESS_KEY = "project.progress.autoFromTasks";

    private final TaskMapper taskMapper;
    private final TaskParticipantMapper participantMapper;
    private final TaskDependencyMapper dependencyMapper;
    private final TaskCommentMapper commentMapper;
    private final MilestoneMapper milestoneMapper;
    private final ProjectProgressSnapshotMapper snapshotMapper;
    private final ProjectMapper projectMapper;
    private final ProjectService projectService;
    private final EmployeeMapper employeeMapper;
    private final SysUserMapper userMapper;
    private final ConfigService configService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public TaskService(TaskMapper taskMapper,
                       TaskParticipantMapper participantMapper,
                       TaskDependencyMapper dependencyMapper,
                       TaskCommentMapper commentMapper,
                       MilestoneMapper milestoneMapper,
                       ProjectProgressSnapshotMapper snapshotMapper,
                       ProjectMapper projectMapper,
                       ProjectService projectService,
                       EmployeeMapper employeeMapper,
                       SysUserMapper userMapper,
                       ConfigService configService,
                       NotificationService notificationService,
                       AuditService auditService) {
        this.taskMapper = taskMapper;
        this.participantMapper = participantMapper;
        this.dependencyMapper = dependencyMapper;
        this.commentMapper = commentMapper;
        this.milestoneMapper = milestoneMapper;
        this.snapshotMapper = snapshotMapper;
        this.projectMapper = projectMapper;
        this.projectService = projectService;
        this.employeeMapper = employeeMapper;
        this.userMapper = userMapper;
        this.configService = configService;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------ tasks

    public List<TaskDtos.TaskView> list(Long projectId, String status, Long assigneeEmployeeId,
                                        String keyword, LoginUser user) {
        ProjectMember member = projectService.requireMember(projectId, user);
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId)
                .eq(StringUtils.hasText(status), Task::getStatus, normalizeStatus(status))
                .eq(assigneeEmployeeId != null, Task::getAssigneeEmployeeId, assigneeEmployeeId)
                .like(StringUtils.hasText(keyword), Task::getTitle, keyword)
                .orderByAsc(Task::getSort)
                .orderByDesc(Task::getCreatedAt);
        return toViews(taskMapper.selectList(wrapper), member, user);
    }

    public TaskDtos.TaskView get(Long projectId, Long taskId, LoginUser user) {
        ProjectMember member = projectService.requireMember(projectId, user);
        return toViews(List.of(requireTask(projectId, taskId)), member, user).get(0);
    }

    @Transactional
    public TaskDtos.TaskView create(Long projectId, TaskDtos.TaskRequest request, LoginUser user) {
        ProjectMember member = projectService.requireMember(projectId, user);
        requireRole("project.task.createRoles", "OWNER,ADMIN,MEMBER", member, null, user);
        validateAssignees(projectId, request.getAssigneeEmployeeId(),
                request.getParticipantEmployeeIds());

        Task task = new Task();
        task.setProjectId(projectId);
        task.setTitle(request.getTitle().trim());
        task.setDescription(request.getDescription());
        task.setStatus(normalizeStatus(request.getStatus()));
        task.setPriority(normalizePriority(request.getPriority()));
        task.setAssigneeEmployeeId(request.getAssigneeEmployeeId());
        task.setCreatorEmployeeId(user.employeeId());
        task.setStartDate(request.getStartDate());
        task.setDueDate(request.getDueDate());
        task.setSort(request.getSort() == null ? 0 : request.getSort());
        applyProgressAndCompletion(task, request.getProgress());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);

        replaceParticipants(task.getId(), request.getParticipantEmployeeIds());
        replaceDependencies(projectId, task.getId(), request.getDependsOnTaskIds());
        notifyAssignee(projectId, task, null);
        auditService.record("PROJECT", "task", "CREATE", "TASK", task.getId(),
                null, task, null, null);
        refreshAutoProgress(projectId, user.employeeId(), "任务创建");
        return toViews(List.of(task), member, user).get(0);
    }

    @Transactional
    public TaskDtos.TaskView update(Long projectId, Long taskId, TaskDtos.TaskRequest request,
                                    LoginUser user) {
        ProjectMember member = projectService.requireMember(projectId, user);
        Task task = requireTask(projectId, taskId);
        requireRole("project.task.editRoles", "OWNER,ADMIN,ASSIGNEE", member, task, user);
        validateAssignees(projectId, request.getAssigneeEmployeeId(),
                request.getParticipantEmployeeIds());

        Long oldAssignee = task.getAssigneeEmployeeId();
        String oldTitle = task.getTitle();
        task.setTitle(request.getTitle().trim());
        task.setDescription(request.getDescription());
        task.setStatus(normalizeStatus(request.getStatus()));
        task.setPriority(normalizePriority(request.getPriority()));
        task.setAssigneeEmployeeId(request.getAssigneeEmployeeId());
        task.setStartDate(request.getStartDate());
        task.setDueDate(request.getDueDate());
        if (request.getSort() != null) {
            task.setSort(request.getSort());
        }
        applyProgressAndCompletion(task, request.getProgress());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        clearCompletionIfNeeded(task);

        if (request.getParticipantEmployeeIds() != null) {
            replaceParticipants(taskId, request.getParticipantEmployeeIds());
        }
        if (request.getDependsOnTaskIds() != null) {
            replaceDependencies(projectId, taskId, request.getDependsOnTaskIds());
        }
        if (!Objects.equals(oldAssignee, task.getAssigneeEmployeeId())) {
            notifyAssignee(projectId, task, oldTitle);
        }
        auditService.record("PROJECT", "task", "UPDATE", "TASK", taskId,
                Map.of("title", oldTitle, "assignee", oldAssignee == null ? "" : oldAssignee),
                task, null, null);
        refreshAutoProgress(projectId, user.employeeId(), "任务更新");
        return toViews(List.of(task), member, user).get(0);
    }

    @Transactional
    public TaskDtos.TaskView updateStatus(Long projectId, Long taskId,
                                          TaskDtos.TaskStatusRequest request, LoginUser user) {
        ProjectMember member = projectService.requireMember(projectId, user);
        Task task = requireTask(projectId, taskId);
        requireRole("project.task.editRoles", "OWNER,ADMIN,ASSIGNEE", member, task, user);
        String oldStatus = task.getStatus();
        task.setStatus(normalizeStatus(request.getStatus()));
        applyProgressAndCompletion(task, request.getProgress());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        clearCompletionIfNeeded(task);
        auditService.record("PROJECT", "task", "STATUS", "TASK", taskId,
                Map.of("status", oldStatus), Map.of("status", task.getStatus()), null, null);
        refreshAutoProgress(projectId, user.employeeId(), "任务状态变更");
        return toViews(List.of(task), member, user).get(0);
    }

    @Transactional
    public void delete(Long projectId, Long taskId, LoginUser user) {
        ProjectMember member = projectService.requireMember(projectId, user);
        Task task = requireTask(projectId, taskId);
        requireRole("project.task.deleteRoles", "OWNER,ADMIN", member, task, user);
        taskMapper.deleteById(taskId);
        participantMapper.delete(new LambdaQueryWrapper<TaskParticipant>()
                .eq(TaskParticipant::getTaskId, taskId));
        dependencyMapper.delete(new LambdaQueryWrapper<TaskDependency>()
                .eq(TaskDependency::getTaskId, taskId)
                .or().eq(TaskDependency::getDependsOnTaskId, taskId));
        commentMapper.delete(new LambdaQueryWrapper<TaskComment>()
                .eq(TaskComment::getTaskId, taskId));
        auditService.record("PROJECT", "task", "DELETE", "TASK", taskId,
                task, null, null, null);
        refreshAutoProgress(projectId, user.employeeId(), "任务删除");
    }

    // ------------------------------------------------------------------ comments

    public List<TaskDtos.CommentView> comments(Long projectId, Long taskId, LoginUser user) {
        projectService.requireMember(projectId, user);
        requireTask(projectId, taskId);
        List<TaskComment> comments = commentMapper.selectList(new LambdaQueryWrapper<TaskComment>()
                .eq(TaskComment::getTaskId, taskId)
                .orderByAsc(TaskComment::getCreatedAt));
        Map<Long, Employee> authors = employees(comments.stream()
                .map(TaskComment::getAuthorEmployeeId).toList());
        return comments.stream().map(comment -> {
            Employee author = authors.get(comment.getAuthorEmployeeId());
            return new TaskDtos.CommentView(comment.getId(), comment.getTaskId(),
                    comment.getAuthorEmployeeId(), author == null ? null : author.getName(),
                    comment.getContent(), comment.getCreatedAt());
        }).toList();
    }

    @Transactional
    public TaskDtos.CommentView comment(Long projectId, Long taskId,
                                        TaskDtos.TaskCommentRequest request, LoginUser user) {
        projectService.requireMember(projectId, user);
        requireTask(projectId, taskId);
        if (user.employeeId() == null) {
            throw new BusinessException(40001, "当前账号未关联员工，不能评论");
        }
        TaskComment comment = new TaskComment();
        comment.setTaskId(taskId);
        comment.setAuthorEmployeeId(user.employeeId());
        comment.setContent(request.getContent().trim());
        comment.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(comment);
        auditService.record("PROJECT", "task", "COMMENT", "TASK", taskId,
                null, comment, null, null);
        Employee author = employeeMapper.selectById(user.employeeId());
        return new TaskDtos.CommentView(comment.getId(), taskId, user.employeeId(),
                author == null ? null : author.getName(), comment.getContent(),
                comment.getCreatedAt());
    }

    // ------------------------------------------------------------------ milestones

    public List<TaskDtos.MilestoneView> milestones(Long projectId, LoginUser user) {
        projectService.requireMember(projectId, user);
        return milestoneMapper.selectList(new LambdaQueryWrapper<Milestone>()
                        .eq(Milestone::getProjectId, projectId)
                        .orderByAsc(Milestone::getDueDate)
                        .orderByAsc(Milestone::getCreatedAt))
                .stream().map(this::milestoneView).toList();
    }

    @Transactional
    public TaskDtos.MilestoneView createMilestone(Long projectId, TaskDtos.MilestoneRequest request,
                                                  LoginUser user) {
        ProjectMember member = projectService.requireMember(projectId, user);
        requireRole("project.milestone.manageRoles", "OWNER,ADMIN", member, null, user);
        Milestone milestone = new Milestone();
        milestone.setProjectId(projectId);
        applyMilestone(milestone, request);
        milestone.setCreatedBy(user.employeeId());
        milestone.setCreatedAt(LocalDateTime.now());
        milestone.setUpdatedAt(LocalDateTime.now());
        milestoneMapper.insert(milestone);
        auditService.record("PROJECT", "milestone", "CREATE", "MILESTONE", milestone.getId(),
                null, milestone, null, null);
        return milestoneView(milestone);
    }

    @Transactional
    public TaskDtos.MilestoneView updateMilestone(Long projectId, Long milestoneId,
                                                  TaskDtos.MilestoneRequest request, LoginUser user) {
        ProjectMember member = projectService.requireMember(projectId, user);
        requireRole("project.milestone.manageRoles", "OWNER,ADMIN", member, null, user);
        Milestone milestone = requireMilestone(projectId, milestoneId);
        String oldStatus = milestone.getStatus();
        applyMilestone(milestone, request);
        milestoneMapper.updateById(milestone);
        if (!"REACHED".equalsIgnoreCase(milestone.getStatus())) {
            milestoneMapper.update(null, new LambdaUpdateWrapper<Milestone>()
                    .eq(Milestone::getId, milestone.getId())
                    .set(Milestone::getReachedAt, null));
            milestone.setReachedAt(null);
        }
        auditService.record("PROJECT", "milestone", "UPDATE", "MILESTONE", milestoneId,
                Map.of("status", oldStatus), milestone, null, null);
        return milestoneView(milestone);
    }

    @Transactional
    public void deleteMilestone(Long projectId, Long milestoneId, LoginUser user) {
        ProjectMember member = projectService.requireMember(projectId, user);
        requireRole("project.milestone.manageRoles", "OWNER,ADMIN", member, null, user);
        Milestone milestone = requireMilestone(projectId, milestoneId);
        milestoneMapper.deleteById(milestoneId);
        auditService.record("PROJECT", "milestone", "DELETE", "MILESTONE", milestoneId,
                milestone, null, null, null);
    }

    // ------------------------------------------------------------------ progress

    public TaskDtos.ProgressView progress(Long projectId, LoginUser user) {
        projectService.requireMember(projectId, user);
        Project project = projectService.requireProject(projectId);
        return progressView(project);
    }

    @Transactional
    public TaskDtos.ProgressView updateProgress(Long projectId,
                                                TaskDtos.ProgressRequest request, LoginUser user) {
        ProjectMember member = projectService.requireMember(projectId, user);
        requireRole("project.progress.manageRoles", "OWNER,ADMIN", member, null, user);
        Project project = projectService.requireProject(projectId);
        long total = taskCount(projectId, false);
        long done = taskCount(projectId, true);
        boolean auto = isAutoProgress(project);
        int oldProgress = project.getProgress() == null ? 0 : project.getProgress();
        int progress = auto ? autoProgress(total, done) : request.getProgress();
        project.setProgress(progress);
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(project);
        snapshot(projectId, progress, total, done, request.getNote(), user.employeeId());
        auditService.record("PROJECT", "progress", "UPDATE", "PROJECT", projectId,
                Map.of("progress", oldProgress), Map.of("progress", progress),
                request.getNote(), null);
        return new TaskDtos.ProgressView(projectId, progress, auto ? "AUTO" : "MANUAL",
                total, done, project.getUpdatedAt());
    }

    public List<TaskDtos.ProgressSnapshotView> snapshots(Long projectId, LoginUser user) {
        projectService.requireMember(projectId, user);
        List<ProjectProgressSnapshot> snapshots = snapshotMapper.selectList(
                new LambdaQueryWrapper<ProjectProgressSnapshot>()
                        .eq(ProjectProgressSnapshot::getProjectId, projectId)
                        .orderByDesc(ProjectProgressSnapshot::getCreatedAt)
                        .last("LIMIT 100"));
        Map<Long, Employee> employees = employees(snapshots.stream()
                .map(ProjectProgressSnapshot::getCreatedBy).toList());
        return snapshots.stream().map(snapshot -> {
            Employee creator = employees.get(snapshot.getCreatedBy());
            return new TaskDtos.ProgressSnapshotView(snapshot.getId(), snapshot.getProjectId(),
                    snapshot.getProgress(), snapshot.getTaskTotal(), snapshot.getTaskDone(),
                    snapshot.getNote(), creator == null ? null : creator.getName(),
                    snapshot.getCreatedAt());
        }).toList();
    }

    // ------------------------------------------------------------------ internals

    private TaskDtos.ProgressView progressView(Project project) {
        long total = taskCount(project.getId(), false);
        long done = taskCount(project.getId(), true);
        boolean auto = isAutoProgress(project);
        int progress = auto ? autoProgress(total, done)
                : (project.getProgress() == null ? 0 : project.getProgress());
        return new TaskDtos.ProgressView(project.getId(), progress, auto ? "AUTO" : "MANUAL",
                total, done, project.getUpdatedAt());
    }

    private boolean isAutoProgress(Project project) {
        return configService.getBoolean(AUTO_PROGRESS_KEY, false)
                || "AUTO".equalsIgnoreCase(project.getProgressMode());
    }

    private int autoProgress(long total, long done) {
        return total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
    }

    private long taskCount(Long projectId, boolean doneOnly) {
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId);
        if (doneOnly) {
            wrapper.eq(Task::getStatus, "DONE");
        } else {
            wrapper.ne(Task::getStatus, "CANCELLED");
        }
        Long count = taskMapper.selectCount(wrapper);
        return count == null ? 0 : count;
    }

    private void refreshAutoProgress(Long projectId, Long employeeId, String note) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || !isAutoProgress(project)) {
            return;
        }
        long total = taskCount(projectId, false);
        long done = taskCount(projectId, true);
        int progress = autoProgress(total, done);
        project.setProgress(progress);
        projectMapper.updateById(project);
        snapshot(projectId, progress, total, done, note, employeeId);
    }

    private void snapshot(Long projectId, int progress, long total, long done,
                          String note, Long employeeId) {
        ProjectProgressSnapshot snapshot = new ProjectProgressSnapshot();
        snapshot.setProjectId(projectId);
        snapshot.setProgress(progress);
        snapshot.setTaskTotal((int) total);
        snapshot.setTaskDone((int) done);
        snapshot.setNote(note);
        snapshot.setCreatedBy(employeeId);
        snapshot.setCreatedAt(LocalDateTime.now());
        snapshotMapper.insert(snapshot);
    }

    private void applyMilestone(Milestone milestone, TaskDtos.MilestoneRequest request) {
        milestone.setTitle(request.getTitle().trim());
        milestone.setDescription(request.getDescription());
        milestone.setDueDate(request.getDueDate());
        String status = StringUtils.hasText(request.getStatus())
                ? request.getStatus().trim().toUpperCase() : "PLANNED";
        if (!Set.of("PLANNED", "REACHED", "CANCELLED").contains(status)) {
            throw new BusinessException(40001, "里程碑状态不合法");
        }
        milestone.setStatus(status);
        if ("REACHED".equals(status)) {
            if (milestone.getReachedAt() == null) {
                milestone.setReachedAt(LocalDateTime.now());
            }
        } else {
            milestone.setReachedAt(null);
        }
    }

    private void applyProgressAndCompletion(Task task, Integer progress) {
        if ("DONE".equals(task.getStatus())) {
            task.setProgress(100);
            if (task.getCompletedAt() == null) {
                task.setCompletedAt(LocalDateTime.now());
            }
        } else {
            task.setProgress(progress == null ? (task.getProgress() == null ? 0 : task.getProgress())
                    : Math.max(0, Math.min(100, progress)));
        }
    }

    private void clearCompletionIfNeeded(Task task) {
        if (!"DONE".equals(task.getStatus()) && task.getCompletedAt() != null) {
            task.setCompletedAt(null);
            taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                    .eq(Task::getId, task.getId())
                    .set(Task::getCompletedAt, null));
        }
    }

    private void replaceParticipants(Long taskId, List<Long> employeeIds) {
        participantMapper.delete(new LambdaQueryWrapper<TaskParticipant>()
                .eq(TaskParticipant::getTaskId, taskId));
        if (employeeIds == null) {
            return;
        }
        for (Long employeeId : new LinkedHashSet<>(employeeIds)) {
            if (employeeId == null) {
                continue;
            }
            TaskParticipant participant = new TaskParticipant();
            participant.setTaskId(taskId);
            participant.setEmployeeId(employeeId);
            participantMapper.insert(participant);
        }
    }

    private void replaceDependencies(Long projectId, Long taskId, List<Long> dependsOnIds) {
        List<Long> ids = dependsOnIds == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(dependsOnIds));
        for (Long dependsOnId : ids) {
            if (dependsOnId == null || Objects.equals(dependsOnId, taskId)) {
                throw new BusinessException(40001, "任务不能依赖自己");
            }
            Task dependency = taskMapper.selectById(dependsOnId);
            if (dependency == null || !Objects.equals(dependency.getProjectId(), projectId)) {
                throw new BusinessException(40001, "依赖任务不存在或不属于当前项目");
            }
        }
        ensureNoCycle(taskId, ids);
        dependencyMapper.delete(new LambdaQueryWrapper<TaskDependency>()
                .eq(TaskDependency::getTaskId, taskId));
        for (Long dependsOnId : ids) {
            TaskDependency dependency = new TaskDependency();
            dependency.setTaskId(taskId);
            dependency.setDependsOnTaskId(dependsOnId);
            dependency.setDependencyType("FINISH_TO_START");
            dependencyMapper.insert(dependency);
        }
    }

    private void ensureNoCycle(Long taskId, List<Long> dependsOnIds) {
        Deque<Long> stack = new ArrayDeque<>(dependsOnIds);
        Set<Long> visited = new HashSet<>();
        while (!stack.isEmpty()) {
            Long current = stack.pop();
            if (Objects.equals(current, taskId)) {
                throw new BusinessException(40001, "任务依赖形成循环");
            }
            if (!visited.add(current)) {
                continue;
            }
            List<TaskDependency> next = dependencyMapper.selectList(
                    new LambdaQueryWrapper<TaskDependency>()
                            .eq(TaskDependency::getTaskId, current));
            next.forEach(dependency -> stack.push(dependency.getDependsOnTaskId()));
        }
    }

    private void validateAssignees(Long projectId, Long assignee, List<Long> participants) {
        if (assignee != null && !projectService.isMember(projectId, assignee)) {
            throw new BusinessException(40001, "任务负责人必须是项目成员");
        }
        if (participants != null) {
            for (Long employeeId : participants) {
                if (employeeId != null && !projectService.isMember(projectId, employeeId)) {
                    throw new BusinessException(40001, "任务参与人必须是项目成员");
                }
            }
        }
    }

    private void notifyAssignee(Long projectId, Task task, String oldTitle) {
        if (task.getAssigneeEmployeeId() == null) {
            return;
        }
        SysUser assigneeUser = userMapper.selectByEmployeeId(task.getAssigneeEmployeeId());
        if (assigneeUser == null) {
            return;
        }
        Project project = projectMapper.selectById(projectId);
        String projectName = project == null ? "项目" : project.getName();
        String title = oldTitle == null ? "新任务：" + task.getTitle()
                : "任务已改派：" + task.getTitle();
        notificationService.notify(assigneeUser.getId(), "TASK", title,
                projectName + " 分配给你一项任务", "/projects/" + projectId);
    }

    private void requireRole(String configKey, String defaultValue, ProjectMember member,
                             Task task, LoginUser user) {
        if (!hasRole(splitConfig(configService.getString(configKey, defaultValue)),
                member, task, user)) {
            throw new BusinessException(40301, "当前项目角色不能执行该操作");
        }
    }

    private boolean hasRole(Set<String> roles, ProjectMember member, Task task, LoginUser user) {
        if (roles.contains(member.getMemberRole())) {
            return true;
        }
        if (task != null) {
            if (roles.contains("ASSIGNEE")
                    && Objects.equals(task.getAssigneeEmployeeId(), user.employeeId())) {
                return true;
            }
            if (roles.contains("CREATOR")
                    && Objects.equals(task.getCreatorEmployeeId(), user.employeeId())) {
                return true;
            }
        }
        return false;
    }

    private Set<String> splitConfig(String value) {
        Set<String> result = new HashSet<>();
        if (!StringUtils.hasText(value)) {
            return result;
        }
        for (String token : value.split(",")) {
            if (StringUtils.hasText(token)) {
                result.add(token.trim().toUpperCase());
            }
        }
        return result;
    }

    private Task requireTask(Long projectId, Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getProjectId(), projectId)) {
            throw new BusinessException(40401, "任务不存在");
        }
        return task;
    }

    private Milestone requireMilestone(Long projectId, Long milestoneId) {
        Milestone milestone = milestoneMapper.selectById(milestoneId);
        if (milestone == null || !Objects.equals(milestone.getProjectId(), projectId)) {
            throw new BusinessException(40401, "里程碑不存在");
        }
        return milestone;
    }

    private String normalizeStatus(String status) {
        String value = StringUtils.hasText(status) ? status.trim().toUpperCase() : "TODO";
        if (!STATUSES.contains(value)) {
            throw new BusinessException(40001, "任务状态不合法");
        }
        return value;
    }

    private String normalizePriority(String priority) {
        String value = StringUtils.hasText(priority) ? priority.trim().toUpperCase() : "NORMAL";
        if (!PRIORITIES.contains(value)) {
            throw new BusinessException(40001, "任务优先级不合法");
        }
        return value;
    }

    private TaskDtos.MilestoneView milestoneView(Milestone milestone) {
        return new TaskDtos.MilestoneView(milestone.getId(), milestone.getProjectId(),
                milestone.getTitle(), milestone.getDescription(), milestone.getDueDate(),
                milestone.getStatus(), milestone.getReachedAt(), milestone.getCreatedAt());
    }

    private List<TaskDtos.TaskView> toViews(List<Task> tasks, ProjectMember member, LoginUser user) {
        if (tasks.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> editRoles = splitConfig(configService.getString(
                "project.task.editRoles", "OWNER,ADMIN,ASSIGNEE"));
        Set<String> deleteRoles = splitConfig(configService.getString(
                "project.task.deleteRoles", "OWNER,ADMIN"));
        List<Long> taskIds = tasks.stream().map(Task::getId).toList();
        Set<Long> employeeIds = new HashSet<>();
        tasks.forEach(task -> {
            if (task.getAssigneeEmployeeId() != null) {
                employeeIds.add(task.getAssigneeEmployeeId());
            }
            if (task.getCreatorEmployeeId() != null) {
                employeeIds.add(task.getCreatorEmployeeId());
            }
        });

        List<TaskParticipant> participants = participantMapper.selectList(
                new LambdaQueryWrapper<TaskParticipant>().in(TaskParticipant::getTaskId, taskIds));
        participants.forEach(participant -> employeeIds.add(participant.getEmployeeId()));
        Map<Long, List<Long>> participantsByTask = participants.stream()
                .collect(Collectors.groupingBy(TaskParticipant::getTaskId,
                        Collectors.mapping(TaskParticipant::getEmployeeId, Collectors.toList())));

        List<TaskDependency> dependencies = dependencyMapper.selectList(
                new LambdaQueryWrapper<TaskDependency>().in(TaskDependency::getTaskId, taskIds));
        Set<Long> dependencyIds = dependencies.stream()
                .map(TaskDependency::getDependsOnTaskId).collect(Collectors.toSet());
        Map<Long, Task> dependencyTasks = dependencyIds.isEmpty() ? Collections.emptyMap()
                : taskMapper.selectBatchIds(dependencyIds).stream()
                .collect(Collectors.toMap(Task::getId, Function.identity()));
        Map<Long, List<Long>> dependenciesByTask = dependencies.stream()
                .collect(Collectors.groupingBy(TaskDependency::getTaskId,
                        Collectors.mapping(TaskDependency::getDependsOnTaskId, Collectors.toList())));

        List<TaskComment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<TaskComment>().select(TaskComment::getTaskId)
                        .in(TaskComment::getTaskId, taskIds));
        Map<Long, Long> commentCounts = comments.stream()
                .collect(Collectors.groupingBy(TaskComment::getTaskId, Collectors.counting()));

        Map<Long, Employee> employees = employees(employeeIds.stream().toList());
        List<TaskDtos.TaskView> views = new ArrayList<>();
        for (Task task : tasks) {
            Employee assignee = employees.get(task.getAssigneeEmployeeId());
            Employee creator = employees.get(task.getCreatorEmployeeId());
            List<TaskDtos.ParticipantView> participantViews =
                    participantsByTask.getOrDefault(task.getId(), List.of()).stream()
                            .map(id -> {
                                Employee employee = employees.get(id);
                                return new TaskDtos.ParticipantView(id,
                                        employee == null ? null : employee.getName(),
                                        employee == null ? null : employee.getEmployeeNo());
                            }).toList();
            List<TaskDtos.TaskRef> dependencyViews =
                    dependenciesByTask.getOrDefault(task.getId(), List.of()).stream()
                            .map(id -> {
                                Task dependency = dependencyTasks.get(id);
                                return new TaskDtos.TaskRef(id,
                                        dependency == null ? null : dependency.getTitle(),
                                        dependency == null ? null : dependency.getStatus());
                            }).toList();
            views.add(new TaskDtos.TaskView(
                    task.getId(),
                    task.getProjectId(),
                    task.getTitle(),
                    task.getDescription(),
                    task.getStatus(),
                    task.getPriority(),
                    task.getAssigneeEmployeeId(),
                    assignee == null ? null : assignee.getName(),
                    task.getCreatorEmployeeId(),
                    creator == null ? null : creator.getName(),
                    task.getStartDate(),
                    task.getDueDate(),
                    task.getProgress() == null ? 0 : task.getProgress(),
                    task.getSort() == null ? 0 : task.getSort(),
                    task.getCompletedAt(),
                    task.getCreatedAt(),
                    task.getUpdatedAt(),
                    participantViews,
                    dependencyViews,
                    commentCounts.getOrDefault(task.getId(), 0L),
                    hasRole(editRoles, member, task, user),
                    hasRole(deleteRoles, member, task, user)));
        }
        return views;
    }

    private Map<Long, Employee> employees(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        return employeeMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
    }
}
