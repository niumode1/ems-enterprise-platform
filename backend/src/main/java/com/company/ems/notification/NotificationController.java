package com.company.ems.notification;

import com.company.ems.common.ApiResponse;
import com.company.ems.common.PageResult;
import com.company.ems.notification.entity.Notification;
import com.company.ems.security.LoginUser;
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
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasAuthority('notification:read')")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationPreferenceService preferenceService;

    public NotificationController(NotificationService notificationService,
                                  NotificationPreferenceService preferenceService) {
        this.notificationService = notificationService;
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public ApiResponse<PageResult<Notification>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(notificationService.page(user, page, size, unreadOnly));
    }

    public record UnreadCount(long count) {
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCount> unreadCount(@AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(new UnreadCount(notificationService.unreadCount(user)));
    }

    @GetMapping("/preferences")
    public ApiResponse<NotificationPreferenceDtos.PreferencesView> preferences(
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(preferenceService.view(user));
    }

    @PutMapping("/preferences")
    public ApiResponse<NotificationPreferenceDtos.PreferencesView> updatePreferences(
            @RequestBody NotificationPreferenceDtos.UpdateRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(preferenceService.update(request, user));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id,
                                      @AuthenticationPrincipal LoginUser user) {
        notificationService.markRead(id, user);
        return ApiResponse.ok();
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead(@AuthenticationPrincipal LoginUser user) {
        notificationService.markAllRead(user);
        return ApiResponse.ok();
    }
}
