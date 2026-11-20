package com.company.ems.customattr;

import com.company.ems.common.ApiResponse;
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
@RequestMapping("/api/v1/custom-fields")
public class CustomAttributeController {

    private final CustomAttributeService service;

    public CustomAttributeController(CustomAttributeService service) {
        this.service = service;
    }

    @GetMapping("/defs")
    @PreAuthorize("hasAuthority('attr:read')")
    public ApiResponse<List<CustomAttributeDtos.DefView>> defs(
            @RequestParam(defaultValue = "EMPLOYEE") String entityType,
            @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return ApiResponse.ok(service.listDefs(entityType, includeDisabled));
    }

    @PostMapping("/defs")
    @PreAuthorize("hasAuthority('attr:create')")
    public ApiResponse<CustomAttributeDtos.DefView> createDef(
            @Valid @RequestBody CustomAttributeDtos.DefRequest request) {
        return ApiResponse.ok(service.createDef(request));
    }

    @PutMapping("/defs/{id}")
    @PreAuthorize("hasAuthority('attr:update')")
    public ApiResponse<CustomAttributeDtos.DefView> updateDef(
            @PathVariable Long id,
            @Valid @RequestBody CustomAttributeDtos.DefRequest request) {
        return ApiResponse.ok(service.updateDef(id, request));
    }

    @PostMapping("/defs/{id}/disable")
    @PreAuthorize("hasAuthority('attr:disable')")
    public ApiResponse<CustomAttributeDtos.DefView> disableDef(@PathVariable Long id) {
        return ApiResponse.ok(service.disableDef(id));
    }

    @GetMapping("/values")
    public ApiResponse<List<CustomAttributeDtos.ValueView>> values(
            @RequestParam(defaultValue = "EMPLOYEE") String entityType,
            @RequestParam Long entityId,
            @RequestParam(defaultValue = "false") boolean history,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(service.values(entityType, entityId, user, history));
    }

    @GetMapping("/values/history")
    public ApiResponse<List<CustomAttributeDtos.ValueView>> history(
            @RequestParam(defaultValue = "EMPLOYEE") String entityType,
            @RequestParam Long entityId,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(service.values(entityType, entityId, user, true));
    }

    @PutMapping("/values")
    public ApiResponse<Integer> setValues(@RequestBody CustomAttributeDtos.ValueSetRequest request,
                                          @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(service.setValues(request, user));
    }
}
