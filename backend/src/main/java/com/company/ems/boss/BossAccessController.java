package com.company.ems.boss;

import com.company.ems.common.ApiResponse;
import com.company.ems.security.LoginUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/boss-access")
public class BossAccessController {

    private final BossAccessService bossAccessService;

    public BossAccessController(BossAccessService bossAccessService) {
        this.bossAccessService = bossAccessService;
    }

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('employee:read')")
    public ApiResponse<BossAccessDtos.StatusView> status(@AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(bossAccessService.status(user));
    }

    @PostMapping("/requests")
    @PreAuthorize("hasAuthority('employee:sensitive:request')")
    public ApiResponse<BossAccessDtos.RequestResult> request(@Valid @RequestBody BossAccessDtos.Request request,
                                                             @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(bossAccessService.request(request, user));
    }

    @GetMapping("/grants")
    @PreAuthorize("hasAuthority('employee:read')")
    public ApiResponse<List<BossAccessDtos.GrantView>> grants(
            @RequestParam(defaultValue = "false") boolean all,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(bossAccessService.list(user, all));
    }

    @PostMapping("/grants/{id}/revoke")
    @PreAuthorize("hasAuthority('employee:read')")
    public ApiResponse<BossAccessDtos.GrantView> revoke(@PathVariable Long id,
                                                        @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(bossAccessService.revoke(id, user));
    }
}
