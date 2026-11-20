package com.company.ems.chat;

import com.company.ems.chat.entity.ChatAccessRequest;
import com.company.ems.common.ApiResponse;
import com.company.ems.common.PageResult;
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
@RequestMapping("/api/v1/chat/admin-access")
public class ChatAdminAccessController {

    private final ChatAdminAccessService accessService;
    private final ChatService chatService;

    public ChatAdminAccessController(ChatAdminAccessService accessService, ChatService chatService) {
        this.accessService = accessService;
        this.chatService = chatService;
    }

    @GetMapping("/status")
    public ApiResponse<ChatAdminAccessDtos.StatusView> status(
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(accessService.status(user));
    }

    @PostMapping("/requests")
    @PreAuthorize("hasAuthority('chat:access:request')")
    public ApiResponse<ChatAdminAccessDtos.RequestResult> request(
            @Valid @RequestBody ChatAdminAccessDtos.Request request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(accessService.request(request, user));
    }

    @GetMapping("/requests")
    public ApiResponse<List<ChatAdminAccessDtos.GrantView>> requests(
            @RequestParam(defaultValue = "false") boolean all,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(accessService.list(all, user));
    }

    @PostMapping("/requests/{id}/revoke")
    public ApiResponse<ChatAdminAccessDtos.GrantView> revoke(
            @PathVariable Long id,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(accessService.revoke(id, user));
    }

    @GetMapping("/conversations/{id}/messages")
    @PreAuthorize("hasAnyAuthority('chat:access:request','chat:access:approve')")
    public ApiResponse<PageResult<ChatDtos.MessageView>> messages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "0") long size,
            @AuthenticationPrincipal LoginUser user) {
        ChatAccessRequest grant = accessService.activeGrant(user.userId(), id);
        accessService.recordRead(grant, user);
        return ApiResponse.ok(chatService.adminMessages(id, page, size));
    }
}
