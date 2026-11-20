package com.company.ems.policy;

import com.company.ems.ai.AiDocumentDtos;
import com.company.ems.common.ApiResponse;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('policy:read')")
    public ApiResponse<List<PolicyDtos.PolicyView>> list(
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(policyService.list(keyword));
    }

    @GetMapping("/capabilities")
    @PreAuthorize("hasAuthority('policy:read')")
    public ApiResponse<PolicyDtos.PolicyCapabilities> capabilities(
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(policyService.capabilities(user));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('policy:read')")
    public ApiResponse<PolicyDtos.PolicyDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(policyService.detail(id));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('policy:read')")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        PolicyService.Download download = policyService.download(id);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (download.contentType() != null) {
            try {
                mediaType = MediaType.parseMediaType(download.contentType());
            } catch (RuntimeException ignored) {
                // fall back to binary stream
            }
        }
        String disposition = ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build().toString();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(download.resource());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('policy:manage')")
    public ApiResponse<PolicyDtos.PolicyView> upload(
            @RequestParam String title,
            @RequestParam(required = false) String category,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(policyService.upload(title, category, file, user));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('policy:manage')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        policyService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/ask")
    @PreAuthorize("hasAuthority('policy:read') and hasAuthority('ai:use')")
    public ApiResponse<AiDocumentDtos.PolicyAnswer> ask(
            @Valid @RequestBody PolicyDtos.PolicyAskRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(policyService.ask(request, user));
    }
}
