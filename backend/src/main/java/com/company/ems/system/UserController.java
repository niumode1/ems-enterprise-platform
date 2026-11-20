package com.company.ems.system;

import com.company.ems.common.ApiResponse;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user:read')")
    public ApiResponse<PageResult<UserDtos.UserView>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(userService.page(page, size, keyword, status));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:create')")
    public ApiResponse<UserDtos.UserView> create(@Valid @RequestBody UserDtos.CreateRequest request,
                                                 @AuthenticationPrincipal LoginUser actor) {
        return ApiResponse.ok(userService.create(request, actor));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:update')")
    public ApiResponse<UserDtos.UserView> update(@PathVariable Long id,
                                                 @RequestBody UserDtos.UpdateRequest request) {
        return ApiResponse.ok(userService.update(id, request));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('user:role:assign')")
    public ApiResponse<UserDtos.UserView> assignRoles(@PathVariable Long id,
                                                      @RequestBody UserDtos.RoleAssignRequest request,
                                                      @AuthenticationPrincipal LoginUser actor) {
        return ApiResponse.ok(userService.assignRoles(id, request, actor));
    }

    @GetMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('user:read')")
    public ApiResponse<List<UserDtos.RoleAssignmentView>> roleHistory(@PathVariable Long id) {
        return ApiResponse.ok(userService.roleHistory(id));
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('user:disable')")
    public ApiResponse<Void> disable(@PathVariable Long id,
                                     @AuthenticationPrincipal LoginUser actor) {
        userService.disable(id, actor);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('user:update')")
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                           @Valid @RequestBody UserDtos.ResetPasswordRequest request) {
        userService.resetPassword(id, request.getPassword());
        return ApiResponse.ok();
    }

    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody UserDtos.ChangePasswordRequest request,
                                            @AuthenticationPrincipal LoginUser actor) {
        userService.changeOwnPassword(actor, request.getCurrentPassword(), request.getNewPassword());
        return ApiResponse.ok();
    }
}
