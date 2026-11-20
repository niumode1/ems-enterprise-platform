package com.company.ems.project;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/projects/{projectId}/documents")
    @PreAuthorize("hasAuthority('doc:read')")
    public ApiResponse<List<ProjectDtos.DocumentView>> list(
            @PathVariable Long projectId,
            @RequestParam(required = false) Long folderId,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(documentService.list(projectId, folderId, user));
    }

    @PostMapping("/projects/{projectId}/documents")
    @PreAuthorize("hasAuthority('doc:upload')")
    public ApiResponse<ProjectDtos.DocumentView> upload(
            @PathVariable Long projectId,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) String name,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(documentService.upload(projectId, folderId, name, file, user));
    }

    @GetMapping("/documents/{id}/download")
    @PreAuthorize("hasAuthority('doc:download')")
    public ResponseEntity<Resource> download(@PathVariable Long id,
                                             @AuthenticationPrincipal LoginUser user) {
        DocumentService.Download download = documentService.download(id, user);
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

    @GetMapping("/documents/{id}/print")
    @PreAuthorize("hasAuthority('doc:read')")
    public ApiResponse<ProjectDtos.DocumentPrint> print(@PathVariable Long id,
                                                        @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(documentService.print(id, user));
    }

    @DeleteMapping("/documents/{id}")
    @PreAuthorize("hasAuthority('doc:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @AuthenticationPrincipal LoginUser user) {
        documentService.delete(id, user);
        return ApiResponse.ok();
    }

    @GetMapping("/documents/{id}/permissions")
    @PreAuthorize("hasAuthority('doc:acl:manage')")
    public ApiResponse<List<ProjectDtos.DocumentPermissionView>> permissions(
            @PathVariable Long id,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(documentService.permissions(id, user));
    }

    @PutMapping("/documents/{id}/permissions")
    @PreAuthorize("hasAuthority('doc:acl:manage')")
    public ApiResponse<ProjectDtos.DocumentPermissionView> setPermission(
            @PathVariable Long id,
            @Valid @RequestBody ProjectDtos.DocumentPermissionRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(documentService.setPermission(id, request, user));
    }

    @GetMapping("/documents/search")
    @PreAuthorize("hasAuthority('doc:search')")
    public ApiResponse<List<ProjectDtos.DocumentSearchResult>> search(
            @RequestParam String keyword,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(documentService.search(keyword, projectId, limit, user));
    }

    @GetMapping("/documents/{id}/versions")
    @PreAuthorize("hasAuthority('doc:read')")
    public ApiResponse<List<ProjectDtos.DocumentVersionView>> versions(
            @PathVariable Long id,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(documentService.versions(id, user));
    }

    @PostMapping("/documents/{id}/versions")
    @PreAuthorize("hasAuthority('doc:version:upload')")
    public ApiResponse<ProjectDtos.DocumentVersionView> uploadVersion(
            @PathVariable Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String note,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(documentService.uploadVersion(id, name, note, file, user));
    }

    @PostMapping("/documents/{id}/versions/{versionNo}/rollback")
    @PreAuthorize("hasAuthority('doc:version:upload')")
    public ApiResponse<ProjectDtos.DocumentVersionView> rollback(
            @PathVariable Long id,
            @PathVariable int versionNo,
            @RequestParam(required = false) String note,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(documentService.rollback(id, versionNo, note, user));
    }

    @GetMapping("/documents/{id}/versions/{versionNo}/download")
    @PreAuthorize("hasAuthority('doc:download')")
    public ResponseEntity<Resource> downloadVersion(
            @PathVariable Long id,
            @PathVariable int versionNo,
            @AuthenticationPrincipal LoginUser user) {
        DocumentService.Download download = documentService.downloadVersion(id, versionNo, user);
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
}
