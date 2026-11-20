package com.company.ems.chat;

import com.company.ems.common.ApiResponse;
import com.company.ems.common.PageResult;
import com.company.ems.security.LoginUser;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/capabilities")
    @PreAuthorize("hasAuthority('chat:use')")
    public ApiResponse<ChatDtos.AttachmentCapabilities> capabilities() {
        return ApiResponse.ok(chatService.attachmentCapabilities());
    }

    @GetMapping("/conversations")
    @PreAuthorize("hasAuthority('chat:use')")
    public ApiResponse<List<ChatDtos.ConversationView>> list(@AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(chatService.list(user));
    }

    @PostMapping("/conversations")
    @PreAuthorize("hasAuthority('chat:use')")
    public ApiResponse<ChatDtos.ConversationDetailView> create(
            @Valid @RequestBody ChatDtos.CreateRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(chatService.create(request, user));
    }

    @GetMapping("/conversations/{id}")
    @PreAuthorize("hasAuthority('chat:use')")
    public ApiResponse<ChatDtos.ConversationDetailView> detail(
            @PathVariable Long id,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(chatService.detail(id, user));
    }

    @PutMapping("/conversations/{id}/settings")
    @PreAuthorize("hasAuthority('chat:use')")
    public ApiResponse<ChatDtos.ConversationDetailView> settings(
            @PathVariable Long id,
            @RequestBody ChatDtos.SettingsRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(chatService.settings(id, request, user));
    }

    @PostMapping("/conversations/{id}/members")
    @PreAuthorize("hasAuthority('chat:use')")
    public ApiResponse<ChatDtos.MemberView> addMember(
            @PathVariable Long id,
            @Valid @RequestBody ChatDtos.AddMemberRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(chatService.addMember(id, request.getEmployeeId(), user));
    }

    @DeleteMapping("/conversations/{id}/members/{employeeId}")
    @PreAuthorize("hasAuthority('chat:use')")
    public ApiResponse<Void> removeMember(
            @PathVariable Long id,
            @PathVariable Long employeeId,
            @AuthenticationPrincipal LoginUser user) {
        chatService.removeMember(id, employeeId, user);
        return ApiResponse.ok();
    }

    @GetMapping("/conversations/{id}/messages")
    @PreAuthorize("hasAuthority('chat:use')")
    public ApiResponse<PageResult<ChatDtos.MessageView>> messages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "0") long size,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(chatService.messages(id, page, size, user));
    }

    @PostMapping("/conversations/{id}/messages")
    @PreAuthorize("hasAuthority('chat:use')")
    public ApiResponse<ChatDtos.MessageView> send(
            @PathVariable Long id,
            @Valid @RequestBody ChatDtos.SendRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(chatService.send(id, request, user));
    }

    @PostMapping("/conversations/{id}/attachments")
    @PreAuthorize("hasAuthority('chat:use')")
    public ApiResponse<ChatDtos.AttachmentView> uploadAttachment(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(chatService.uploadAttachment(id, file, user));
    }

    @GetMapping("/attachments/{id}/download")
    @PreAuthorize("hasAnyAuthority('chat:use','chat:access:request')")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable Long id,
            @AuthenticationPrincipal LoginUser user) {
        ChatService.AttachmentDownload download = chatService.downloadAttachment(id, user);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (download.contentType() != null) {
            try {
                mediaType = MediaType.parseMediaType(download.contentType());
            } catch (RuntimeException ignored) {
                // keep binary fallback
            }
        }
        String disposition = ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build().toString();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header("X-Content-Type-Options", "nosniff")
                .body(download.resource());
    }

    @PostMapping("/conversations/{id}/read")
    @PreAuthorize("hasAuthority('chat:use')")
    public ApiResponse<Void> markRead(@PathVariable Long id,
                                      @AuthenticationPrincipal LoginUser user) {
        chatService.markRead(id, user);
        return ApiResponse.ok();
    }

    @PostMapping("/messages/{id}/read")
    @PreAuthorize("hasAuthority('chat:use')")
    public ApiResponse<Void> markMessageRead(@PathVariable Long id,
                                             @AuthenticationPrincipal LoginUser user) {
        chatService.markMessageRead(id, user);
        return ApiResponse.ok();
    }

    @PostMapping("/messages/{id}/recall")
    @PreAuthorize("hasAuthority('chat:use')")
    public ApiResponse<ChatDtos.MessageView> recall(@PathVariable Long id,
                                                    @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(chatService.recall(id, user));
    }
}
