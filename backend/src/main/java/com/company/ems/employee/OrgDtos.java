package com.company.ems.employee;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

public final class OrgDtos {

    private OrgDtos() {
    }

    @Data
    public static class OrgRequest {
        private Long parentId;
        @NotBlank
        private String name;
        private String code;
        private String type;
        private Long leaderEmployeeId;
        private Integer sort;
    }

    @Data
    public static class PositionRequest {
        @NotBlank
        private String code;
        @NotBlank
        private String name;
        private String level;
    }

    public record OrgNode(
            Long id,
            Long parentId,
            String name,
            String code,
            String type,
            Long leaderEmployeeId,
            String path,
            Integer sort,
            String status,
            long employeeCount,
            List<OrgNode> children
    ) {
        public OrgNode withChildren(List<OrgNode> children) {
            return new OrgNode(id, parentId, name, code, type, leaderEmployeeId,
                    path, sort, status, employeeCount, children);
        }
    }

    public record ReportingNode(
            Long employeeId,
            String name,
            String employeeNo,
            Long positionId,
            String positionName,
            List<ReportingNode> children
    ) {
        public ReportingNode withChildren(List<ReportingNode> children) {
            return new ReportingNode(employeeId, name, employeeNo, positionId, positionName, children);
        }

        public ReportingNode {
            if (children == null) {
                children = new ArrayList<>();
            }
        }
    }

    public record RelationEmployee(Long id, String employeeNo, String name) {
    }

    public record EmployeeRelations(Long employeeId,
                                    RelationEmployee manager,
                                    List<RelationEmployee> superiors,
                                    List<RelationEmployee> subordinates,
                                    List<RelationEmployee> siblings) {
    }
}
