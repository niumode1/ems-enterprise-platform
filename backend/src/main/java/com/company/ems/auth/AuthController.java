package com.company.ems.auth;

import com.company.ems.common.ApiResponse;
import com.company.ems.common.BusinessException;
import com.company.ems.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                                     HttpServletRequest servletRequest) {
        return ApiResponse.ok(authService.login(request, servletRequest));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthDtos.LoginResponse> refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<AuthDtos.UserInfo> me(@AuthenticationPrincipal LoginUser user) {
        if (user == null) {
            throw new BusinessException(40101, "未登录");
        }
        return ApiResponse.ok(authService.me(user));
    }
}
