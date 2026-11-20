package com.company.ems.employee;

import com.company.ems.common.ApiResponse;
import com.company.ems.employee.entity.OrgUnit;
import com.company.ems.employee.entity.Position;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/org")
public class OrgController {

    private final OrgService orgService;

    public OrgController(OrgService orgService) {
        this.orgService = orgService;
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('org:read')")
    public ApiResponse<List<OrgDtos.OrgNode>> tree() {
        return ApiResponse.ok(orgService.tree());
    }

    @GetMapping("/reporting-tree")
    @PreAuthorize("hasAuthority('org:read')")
    public ApiResponse<List<OrgDtos.ReportingNode>> reportingTree() {
        return ApiResponse.ok(orgService.reportingTree());
    }

    @GetMapping("/employees/{id}/relations")
    @PreAuthorize("hasAuthority('org:read')")
    public ApiResponse<OrgDtos.EmployeeRelations> relations(@PathVariable Long id) {
        return ApiResponse.ok(orgService.relations(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('org:create')")
    public ApiResponse<OrgUnit> create(@Valid @RequestBody OrgDtos.OrgRequest request) {
        return ApiResponse.ok(orgService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('org:update')")
    public ApiResponse<OrgUnit> update(@PathVariable Long id,
                                       @Valid @RequestBody OrgDtos.OrgRequest request) {
        return ApiResponse.ok(orgService.update(id, request));
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('org:disable')")
    public ApiResponse<OrgUnit> disable(@PathVariable Long id) {
        return ApiResponse.ok(orgService.disable(id));
    }

    @GetMapping("/positions")
    @PreAuthorize("hasAuthority('org:read')")
    public ApiResponse<List<Position>> positions() {
        return ApiResponse.ok(orgService.positions());
    }

    @PostMapping("/positions")
    @PreAuthorize("hasAuthority('employee:position:update')")
    public ApiResponse<Position> createPosition(@Valid @RequestBody OrgDtos.PositionRequest request) {
        return ApiResponse.ok(orgService.createPosition(request));
    }
}
