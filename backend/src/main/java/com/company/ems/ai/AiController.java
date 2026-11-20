package com.company.ems.ai;

import com.company.ems.common.ApiResponse;
import com.company.ems.common.PageResult;
import com.company.ems.project.TaskDtos;
import com.company.ems.security.LoginUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiProviderService providerService;
    private final AiAssistantService assistantService;
    private final AiDocumentService documentService;

    public AiController(AiProviderService providerService,
                        AiAssistantService assistantService,
                        AiDocumentService documentService) {
        this.providerService = providerService;
        this.assistantService = assistantService;
        this.documentService = documentService;
    }

    @GetMapping("/config")
    @PreAuthorize("hasAuthority('ai:config:read')")
    public ApiResponse<List<AiDtos.ProviderView>> config() {
        return ApiResponse.ok(providerService.list());
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority('ai:config:update')")
    public ApiResponse<AiDtos.ProviderView> save(
            @Validated @RequestBody AiDtos.ProviderRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(providerService.save(request, user));
    }

    @PostMapping("/providers/test")
    @PreAuthorize("hasAuthority('ai:config:update')")
    public ApiResponse<AiDtos.TestResult> test(@RequestBody(required = false) AiDtos.TestRequest request,
                                               @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(providerService.test(
                request == null ? null : request.getProviderId(), user));
    }

    @PostMapping("/ask")
    @PreAuthorize("hasAuthority('ai:use')")
    public ApiResponse<AiAssistantDtos.AskResult> ask(
            @Valid @RequestBody AiAssistantDtos.AskRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(assistantService.ask(request, user));
    }

    @PostMapping("/help/locate")
    @PreAuthorize("hasAuthority('ai:help')")
    public ApiResponse<List<AiAssistantDtos.HelpHit>> locate(
            @Valid @RequestBody AiAssistantDtos.AskRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(assistantService.locate(request.getQuestion(), user));
    }

    @PostMapping("/people/ask")
    @PreAuthorize("hasAuthority('ai:people')")
    public ApiResponse<AiAssistantDtos.PeopleAnswer> askPeople(
            @Valid @RequestBody AiAssistantDtos.AskRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(assistantService.askPeople(request, user));
    }

    @PostMapping("/documents/ask")
    @PreAuthorize("hasAuthority('ai:doc')")
    public ApiResponse<AiDocumentDtos.DocAnswer> askDocument(
            @Valid @RequestBody AiDocumentDtos.DocAskRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(documentService.ask(request, user));
    }

    @PostMapping("/documents/{documentId}/extract-tasks")
    @PreAuthorize("hasAuthority('ai:doc')")
    public ApiResponse<AiDocumentDtos.DocTaskExtractResult> extractTasks(
            @PathVariable Long documentId,
            @RequestBody(required = false) AiDocumentDtos.DocTaskExtractRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(documentService.extractTasks(documentId,
                request == null ? null : request.getMax(), user));
    }

    @PostMapping("/documents/{documentId}/apply-tasks")
    @PreAuthorize("hasAuthority('ai:doc')")
    public ApiResponse<List<TaskDtos.TaskView>> applyTasks(
            @PathVariable Long documentId,
            @Valid @RequestBody AiDocumentDtos.DocTaskApplyRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(documentService.applyTasks(documentId, request, user));
    }

    @GetMapping("/quick-questions")
    @PreAuthorize("hasAuthority('ai:use')")
    public ApiResponse<List<AiAssistantDtos.QuickQuestion>> quickQuestions(
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(assistantService.quickQuestions(user));
    }

    @GetMapping("/logs")
    @PreAuthorize("hasAuthority('ai:log:read')")
    public ApiResponse<PageResult<AiAssistantDtos.QueryLogView>> logs(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String feature) {
        return ApiResponse.ok(assistantService.logs(page, size, feature));
    }

    @GetMapping("/help-index")
    @PreAuthorize("hasAuthority('ai:config:read')")
    public ApiResponse<List<AiAssistantDtos.HelpIndexView>> helpIndex() {
        return ApiResponse.ok(assistantService.listHelpIndex());
    }

    @PostMapping("/help-index")
    @PreAuthorize("hasAuthority('ai:config:update')")
    public ApiResponse<AiAssistantDtos.HelpIndexView> createHelpIndex(
            @Valid @RequestBody AiAssistantDtos.HelpIndexRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(assistantService.saveHelpIndex(null, request, user));
    }

    @PutMapping("/help-index/{id}")
    @PreAuthorize("hasAuthority('ai:config:update')")
    public ApiResponse<AiAssistantDtos.HelpIndexView> updateHelpIndex(
            @PathVariable Long id,
            @Valid @RequestBody AiAssistantDtos.HelpIndexRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(assistantService.saveHelpIndex(id, request, user));
    }

    @DeleteMapping("/help-index/{id}")
    @PreAuthorize("hasAuthority('ai:config:update')")
    public ApiResponse<Void> deleteHelpIndex(@PathVariable Long id) {
        assistantService.deleteHelpIndex(id);
        return ApiResponse.ok();
    }
}
