package com.company.ems.employee;

import com.company.ems.common.ApiResponse;
import com.company.ems.common.BusinessException;
import com.company.ems.common.PageResult;
import com.company.ems.security.LoginUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final EmployeeVisibilityService visibilityService;

    public EmployeeController(EmployeeService employeeService,
                              EmployeeVisibilityService visibilityService) {
        this.employeeService = employeeService;
        this.visibilityService = visibilityService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('employee:read')")
    public ApiResponse<PageResult<EmployeeDtos.EmployeeView>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(employeeService.page(page, size, keyword, orgId, status, user));
    }

    @GetMapping("/me")
    public ApiResponse<EmployeeDtos.EmployeeView> me(@AuthenticationPrincipal LoginUser user) {
        if (user.employeeId() == null) {
            throw new BusinessException(40401, "当前账号未关联员工档案");
        }
        return ApiResponse.ok(employeeService.get(user.employeeId(), user));
    }

    @PutMapping("/me")
    public ApiResponse<EmployeeDtos.UpdateResult> updateMe(
            @RequestBody EmployeeDtos.UpdateRequest request,
            @AuthenticationPrincipal LoginUser user) {
        if (user.employeeId() == null) {
            throw new BusinessException(40401, "当前账号未关联员工档案");
        }
        return ApiResponse.ok(employeeService.update(user.employeeId(), request, user));
    }

    @GetMapping("/me/visibility")
    public ApiResponse<java.util.List<EmployeeDtos.VisibilityView>> myVisibility(
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(visibilityService.myVisibility(user));
    }

    @PutMapping("/me/visibility")
    public ApiResponse<EmployeeDtos.VisibilityView> updateVisibility(
            @Valid @RequestBody EmployeeDtos.VisibilityUpdateRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(visibilityService.updateVisibility(user, request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('employee:read')")
    public ApiResponse<EmployeeDtos.EmployeeView> get(@PathVariable Long id,
                                                     @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(employeeService.get(id, user));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('employee:create')")
    public ApiResponse<EmployeeDtos.EmployeeView> create(@Valid @RequestBody EmployeeDtos.CreateRequest request,
                                                         @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(employeeService.create(request, user));
    }

    @PutMapping("/{id}")
    public ApiResponse<EmployeeDtos.UpdateResult> update(@PathVariable Long id,
                                                         @RequestBody EmployeeDtos.UpdateRequest request,
                                                         @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(employeeService.update(id, request, user));
    }
}
